package com.nochultwi.backend.domain.student.data;

import com.nochultwi.backend.domain.course.entity.Session;
import com.nochultwi.backend.domain.course.repository.SessionRepository;
import com.nochultwi.backend.domain.student.entity.CourseEnrollment;
import com.nochultwi.backend.domain.student.entity.Student;
import com.nochultwi.backend.domain.student.repository.CourseEnrollmentRepository;
import com.nochultwi.backend.domain.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 로컬 개발 및 시연 환경(local/default)에서 30000-03(창의공학기초설계 03분반) 강좌의
 * 30명 수강생(512차원 얼굴 임베딩 벡터 포함)과 당일 활성 세션을 자동으로 초기화합니다.
 */
@Slf4j
@Component
@Profile({"local", "default"})
@Order(10)
@RequiredArgsConstructor
public class StudentDemoDataInitializer implements CommandLineRunner {

    private static final String TARGET_COURSE_ID = "30000-03";
    private static final int DEMO_STUDENT_COUNT = 30;

    private final StudentRepository studentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final SessionRepository sessionRepository;
    private final Clock clock;

    private static final String[] FIRST_NAMES = {
            "민준", "서준", "도윤", "예준", "시우", "하준", "지호", "지후", "준우", "준서",
            "서연", "서윤", "지우", "서현", "하은", "하윤", "민서", "지유", "윤서", "지아",
            "채원", "다은", "수아", "지민", "지원", "예은", "소율", "예원", "시은", "수빈"
    };

    private static final String[] LAST_NAMES = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임"};

    @Override
    @Transactional
    public void run(String... args) {
        if (studentRepository.count() > 0) {
            log.info("Student data already exists (count={}). Skipping demo student initialization.", studentRepository.count());
            return;
        }

        log.info("Initializing {} demo students and enrollment for course {}...", DEMO_STUDENT_COUNT, TARGET_COURSE_ID);
        List<Student> students = new ArrayList<>();
        Random random = new Random(20261025L); // 재현 가능한 고정 시드

        for (int i = 0; i < DEMO_STUDENT_COUNT; i++) {
            String studentNumber = String.format("202692%04d", i + 1);
            String name = LAST_NAMES[i % LAST_NAMES.length] + FIRST_NAMES[i % FIRST_NAMES.length];

            // 512차원 정규화된(L2-normalized) 더미 얼굴 특징 벡터 생성
            float[] vector = generateNormalizedVector(512, random);
            byte[] faceVectorBytes = Student.floatsToBytes(vector);

            Student student = Student.builder()
                    .studentNumber(studentNumber)
                    .name(name)
                    .department("컴퓨터과학부")
                    .admissionYear(2026)
                    .faceVector(faceVectorBytes)
                    .build();

            students.add(student);
        }

        List<Student> savedStudents = studentRepository.saveAll(students);

        List<CourseEnrollment> enrollments = savedStudents.stream()
                .map(s -> new CourseEnrollment(TARGET_COURSE_ID, s))
                .toList();
        courseEnrollmentRepository.saveAll(enrollments);

        // 시연 편의를 위해 당일 진행 중인 활성 세션(Session)이 없으면 자동 생성
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Session> activeSessions = sessionRepository.findActiveSessions(TARGET_COURSE_ID, now);
        if (activeSessions.isEmpty()) {
            Session demoSession = Session.builder()
                    .sessionId("session-2026-demo-01")
                    .courseId(TARGET_COURSE_ID)
                    .date(LocalDate.now(clock))
                    .week(8)
                    .sessionNumber(1)
                    .openAt(now.minusHours(1))
                    .lateFrom(now.plusHours(1))
                    .closeAt(now.plusHours(3))
                    .build();
            sessionRepository.save(demoSession);
            log.info("Demo active session initialized: sessionId={}, courseId={}", demoSession.getSessionId(), TARGET_COURSE_ID);
        }

        log.info("Successfully initialized {} demo students and enrollments for course {}", savedStudents.size(), TARGET_COURSE_ID);
    }

    private float[] generateNormalizedVector(int dim, Random random) {
        float[] vector = new float[dim];
        float sumSquare = 0.0f;
        for (int i = 0; i < dim; i++) {
            vector[i] = (float) random.nextGaussian();
            sumSquare += vector[i] * vector[i];
        }
        float norm = (float) Math.sqrt(sumSquare);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }
}
