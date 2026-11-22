package com.nochultwi.backend.domain.course.service;

import com.nochultwi.backend.domain.course.repository.SessionRepository;
import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
import com.nochultwi.backend.global.security.device.DevicePrincipal;
import com.nochultwi.backend.infra.school.SchoolApiPort;
import com.nochultwi.backend.infra.school.SchoolCourseDto;
import com.nochultwi.backend.infra.school.UosWiseApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 키오스크 단말기에 배정된 강의 또는 강의실 기준으로 요청 대상(강의 ID / 강의실 번호)에 대한
 * 접근 권한을 단일 규칙으로 검증하는 도메인 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceCourseAuthorizationService {

    private final SessionRepository sessionRepository;
    private final SchoolApiPort schoolApiPort;
    private final Clock clock;

    public void validateAccess(DevicePrincipal principal, String targetCourseOrClassroom) {
        if (principal == null) {
            return;
        }
        validateAccess(principal.courseId(), targetCourseOrClassroom);
    }

    public void validateAccess(String deviceAssigned, String targetCourseOrClassroom) {
        if (!isAuthorizedForTarget(deviceAssigned, targetCourseOrClassroom)) {
            throw new BusinessException(ErrorCode.COURSE_FORBIDDEN, "해당 강의에 대한 단말기 접근 권한이 없습니다.");
        }
    }

    public boolean isAuthorizedForTarget(String deviceAssigned, String target) {
        if (!StringUtils.hasText(deviceAssigned) || !StringUtils.hasText(target)) {
            return false;
        }

        // 1. 단말기에 직접 배정된 값과 요청 대상이 정확히 일치하는 경우 (강의 코드 1:1 배정 또는 동일 강의실 코드 조회)
        if (deviceAssigned.trim().equalsIgnoreCase(target.trim())) {
            return true;
        }

        // 2. 강의실 번호 간 일치 (예: "37-B601" == "37-나601")
        if (schoolApiPort.isSameClassroom(deviceAssigned, target)
                && !schoolApiPort.getCoursesByClassroom(target).isEmpty()) {
            return true;
        }

        // 3. 단말기가 강의실(예: "37-B601")로 등록되어 있고, 특정 강의(예: "30000-03")에 접근하는 경우:
        //    ① 해당 강의가 이 강의실을 사용하는지 확인하고, ② 현재 시각이 이 강의실에서 열리는 수업 시간이어야만 접근 허용
        Optional<SchoolCourseDto> courseOpt = schoolApiPort.getCourseInfo(target);
        if (courseOpt.isEmpty()) {
            return false;
        }

        SchoolCourseDto course = courseOpt.get();
        boolean usesThisClassroom = schoolApiPort.isSameClassroom(deviceAssigned, course.classroom())
                || UosWiseApiClient.getEffectiveSchedules(course).stream()
                .anyMatch(s -> schoolApiPort.isSameClassroom(deviceAssigned, s.classroom()));

        if (!usesThisClassroom) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean hasActiveSession = !sessionRepository.findActiveSessions(target, now).isEmpty();
        boolean isScheduledInThisClassroomNow = schoolApiPort.isCourseScheduledAtInClassroom(course, deviceAssigned, now);
        return hasActiveSession || isScheduledInThisClassroomNow;
    }
}
