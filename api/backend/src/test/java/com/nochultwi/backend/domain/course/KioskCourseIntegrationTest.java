package com.nochultwi.backend.domain.course;

import com.nochultwi.backend.domain.course.entity.Session;
import com.nochultwi.backend.domain.course.repository.SessionRepository;
import com.nochultwi.backend.domain.device.entity.Device;
import com.nochultwi.backend.domain.device.repository.DeviceRepository;
import com.nochultwi.backend.global.filter.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Import(KioskCourseIntegrationTest.MutableClockConfig.class)
class KioskCourseIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant TUESDAY_10_00_KST = Instant.parse("2026-10-20T01:00:00Z");   // 화 10:00 (공강)
    private static final Instant WEDNESDAY_18_05_KST = Instant.parse("2026-10-21T09:05:00Z"); // 수 18:05 (30000-03 수업 중)
    private static final Instant FRIDAY_14_05_KST = Instant.parse("2026-10-23T05:05:00Z");    // 금 14:05 (30000-02 수업 중)

    private static final String TEST_DEVICE_ID = "kiosk-301";
    private static final String TEST_DEVICE_KEY = "kiosk-secret-301!";
    private static final String COURSE_ID = "30000-03";

    private static final String CLASSROOM_DEVICE_ID = "kiosk-37-b601";
    private static final String CLASSROOM_DEVICE_KEY = "kiosk-room-secret-601!";
    private static final String CLASSROOM_ID = "37-B601";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RequestIdFilter requestIdFilter;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MutableClock mutableClock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mutableClock.setInstant(TUESDAY_10_00_KST);

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter)
                .apply(springSecurity())
                .build();

        sessionRepository.deleteAll();
        deviceRepository.deleteAll();

        // 1. 단일 강좌(30000-03) 배정 단말기 등록
        deviceRepository.save(Device.builder()
                .deviceId(TEST_DEVICE_ID)
                .deviceKeyHash(passwordEncoder.encode(TEST_DEVICE_KEY))
                .name("정보기술관 301호 단말기")
                .courseId(COURSE_ID)
                .isActive(true)
                .build());

        // 2. 강의실(37-B601 = 37-나601) 배정 단말기 등록
        deviceRepository.save(Device.builder()
                .deviceId(CLASSROOM_DEVICE_ID)
                .deviceKeyHash(passwordEncoder.encode(CLASSROOM_DEVICE_KEY))
                .name("37호관 나601호 강의실 단말기")
                .courseId(CLASSROOM_ID)
                .isActive(true)
                .build());
    }

    @Test
    @DisplayName("활성화된 차시가 있을 때 정상적으로 course 정보와 session 세부 정보를 반환한다 (200 OK)")
    void getSessionWithActiveSession() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(mutableClock);

        Session activeSession = Session.builder()
                .sessionId("session-test-01")
                .courseId(COURSE_ID)
                .date(LocalDate.now(mutableClock))
                .week(4)
                .sessionNumber(1)
                .openAt(now.minusMinutes(10))
                .lateFrom(now.plusMinutes(10))
                .closeAt(now.plusMinutes(40))
                .build();
        sessionRepository.save(activeSession);

        mockMvc.perform(get("/api/v1/kiosk/courses/" + COURSE_ID + "/current-session")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.course.courseId").value(COURSE_ID))
                .andExpect(jsonPath("$.course.courseCode").value("30000"))
                .andExpect(jsonPath("$.course.name").isNotEmpty())
                .andExpect(jsonPath("$.serverTime").isNotEmpty())
                .andExpect(jsonPath("$.session.sessionId").value("session-test-01"))
                .andExpect(jsonPath("$.session.week").value(4))
                .andExpect(jsonPath("$.session.sessionNumber").value(1));
    }

    @Test
    @DisplayName("수업 외 시간이고 활성 차시가 없을 때는 항상 session: null을 반환한다 (200 OK)")
    void getSessionWithoutActiveSession() throws Exception {
        // 화요일 10:00 KST 고정 시각 -> 수요일(30000-03) 수업 시간이 아니므로 자동 세션이 생성되지 않음
        mockMvc.perform(get("/api/v1/kiosk/courses/" + COURSE_ID + "/current-session")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.course.courseId").value(COURSE_ID))
                .andExpect(jsonPath("$.session").doesNotExist());
    }

    @Test
    @DisplayName("강의실 단말기가 수업 시간 중 /current-session 호출 시 당일 세션을 자동 생성해 반환한다 (200 OK)")
    void getCurrentSessionByClassroomDuringClassAutoCreatesSession() throws Exception {
        mutableClock.setInstant(WEDNESDAY_18_05_KST);

        mockMvc.perform(get("/api/v1/kiosk/current-session")
                        .header("X-Device-Id", CLASSROOM_DEVICE_ID)
                        .header("X-Device-Key", CLASSROOM_DEVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.course.courseId").value("30000-03"))
                .andExpect(jsonPath("$.course.courseCode").value("30000"))
                .andExpect(jsonPath("$.session.sessionId").value("session-30000-03-2026-10-21"));

        assertThat(sessionRepository.findBySessionId("session-30000-03-2026-10-21")).isPresent();
    }

    @Test
    @DisplayName("강의실 단말기는 요일·시간대에 따라 같은 강의실의 다른 수업(30000-02)으로 자동 전환된다 (200 OK)")
    void getCurrentSessionByClassroomSwitchesCourseBySchedule() throws Exception {
        mutableClock.setInstant(FRIDAY_14_05_KST);

        mockMvc.perform(get("/api/v1/kiosk/current-session")
                        .header("X-Device-Id", CLASSROOM_DEVICE_ID)
                        .header("X-Device-Key", CLASSROOM_DEVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.course.courseId").value("30000-02"))
                .andExpect(jsonPath("$.session.sessionId").value("session-30000-02-2026-10-23"));
    }

    @Test
    @DisplayName("강의실 단말기가 공강 시간에 /current-session 호출 시 대표 강의와 session: null을 반환한다 (200 OK)")
    void getCurrentSessionByClassroomOutsideClassReturnsNullSession() throws Exception {
        mutableClock.setInstant(TUESDAY_10_00_KST);

        mockMvc.perform(get("/api/v1/kiosk/current-session")
                        .header("X-Device-Id", CLASSROOM_DEVICE_ID)
                        .header("X-Device-Key", CLASSROOM_DEVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.course.courseId").isNotEmpty())
                .andExpect(jsonPath("$.session").doesNotExist());
    }

    @Test
    @DisplayName("강의실 단말기가 특정 강의 경로로 접근 시 수업 시간 외에는 403, 수업 시간 중에는 200을 반환한다")
    void classroomDeviceAccessesScheduledCourseOnlyDuringClassTime() throws Exception {
        // 화요일 10:00 (수업 시간 외) -> 403 COURSE_FORBIDDEN
        mutableClock.setInstant(TUESDAY_10_00_KST);
        mockMvc.perform(get("/api/v1/kiosk/courses/" + COURSE_ID + "/current-session")
                        .header("X-Device-Id", CLASSROOM_DEVICE_ID)
                        .header("X-Device-Key", CLASSROOM_DEVICE_KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_FORBIDDEN"));

        // 수요일 18:05 (30000-03 수업 시간) -> 200 OK + 자동 세션 반환
        mutableClock.setInstant(WEDNESDAY_18_05_KST);
        mockMvc.perform(get("/api/v1/kiosk/courses/" + COURSE_ID + "/current-session")
                        .header("X-Device-Id", CLASSROOM_DEVICE_ID)
                        .header("X-Device-Key", CLASSROOM_DEVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.course.courseId").value(COURSE_ID))
                .andExpect(jsonPath("$.session.sessionId").value("session-30000-03-2026-10-21"));
    }

    @Test
    @DisplayName("배정되지 않은 다른 강의 경로로 요청하면 403 COURSE_FORBIDDEN을 반환한다")
    void getSessionOtherCourseReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/kiosk/courses/99999/current-session")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_FORBIDDEN"));
    }

    @Test
    @DisplayName("단말기 인증 헤더가 없으면 401 DEVICE_UNAUTHORIZED를 반환한다")
    void getSessionUnauthorizedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/kiosk/courses/" + COURSE_ID + "/current-session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVICE_UNAUTHORIZED"));
    }

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(TUESDAY_10_00_KST, SEOUL);
        }
    }

    static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant initialInstant, ZoneId zone) {
            this.instant = initialInstant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(this.instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
