package com.nochultwi.backend.domain.attendance.service;

import com.nochultwi.backend.domain.attendance.dto.CheckInRequest;
import com.nochultwi.backend.domain.attendance.dto.CheckInResponse;
import com.nochultwi.backend.domain.attendance.dto.CheckInResult;
import com.nochultwi.backend.domain.attendance.entity.AttendanceRecord;
import com.nochultwi.backend.domain.attendance.entity.AttendanceSource;
import com.nochultwi.backend.domain.attendance.entity.AttendanceStatus;
import com.nochultwi.backend.domain.attendance.idempotency.IdempotencyStore;
import com.nochultwi.backend.domain.attendance.repository.AttendanceRepository;
import com.nochultwi.backend.domain.attendance.token.RecognitionTokenStore;
import com.nochultwi.backend.domain.course.entity.Session;
import com.nochultwi.backend.domain.course.repository.SessionRepository;
import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
import com.nochultwi.backend.global.security.device.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KioskCheckInService {

    private static final long IDEMPOTENCY_TTL_SECONDS = 86400L; // 24시간 보관

    private final IdempotencyStore idempotencyStore;
    private final RecognitionTokenStore tokenStore;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final Clock clock;

    @Transactional
    public CheckInResult checkIn(String idempotencyKey, CheckInRequest request, DevicePrincipal principal) {
        // 1. 멱등성 키 헤더 검증
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idempotency-Key 헤더는 필수입니다.");
        }
        idempotencyKey = idempotencyKey.trim();

        String payloadHash = request.recognitionToken() != null ? request.recognitionToken().trim() : "";

        // 2. 멱등성 캐시 확인 (네트워크 재시도 방어)
        Optional<IdempotencyStore.IdempotencyEntry> cachedOpt = idempotencyStore.get(idempotencyKey);
        if (cachedOpt.isPresent()) {
            IdempotencyStore.IdempotencyEntry cached = cachedOpt.get();
            if (!cached.requestPayloadHash().equals(payloadHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "동일한 멱등성 키로 다른 요청이 전송되었습니다.");
            }
            log.info("Idempotent check-in replayed: key={}", idempotencyKey);
            return new CheckInResult(HttpStatus.valueOf(cached.httpStatus()), (CheckInResponse) cached.responseBody());
        }

        // 3. 1회용 토큰 조회
        RecognitionTokenStore.RecognitionTokenData tokenData = tokenStore.findToken(request.recognitionToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOGNITION_EXPIRED, "인식 결과가 만료되었거나 존재하지 않습니다. 다시 촬영해 주세요."));

        // 4. 단말기 일치 여부 검증
        if (principal != null && !tokenData.deviceId().equals(principal.deviceId())) {
            throw new BusinessException(ErrorCode.RECOGNITION_DEVICE_MISMATCH, "인식을 요청한 단말기와 일치하지 않습니다.");
        }

        // 5. 토큰 원자적 소모 (Atomic Consume: CAS 1회 성공 보장)
        if (!tokenData.tryConsume()) {
            throw new BusinessException(ErrorCode.RECOGNITION_CONSUMED, "이미 사용된 인식 결과입니다. 다시 촬영해 주세요.");
        }

        // 6. 차시(Session) 조회 및 출석 시간 판정
        Session session = sessionRepository.findBySessionId(tokenData.sessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND, "차시 정보를 찾을 수 없습니다."));

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (now.isBefore(session.getOpenAt())) {
            throw new BusinessException(ErrorCode.SESSION_NOT_OPEN, "아직 출석 가능 시간이 아닙니다.");
        }
        if (now.isAfter(session.getCloseAt())) {
            throw new BusinessException(ErrorCode.SESSION_CLOSED, "출석 마감 시간이 지났습니다.");
        }

        // 상태 머신 판정: openAt <= now < lateFrom 이면 PRESENT, lateFrom <= now <= closeAt 이면 LATE
        AttendanceStatus determinedStatus = now.isBefore(session.getLateFrom())
                ? AttendanceStatus.PRESENT
                : AttendanceStatus.LATE;

        // 7. 기존 출결 기록 확인 (중복 출석 및 잠금 확인)
        Optional<AttendanceRecord> existingOpt =
                attendanceRepository.findBySessionIdAndStudentId(tokenData.sessionId(), tokenData.studentId());

        if (existingOpt.isPresent()) {
            AttendanceRecord existing = existingOpt.get();
            if (existing.isManualOverride() && existing.getStatus() == AttendanceStatus.ABSENT) {
                throw new BusinessException(ErrorCode.ATTENDANCE_LOCKED, "관리자에게 출결 상태를 문의해 주세요.");
            }

            CheckInResponse response = CheckInResponse.alreadyCheckedIn(
                    existing.getStatus().name(), existing.getCheckedInAt(), tokenData.sessionId()
            );
            idempotencyStore.save(idempotencyKey, payloadHash, HttpStatus.OK.value(), response, IDEMPOTENCY_TTL_SECONDS);
            return new CheckInResult(HttpStatus.OK, response);
        }

        // 8. 신규 출석 기록 생성
        AttendanceRecord record = AttendanceRecord.builder()
                .sessionId(tokenData.sessionId())
                .studentId(tokenData.studentId())
                .status(determinedStatus)
                .source(AttendanceSource.FACE)
                .checkedInAt(now)
                .isManualOverride(false)
                .build();

        attendanceRepository.save(record);
        log.info("Attendance checked in: studentId={}, sessionId={}, status={}",
                tokenData.studentId(), tokenData.sessionId(), determinedStatus);

        CheckInResponse response = CheckInResponse.checkedIn(
                determinedStatus.name(), now, tokenData.sessionId()
        );

        // 9. 멱등성 결과 캐시 저장 (24시간)
        idempotencyStore.save(idempotencyKey, payloadHash, HttpStatus.CREATED.value(), response, IDEMPOTENCY_TTL_SECONDS);

        return new CheckInResult(HttpStatus.CREATED, response);
    }
}
