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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class KioskCourseIntegrationTest {

    private static final String TEST_DEVICE_ID = "kiosk-301";
    private static final String TEST_DEVICE_KEY = "kiosk-secret-301!";
    private static final String COURSE_ID = "30000-03";

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
    private Clock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter)
                .apply(springSecurity())
                .build();

        sessionRepository.deleteAll();
        deviceRepository.deleteAll();

        // 단말기 등록 (30000 강좌 배정)
        Device device = Device.builder()
                .deviceId(TEST_DEVICE_ID)
                .deviceKeyHash(passwordEncoder.encode(TEST_DEVICE_KEY))
                .name("정보기술관 301호 단말기")
                .courseId(COURSE_ID)
                .isActive(true)
                .build();
        deviceRepository.save(device);
    }

    @Test
    @DisplayName("활성화된 차시가 있을 때 정상적으로 course 정보와 session 세부 정보를 반환한다 (200 OK)")
    void getSessionWithActiveSession() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(clock);

        Session activeSession = Session.builder()
                .sessionId("session-test-01")
                .courseId(COURSE_ID)
                .date(LocalDate.now(clock))
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
    @DisplayName("현재 열려 있는 차시가 없을 때는 session: null을 반환한다 (200 OK)")
    void getSessionWithoutActiveSession() throws Exception {
        mockMvc.perform(get("/api/v1/kiosk/courses/" + COURSE_ID + "/current-session")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.course.courseId").value(COURSE_ID))
                .andExpect(jsonPath("$.session").doesNotExist());
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
}
