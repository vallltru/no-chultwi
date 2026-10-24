package com.nochultwi.backend.domain.device;

import com.nochultwi.backend.domain.device.entity.Device;
import com.nochultwi.backend.domain.device.repository.DeviceRepository;
import com.nochultwi.backend.global.filter.RequestIdFilter;
import com.nochultwi.backend.global.security.device.DevicePrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(DeviceAuthIntegrationTest.KioskProbeController.class)
class DeviceAuthIntegrationTest {

    private static final String TEST_DEVICE_ID = "kiosk-it-301";
    private static final String TEST_RAW_KEY = "kiosk-secret-key-1234!";
    private static final String ASSIGNED_COURSE_ID = "30000-03";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RequestIdFilter requestIdFilter;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter)
                .apply(springSecurity())
                .build();

        deviceRepository.deleteAll();

        // 활성화된 테스트 단말기 등록
        Device activeDevice = Device.builder()
                .deviceId(TEST_DEVICE_ID)
                .deviceKeyHash(passwordEncoder.encode(TEST_RAW_KEY))
                .name("정보기술관 301호 단말기")
                .courseId(ASSIGNED_COURSE_ID)
                .isActive(true)
                .build();
        deviceRepository.save(activeDevice);

        // 비활성화된 테스트 단말기 등록
        Device inactiveDevice = Device.builder()
                .deviceId("kiosk-disabled")
                .deviceKeyHash(passwordEncoder.encode(TEST_RAW_KEY))
                .name("폐기된 단말기")
                .courseId(ASSIGNED_COURSE_ID)
                .isActive(false)
                .build();
        deviceRepository.save(inactiveDevice);
    }

    @Test
    @DisplayName("단말기 인증 헤더(X-Device-Id, X-Device-Key)가 누락되면 401 DEVICE_UNAUTHORIZED를 반환한다")
    void missingHeadersReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/kiosk/courses/30000-03/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVICE_UNAUTHORIZED"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("등록되지 않은 단말기 ID로 요청하면 401 DEVICE_UNAUTHORIZED를 반환한다")
    void unregisteredDeviceReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/kiosk/courses/30000-03/probe")
                        .header("X-Device-Id", "unknown-kiosk")
                        .header("X-Device-Key", TEST_RAW_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVICE_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("단말기 비밀키가 불일치하면 401 DEVICE_UNAUTHORIZED를 반환한다")
    void invalidKeyReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/kiosk/courses/30000-03/probe")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", "wrong-secret-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVICE_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("비활성화된 단말기(isActive=false)는 401 DEVICE_UNAUTHORIZED를 반환한다")
    void inactiveDeviceReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/kiosk/courses/30000-03/probe")
                        .header("X-Device-Id", "kiosk-disabled")
                        .header("X-Device-Key", TEST_RAW_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVICE_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("배정되지 않은 다른 강의 경로(/courses/other-course/**)로 접근하면 403 COURSE_FORBIDDEN을 반환한다")
    void wrongCourseReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/kiosk/courses/99999/probe")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_RAW_KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("해당 강의에 대한 단말기 접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("정상 인증된 단말기는 200 OK와 함께 배정된 강의 경로에 접근할 수 있다")
    void validDevicePasses() throws Exception {
        mockMvc.perform(get("/api/v1/kiosk/courses/30000-03/probe")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(TEST_DEVICE_ID))
                .andExpect(jsonPath("$.courseId").value(ASSIGNED_COURSE_ID))
                .andExpect(header().exists("X-Request-Id"));
    }

    @RestController
    @RequestMapping("/api/v1/kiosk")
    static class KioskProbeController {

        @GetMapping("/courses/{courseId}/probe")
        public ResponseEntity<Map<String, Object>> probeSession(
                @PathVariable String courseId,
                @AuthenticationPrincipal DevicePrincipal principal) {
            return ResponseEntity.ok(Map.of(
                    "courseId", courseId,
                    "deviceId", principal.deviceId(),
                    "name", principal.name()
            ));
        }
    }
}
