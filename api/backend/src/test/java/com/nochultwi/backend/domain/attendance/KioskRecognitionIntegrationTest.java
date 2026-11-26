package com.nochultwi.backend.domain.attendance;

import com.nochultwi.backend.domain.attendance.entity.AttendanceRecord;
import com.nochultwi.backend.domain.attendance.entity.AttendanceSource;
import com.nochultwi.backend.domain.attendance.entity.AttendanceStatus;
import com.nochultwi.backend.domain.attendance.repository.AttendanceRepository;
import com.nochultwi.backend.domain.attendance.token.RecognitionTokenStore;
import com.nochultwi.backend.domain.course.entity.Session;
import com.nochultwi.backend.domain.course.repository.SessionRepository;
import com.nochultwi.backend.domain.device.entity.Device;
import com.nochultwi.backend.domain.device.repository.DeviceRepository;
import com.nochultwi.backend.domain.student.entity.CourseEnrollment;
import com.nochultwi.backend.domain.student.entity.Student;
import com.nochultwi.backend.domain.student.repository.CourseEnrollmentRepository;
import com.nochultwi.backend.domain.student.repository.StudentRepository;
import com.nochultwi.backend.global.filter.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class KioskRecognitionIntegrationTest {

    private static final String TEST_DEVICE_ID = "kiosk-test-601";
    private static final String TEST_DEVICE_KEY = "kiosk-test-secret-601!";
    private static final String COURSE_ID = "TEST-REC-COURSE-01";
    private static final String SESSION_ID = "session-test-kiosk-rec";

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
    private StudentRepository studentRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private RecognitionTokenStore tokenStore;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private MockMvc mockMvc;
    private Student testStudent;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter)
                .apply(springSecurity())
                .build();

        deviceRepository.deleteAll();
        attendanceRepository.deleteAll();
        tokenStore.clear();

        // 1. 단말기 등록 (테스트 전용 강좌 배정)
        Device device = Device.builder()
                .deviceId(TEST_DEVICE_ID)
                .deviceKeyHash(passwordEncoder.encode(TEST_DEVICE_KEY))
                .name("37호관 나601호 단말기")
                .courseId(COURSE_ID)
                .isActive(true)
                .build();
        deviceRepository.save(device);

        // 2. 데모 시드 데이터에 의존하지 않도록 테스트 자체 수강생·수강신청 데이터 생성
        float[] faceVector = new float[512];
        faceVector[0] = 1.0f;
        testStudent = studentRepository.save(Student.builder()
                .studentNumber("2026880001")
                .name("인식테스트학생")
                .department("컴퓨터과학부")
                .admissionYear(2026)
                .faceVector(Student.floatsToBytes(faceVector))
                .build());
        courseEnrollmentRepository.save(new CourseEnrollment(COURSE_ID, testStudent));

        // 3. 현재 열려 있는 차시(Session) 등록
        OffsetDateTime now = OffsetDateTime.now(clock);
        sessionRepository.findBySessionId(SESSION_ID).orElseGet(() -> {
            Session s = Session.builder()
                    .sessionId(SESSION_ID)
                    .courseId(COURSE_ID)
                    .date(LocalDate.now(clock))
                    .week(8)
                    .sessionNumber(1)
                    .openAt(now.minusMinutes(10))
                    .lateFrom(now.plusMinutes(20))
                    .closeAt(now.plusMinutes(50))
                    .build();
            return sessionRepository.save(s);
        });
    }

    @Test
    @DisplayName("정상 얼굴 이미지 전송 시 200 OK와 MATCHED 결과 및 60초 1회용 토큰을 발급한다")
    void recognizeSuccessMatched() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3, 4, 5}
        );

        mockMvc.perform(multipart("/api/v1/kiosk/recognitions")
                        .file(imageFile)
                        .param("courseId", COURSE_ID)
                        .param("sessionId", SESSION_ID)
                        .param("capturedAt", OffsetDateTime.now(clock).toString())
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.result").value("MATCHED"))
                .andExpect(jsonPath("$.recognitionToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.candidate.name").value("인식테스트학생"))
                .andExpect(jsonPath("$.candidate.department").value("컴퓨터과학부"))
                .andExpect(jsonPath("$.candidate.admissionYear").value(2026));
    }

    @Test
    @DisplayName("얼굴 미검출 또는 매칭 미달 시 200 OK와 RETRY 결과를 반환한다")
    void recognizeUnrecognizedReturnsRetry() throws Exception {
        // Stub 어댑터에서 1바이트 0x00은 인식 실패 시뮬레이션
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "face.jpg", "image/jpeg", new byte[]{0}
        );

        mockMvc.perform(multipart("/api/v1/kiosk/recognitions")
                        .file(imageFile)
                        .param("courseId", COURSE_ID)
                        .param("sessionId", SESSION_ID)
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("RETRY"))
                .andExpect(jsonPath("$.message").value("얼굴을 확인하지 못했어요. 다시 시도해 주세요"))
                .andExpect(jsonPath("$.recognitionToken").doesNotExist());
    }

    @Test
    @DisplayName("이미 출석 완료된 학생인 경우 200 OK와 ALREADY_CHECKED_IN 결과를 반환한다")
    void recognizeAlreadyCheckedIn() throws Exception {
        attendanceRepository.save(AttendanceRecord.builder()
                .sessionId(SESSION_ID)
                .studentId(testStudent.getId())
                .status(AttendanceStatus.PRESENT)
                .source(AttendanceSource.FACE)
                .checkedInAt(OffsetDateTime.now(clock).minusMinutes(5))
                .isManualOverride(false)
                .build());

        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/kiosk/recognitions")
                        .file(imageFile)
                        .param("courseId", COURSE_ID)
                        .param("sessionId", SESSION_ID)
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"))
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.recognitionToken").doesNotExist());
    }

    @Test
    @DisplayName("관리자에 의해 결석(ABSENT)으로 잠긴 학생인 경우 200 OK와 ATTENDANCE_LOCKED를 반환한다")
    void recognizeAttendanceLocked() throws Exception {
        attendanceRepository.save(AttendanceRecord.builder()
                .sessionId(SESSION_ID)
                .studentId(testStudent.getId())
                .status(AttendanceStatus.ABSENT)
                .source(AttendanceSource.ADMIN)
                .isManualOverride(true)
                .overrideReason("무단 이탈 적발")
                .build());

        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/kiosk/recognitions")
                        .file(imageFile)
                        .param("courseId", COURSE_ID)
                        .param("sessionId", SESSION_ID)
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ATTENDANCE_LOCKED"))
                .andExpect(jsonPath("$.message").value("관리자에게 출결 상태를 문의해 주세요"))
                .andExpect(jsonPath("$.recognitionToken").doesNotExist());
    }

    @Test
    @DisplayName("아직 열리지 않은 차시로 요청 시 409 SESSION_NOT_OPEN을 반환한다")
    void recognizeSessionNotOpenReturns409() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Session futureSession = sessionRepository.save(Session.builder()
                .sessionId("session-future-01")
                .courseId(COURSE_ID)
                .date(LocalDate.now(clock).plusDays(1))
                .week(9)
                .sessionNumber(1)
                .openAt(now.plusHours(2))
                .lateFrom(now.plusHours(3))
                .closeAt(now.plusHours(4))
                .build());

        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/kiosk/recognitions")
                        .file(imageFile)
                        .param("courseId", COURSE_ID)
                        .param("sessionId", futureSession.getSessionId())
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_OPEN"));
    }

    @Test
    @DisplayName("이미 종료된 차시로 요청 시 409 SESSION_CLOSED를 반환한다")
    void recognizeSessionClosedReturns409() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Session closedSession = sessionRepository.save(Session.builder()
                .sessionId("session-past-01")
                .courseId(COURSE_ID)
                .date(LocalDate.now(clock).minusDays(1))
                .week(7)
                .sessionNumber(1)
                .openAt(now.minusHours(4))
                .lateFrom(now.minusHours(3))
                .closeAt(now.minusHours(2))
                .build());

        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/kiosk/recognitions")
                        .file(imageFile)
                        .param("courseId", COURSE_ID)
                        .param("sessionId", closedSession.getSessionId())
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_CLOSED"));
    }

    @Test
    @DisplayName("단말기 배정 강의와 다른 강의 ID로 요청 시 403 COURSE_FORBIDDEN을 반환한다")
    void recognizeOtherCourseReturns403() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/kiosk/recognitions")
                        .file(imageFile)
                        .param("courseId", "99999-01")
                        .param("sessionId", SESSION_ID)
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_FORBIDDEN"));
    }

    @Test
    @DisplayName("지원하지 않는 파일 형식(PDF) 전송 시 415 UNSUPPORTED_IMAGE_TYPE을 반환한다")
    void recognizeUnsupportedFileTypeReturns415() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "image", "document.pdf", "application/pdf", new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/kiosk/recognitions")
                        .file(pdfFile)
                        .param("courseId", COURSE_ID)
                        .param("sessionId", SESSION_ID)
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));
    }

    @Test
    @DisplayName("단말기 인증 헤더가 누락되면 401 DEVICE_UNAUTHORIZED를 반환한다")
    void recognizeMissingHeadersReturns401() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/kiosk/recognitions")
                        .file(imageFile)
                        .param("courseId", COURSE_ID)
                        .param("sessionId", SESSION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVICE_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("5MB를 초과하는 대용량 이미지 전송 시 413 IMAGE_TOO_LARGE를 반환한다")
    void recognizeImageExceeds5MbReturns413() throws Exception {
        byte[] oversizedBytes = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile largeImage = new MockMultipartFile(
                "image", "large.jpg", "image/jpeg", oversizedBytes
        );

        mockMvc.perform(multipart("/api/v1/kiosk/recognitions")
                        .file(largeImage)
                        .param("courseId", COURSE_ID)
                        .param("sessionId", SESSION_ID)
                        .header("X-Device-Id", TEST_DEVICE_ID)
                        .header("X-Device-Key", TEST_DEVICE_KEY))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
    }
}
