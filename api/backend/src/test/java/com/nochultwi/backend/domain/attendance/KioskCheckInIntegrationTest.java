package com.nochultwi.backend.domain.attendance;

import com.nochultwi.backend.domain.attendance.dto.CheckInRequest;
import com.nochultwi.backend.domain.attendance.entity.AttendanceRecord;
import com.nochultwi.backend.domain.attendance.entity.AttendanceSource;
import com.nochultwi.backend.domain.attendance.entity.AttendanceStatus;
import com.nochultwi.backend.domain.attendance.idempotency.IdempotencyStore;
import com.nochultwi.backend.domain.attendance.repository.AttendanceRepository;
import com.nochultwi.backend.domain.attendance.token.RecognitionTokenStore;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class KioskCheckInIntegrationTest {

    private static final String TEST_DEVICE_ID = "kiosk-checkin-301";
    private static final String TEST_DEVICE_KEY = "kiosk-checkin-secret-301!";
    private static final String COURSE_ID = "30000-03";
    private static final String SESSION_ID = "session-checkin-test-01";
    private static final Long TEST_STUDENT_ID = 2026101L;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RequestIdFilter requestIdFilter;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private RecognitionTokenStore tokenStore;

    @Autowired
    private IdempotencyStore idempotencyStore;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter)
                .apply(springSecurity())
                .build();

        deviceRepository.deleteAll();
        attendanceRepository.deleteAll();
        tokenStore.clear();
        idempotencyStore.clear();

        // 1. 단말기 등록
        Device device = Device.builder()
                .deviceId(TEST_DEVICE_ID)
                .deviceKeyHash(passwordEncoder.encode(TEST_DEVICE_KEY))
                .name("키오스크 단말기 301호")
                .courseId(COURSE_ID)
                .isActive(true)
                .build();
        deviceRepository.save(device);

        // 2. 현재 활성 차시(Session) 등록
        OffsetDateTime now = OffsetDateTime.now(clock);
        sessionRepository.findBySessionId(SESSION_ID).orElseGet(() -> {
            Session s = Session.builder()
                    .sessionId(SESSION_ID)
                    .courseId(COURSE_ID)
                    .date(LocalDate.now(clock))
                    .week(8)
                    .sessionNumber(1)
                    .openAt(now.minusMinutes(10))
                    .lateFrom(now.plusMinutes(15)) // 15분 후 지각
                    .closeAt(now.plusMinutes(50))  // 50분 후 마감
                    .build();
            return sessionRepository.save(s);
        });
    }

    @Test
    @DisplayName("지각 시각 전 출석 체크인 시 201 Created와 PRESENT 상태를 반환하고 DB에 기록된다")
    void checkInSuccessPresentReturns201() throws Exception {
        RecognitionTokenStore.RecognitionTokenData tokenData = tokenStore.createToken(
                TEST_STUDENT_ID, "2026920001", "김민준", "컴퓨터과학부", 2026,
                SESSION_ID, COURSE_ID, TEST_DEVICE_ID, 60
        );

        String idempotencyKey = UUID.randomUUID().toString();
        CheckInRequest request = new CheckInRequest(tokenData.token());

        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.result").value("CHECKED_IN"))
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.checkedInAt").isNotEmpty())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID));

        assertThat(attendanceRepository.findBySessionIdAndStudentId(SESSION_ID, TEST_STUDENT_ID)).isPresent();
    }

    @Test
    @DisplayName("지각 시각 이후 출석 체크인 시 201 Created와 LATE 상태를 반환한다")
    void checkInSuccessLateReturns201() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String lateSessionId = "session-late-test-01";
        sessionRepository.save(Session.builder()
                .sessionId(lateSessionId)
                .courseId(COURSE_ID)
                .date(LocalDate.now(clock))
                .week(8)
                .sessionNumber(2)
                .openAt(now.minusMinutes(30))
                .lateFrom(now.minusMinutes(10)) // 10분 전부터 이미 지각
                .closeAt(now.plusMinutes(30))
                .build());

        RecognitionTokenStore.RecognitionTokenData tokenData = tokenStore.createToken(
                TEST_STUDENT_ID, "2026920001", "김민준", "컴퓨터과학부", 2026,
                lateSessionId, COURSE_ID, TEST_DEVICE_ID, 60
        );

        String idempotencyKey = UUID.randomUUID().toString();
        CheckInRequest request = new CheckInRequest(tokenData.token());

        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("CHECKED_IN"))
                .andExpect(jsonPath("$.status").value("LATE"));
    }

    @Test
    @DisplayName("동일한 멱등성 키로 재시도 시 토큰이 소모되었더라도 최초 201 응답과 동일한 결과를 반환한다")
    void checkInIdempotentReplayReturnsSame201() throws Exception {
        RecognitionTokenStore.RecognitionTokenData tokenData = tokenStore.createToken(
                TEST_STUDENT_ID, "2026920001", "김민준", "컴퓨터과학부", 2026,
                SESSION_ID, COURSE_ID, TEST_DEVICE_ID, 60
        );

        String idempotencyKey = UUID.randomUUID().toString();
        CheckInRequest request = new CheckInRequest(tokenData.token());

        // 첫 번째 호출: 201 Created
        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("CHECKED_IN"));

        // 두 번째 호출: 동일한 키와 동일 본문 재시도 -> 캐시된 201 Created 동일 반환
        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("CHECKED_IN"));

        // 중복 레코드 삽입 없이 레코드는 1개만 유지
        assertThat(attendanceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일한 멱등성 키로 다른 본문 전송 시 409 IDEMPOTENCY_KEY_REUSED를 반환한다")
    void checkInIdempotentKeyReusedWithDifferentBodyReturns409() throws Exception {
        RecognitionTokenStore.RecognitionTokenData token1 = tokenStore.createToken(
                TEST_STUDENT_ID, "2026920001", "김민준", "컴퓨터과학부", 2026,
                SESSION_ID, COURSE_ID, TEST_DEVICE_ID, 60
        );

        String idempotencyKey = UUID.randomUUID().toString();
        CheckInRequest req1 = new CheckInRequest(token1.token());

        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // 다른 토큰으로 동일 키 전송
        CheckInRequest req2 = new CheckInRequest("different-token");
        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("이미 소모된 토큰으로 다른 요청 전송 시 409 RECOGNITION_CONSUMED를 반환한다")
    void checkInConsumedTokenReturns409() throws Exception {
        RecognitionTokenStore.RecognitionTokenData tokenData = tokenStore.createToken(
                TEST_STUDENT_ID, "2026920001", "김민준", "컴퓨터과학부", 2026,
                SESSION_ID, COURSE_ID, TEST_DEVICE_ID, 60
        );

        // 첫 번째 체크인 성공
        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckInRequest(tokenData.token()))))
                .andExpect(status().isCreated());

        // 이미 소비된 토큰으로 새로운 멱등키를 사용해 호출
        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckInRequest(tokenData.token()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOGNITION_CONSUMED"));
    }

    @Test
    @DisplayName("유효하지 않거나 만료된 토큰 전송 시 409 RECOGNITION_EXPIRED를 반환한다")
    void checkInExpiredTokenReturns409() throws Exception {
        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckInRequest("non-existent-token"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOGNITION_EXPIRED"));
    }

    @Test
    @DisplayName("토큰을 발급한 단말기와 다른 단말기에서 전송 시 409 RECOGNITION_DEVICE_MISMATCH를 반환한다")
    void checkInDeviceMismatchReturns409() throws Exception {
        // device-A에 발급된 토큰
        RecognitionTokenStore.RecognitionTokenData tokenData = tokenStore.createToken(
                TEST_STUDENT_ID, "2026920001", "김민준", "컴퓨터과학부", 2026,
                SESSION_ID, COURSE_ID, "other-device-id", 60
        );

        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckInRequest(tokenData.token()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOGNITION_DEVICE_MISMATCH"));
    }

    @Test
    @DisplayName("관리자가 결석(ABSENT)으로 잠근 학생은 409 ATTENDANCE_LOCKED를 반환한다")
    void checkInStudentLockedReturns409() throws Exception {
        attendanceRepository.save(AttendanceRecord.builder()
                .sessionId(SESSION_ID)
                .studentId(TEST_STUDENT_ID)
                .status(AttendanceStatus.ABSENT)
                .source(AttendanceSource.ADMIN)
                .isManualOverride(true)
                .overrideReason("출석 부정 적발")
                .build());

        RecognitionTokenStore.RecognitionTokenData tokenData = tokenStore.createToken(
                TEST_STUDENT_ID, "2026920001", "김민준", "컴퓨터과학부", 2026,
                SESSION_ID, COURSE_ID, TEST_DEVICE_ID, 60
        );

        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckInRequest(tokenData.token()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_LOCKED"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 누락되면 400 INVALID_REQUEST를 반환한다")
    void checkInMissingIdempotencyKeyReturns400() throws Exception {
        CheckInRequest request = new CheckInRequest("some-token");

        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("단말기 인증 헤더가 누락되면 401 DEVICE_UNAUTHORIZED를 반환한다")
    void checkInMissingDeviceAuthReturns401() throws Exception {
        CheckInRequest request = new CheckInRequest("some-token");

        mockMvc.perform(post("/api/v1/kiosk/check-ins")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVICE_UNAUTHORIZED"));
    }
}
