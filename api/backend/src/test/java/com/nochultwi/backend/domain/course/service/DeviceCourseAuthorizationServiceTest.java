package com.nochultwi.backend.domain.course.service;

import com.nochultwi.backend.domain.course.entity.Session;
import com.nochultwi.backend.domain.course.repository.SessionRepository;
import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
import com.nochultwi.backend.global.security.device.DevicePrincipal;
import com.nochultwi.backend.infra.school.SchoolApiPort;
import com.nochultwi.backend.infra.school.SchoolCourseDto;
import com.nochultwi.backend.infra.school.UosWiseApiClient;
import com.nochultwi.backend.infra.school.UosWiseProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceCourseAuthorizationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private UosWiseApiClient createOfflineSchoolClient(Clock clock) {
        UosWiseProperties props = new UosWiseProperties("dummy", "https://wise.uos.ac.kr/COM", "2026", "20", false);
        return new UosWiseApiClient(props, clock);
    }

    @Test
    @DisplayName("1:1로 배정된 강의 ID와 요청 강의 ID가 일치하면 접근이 허용된다")
    void exactCourseMatchAllowed() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-21T01:00:00Z"), SEOUL);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        DeviceCourseAuthorizationService service = new DeviceCourseAuthorizationService(
                sessionRepository, createOfflineSchoolClient(clock), clock);
        DevicePrincipal principal = new DevicePrincipal(1L, "kiosk-01", "단말기", "30000-03");

        assertThatCode(() -> service.validateAccess(principal, "30000-03"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("배정되지 않은 다른 강의 ID 요청 시 403 COURSE_FORBIDDEN 예외가 발생한다")
    void unassignedCourseThrowsForbidden() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-21T01:00:00Z"), SEOUL);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        DeviceCourseAuthorizationService service = new DeviceCourseAuthorizationService(
                sessionRepository, createOfflineSchoolClient(clock), clock);
        DevicePrincipal principal = new DevicePrincipal(1L, "kiosk-01", "단말기", "30000-03");

        assertThatThrownBy(() -> service.validateAccess(principal, "99999-01"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COURSE_FORBIDDEN);
    }

    @Test
    @DisplayName("강의실 표기(영문 B vs 한글 나)가 정규화 후 일치하고 강의가 존재하면 접근이 허용된다")
    void normalizedClassroomMatchAllowed() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-21T01:00:00Z"), SEOUL);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        DeviceCourseAuthorizationService service = new DeviceCourseAuthorizationService(
                sessionRepository, createOfflineSchoolClient(clock), clock);

        assertThat(service.isAuthorizedForTarget("37-B601", "37-나601")).isTrue();
    }

    @Test
    @DisplayName("강의실 단말기가 해당 강의실에서 현재 시간표상 진행 중인 수업에 접근하면 허용된다")
    void classroomDeviceAllowedDuringScheduledClassTime() {
        // 2026-10-21(수) 18:10 KST (09:10 UTC) -> 30000-03: 수[10,11,12,13]/37-나601 (17:50~21:50)
        Clock clock = Clock.fixed(Instant.parse("2026-10-21T09:10:00Z"), SEOUL);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        when(sessionRepository.findActiveSessions(eq("30000-03"), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        DeviceCourseAuthorizationService service = new DeviceCourseAuthorizationService(
                sessionRepository, createOfflineSchoolClient(clock), clock);

        assertThatCode(() -> service.validateAccess("37-B601", "30000-03"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("강의실 단말기라도 수업 시간 외이고 활성 세션이 없으면 접근이 차단된다")
    void classroomDeviceDeniedOutsideClassTimeAndNoActiveSession() {
        // 2026-10-20(화) 10:00 KST (01:00 UTC) -> 수요일 수업 시간 외
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), SEOUL);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        when(sessionRepository.findActiveSessions(eq("30000-03"), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        DeviceCourseAuthorizationService service = new DeviceCourseAuthorizationService(
                sessionRepository, createOfflineSchoolClient(clock), clock);

        assertThatThrownBy(() -> service.validateAccess("37-B601", "30000-03"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COURSE_FORBIDDEN);
    }

    @Test
    @DisplayName("강의실 단말기에서 정규 시간 외라도 DB에 활성 세션이 열려 있으면 접근이 허용된다")
    void classroomDeviceAllowedWhenActiveSessionExists() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), SEOUL);
        OffsetDateTime now = OffsetDateTime.now(clock);
        Session activeSession = Session.builder()
                .sessionId("session-makeup-01")
                .courseId("30000-03")
                .date(LocalDate.now(clock))
                .week(8)
                .sessionNumber(1)
                .openAt(now.minusMinutes(10))
                .lateFrom(now.plusMinutes(10))
                .closeAt(now.plusMinutes(50))
                .build();

        SessionRepository sessionRepository = mock(SessionRepository.class);
        when(sessionRepository.findActiveSessions(eq("30000-03"), any(OffsetDateTime.class)))
                .thenReturn(List.of(activeSession));

        DeviceCourseAuthorizationService service = new DeviceCourseAuthorizationService(
                sessionRepository, createOfflineSchoolClient(clock), clock);

        assertThatCode(() -> service.validateAccess("37-B601", "30000-03"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 강의실에 배정된 수업에는 접근이 차단된다")
    void differentClassroomCourseDenied() {
        // 2026-10-23(금) 14:10 KST (05:10 UTC) -> 30000-01은 1-105 강의실(금 6~9교시)
        Clock clock = Clock.fixed(Instant.parse("2026-10-23T05:10:00Z"), SEOUL);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        DeviceCourseAuthorizationService service = new DeviceCourseAuthorizationService(
                sessionRepository, createOfflineSchoolClient(clock), clock);

        assertThatThrownBy(() -> service.validateAccess("37-B601", "30000-01"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COURSE_FORBIDDEN);
    }

    @Test
    @DisplayName("다중 요일·강의실 수업에서는 현재 요일에 해당하는 강의실 단말기만 허용된다")
    void multiDayClassroomCourseAllowsOnlyMatchingDayClassroom() {
        // 2026-10-20(화) 13:10 KST (04:10 UTC) -> 화[5,6]/14-414 (12:50~14:50), 목[5,6]/14-430
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T04:10:00Z"), SEOUL);
        SchoolCourseDto multiRoomCourse = new SchoolCourseDto(
                "40000-01", "40000", "다중강의실수업(01)", "홍길동", "컴퓨터과학부", "01",
                "화[5,6]/14-414,목[5,6]/14-430", "TUESDAY", "14-414", 5, 6
        );

        UosWiseApiClient delegate = createOfflineSchoolClient(clock);
        SchoolApiPort schoolApiPort = new SchoolApiPort() {
            @Override
            public Optional<SchoolCourseDto> getCourseInfo(String courseId) {
                return "40000-01".equals(courseId) ? Optional.of(multiRoomCourse) : Optional.empty();
            }

            @Override
            public boolean isSameClassroom(String roomA, String roomB) {
                return delegate.isSameClassroom(roomA, roomB);
            }

            @Override
            public boolean isCourseScheduledAtInClassroom(SchoolCourseDto course, String classroom, OffsetDateTime time) {
                return delegate.isCourseScheduledAtInClassroom(course, classroom, time);
            }
        };

        SessionRepository sessionRepository = mock(SessionRepository.class);
        when(sessionRepository.findActiveSessions(eq("40000-01"), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        DeviceCourseAuthorizationService service = new DeviceCourseAuthorizationService(
                sessionRepository, schoolApiPort, clock);

        assertThat(service.isAuthorizedForTarget("14-414", "40000-01")).isTrue();
        assertThat(service.isAuthorizedForTarget("14-430", "40000-01")).isFalse();
    }
}
