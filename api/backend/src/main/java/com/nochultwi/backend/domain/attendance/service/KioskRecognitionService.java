package com.nochultwi.backend.domain.attendance.service;

import com.nochultwi.backend.domain.attendance.dto.CandidateDto;
import com.nochultwi.backend.domain.attendance.dto.RecognitionResponse;
import com.nochultwi.backend.domain.attendance.entity.AttendanceRecord;
import com.nochultwi.backend.domain.attendance.entity.AttendanceStatus;
import com.nochultwi.backend.domain.attendance.repository.AttendanceRepository;
import com.nochultwi.backend.domain.attendance.token.RecognitionTokenStore;
import com.nochultwi.backend.domain.course.entity.Session;
import com.nochultwi.backend.domain.course.repository.SessionRepository;
import com.nochultwi.backend.domain.student.dto.EnrolledStudentDto;
import com.nochultwi.backend.domain.student.service.StudentService;
import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
import com.nochultwi.backend.global.security.device.DevicePrincipal;
import com.nochultwi.backend.infra.ai.FaceRecognitionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KioskRecognitionService {

    private static final long TOKEN_TTL_SECONDS = 60L;

    private final SessionRepository sessionRepository;
    private final StudentService studentService;
    private final AttendanceRepository attendanceRepository;
    private final FaceRecognitionPort faceRecognitionPort;
    private final RecognitionTokenStore tokenStore;
    private final Clock clock;

    public RecognitionResponse recognize(
            String courseId, String sessionId, String capturedAt,
            MultipartFile image, DevicePrincipal principal) {

        // 1. 단말기 이미지 전송 파일 검증
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "얼굴 이미지가 제공되지 않았습니다.");
        }

        // 명세 6.3: 최대 5MB 크기 제한 (초과 시 413 IMAGE_TOO_LARGE)
        if (image.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE, "이미지 파일 크기는 5MB를 초과할 수 없습니다.");
        }

        String contentType = image.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg") && !contentType.equalsIgnoreCase("image/png"))) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE, "지원하지 않는 이미지 형식입니다. (JPEG/PNG 형식만 지원)");
        }

        // 2. 단말기 등록 강좌와 요청 강좌 일치 여부 확인
        if (principal != null && !principal.courseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.COURSE_FORBIDDEN, "해당 강의에 대한 단말기 접근 권한이 없습니다.");
        }

        // 3. 차시(Session) 유효성 및 출석 가능 시간대 검증
        Session session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND, "차시 정보를 찾을 수 없습니다."));

        if (!session.getCourseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "해당 강의의 차시 정보가 아닙니다.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (now.isBefore(session.getOpenAt())) {
            throw new BusinessException(ErrorCode.SESSION_NOT_OPEN, "아직 출석 가능 시간이 아닙니다.");
        }
        if (now.isAfter(session.getCloseAt())) {
            throw new BusinessException(ErrorCode.SESSION_CLOSED, "출석 마감 시간이 지났습니다.");
        }

        // 4. 해당 강좌의 등록 수강생 목록 조회
        List<EnrolledStudentDto> candidates = studentService.getEnrolledStudents(courseId);
        if (candidates.isEmpty()) {
            return RecognitionResponse.retry("얼굴을 확인하지 못했어요. 다시 시도해 주세요");
        }

        // 5. 얼굴 인식 수행 (메모리에서 즉시 처리 후 원본 폐기)
        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (IOException e) {
            log.error("Failed to read face image bytes", e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미지 파일을 읽을 수 없습니다.");
        }

        Optional<EnrolledStudentDto> matchOpt = faceRecognitionPort.recognize(imageBytes, candidates);
        if (matchOpt.isEmpty()) {
            return RecognitionResponse.retry("얼굴을 확인하지 못했어요. 다시 시도해 주세요");
        }

        EnrolledStudentDto matchedStudent = matchOpt.get();

        // 6. 기존 출결 기록 조회 (중복 출석 또는 잠금 여부)
        Optional<AttendanceRecord> existingRecord =
                attendanceRepository.findBySessionIdAndStudentId(sessionId, matchedStudent.studentId());

        if (existingRecord.isPresent()) {
            AttendanceRecord record = existingRecord.get();
            // 관리자가 ABSENT로 정정한 경우 키오스크에서 재인증 불가 (ATTENDANCE_LOCKED)
            if (record.isManualOverride() && record.getStatus() == AttendanceStatus.ABSENT) {
                return RecognitionResponse.attendanceLocked("관리자에게 출결 상태를 문의해 주세요");
            }
            // 이미 PRESENT 또는 LATE로 출석 처리된 경우
            if (record.getStatus() == AttendanceStatus.PRESENT || record.getStatus() == AttendanceStatus.LATE) {
                return RecognitionResponse.alreadyCheckedIn(record.getStatus().name(), record.getCheckedInAt());
            }
        }

        // 7. 60초 1회용 토큰 발급
        String deviceId = principal != null ? principal.deviceId() : "unknown-device";
        RecognitionTokenStore.RecognitionTokenData tokenData = tokenStore.createToken(
                matchedStudent.studentId(),
                matchedStudent.studentNumber(),
                matchedStudent.name(),
                matchedStudent.department(),
                matchedStudent.admissionYear(),
                sessionId,
                courseId,
                deviceId,
                TOKEN_TTL_SECONDS
        );

        OffsetDateTime expiresAt = tokenData.expiresAt().atOffset(now.getOffset());
        CandidateDto candidateDto = new CandidateDto(
                matchedStudent.name(),
                matchedStudent.department(),
                matchedStudent.admissionYear()
        );

        log.info("Face recognized: studentNumber={}, name={}, sessionId={}, tokenIssued=true",
                matchedStudent.studentNumber(), matchedStudent.name(), sessionId);

        return RecognitionResponse.matched(tokenData.token(), expiresAt, candidateDto);
    }
}
