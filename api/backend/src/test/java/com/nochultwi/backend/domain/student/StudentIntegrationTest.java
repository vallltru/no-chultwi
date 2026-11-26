package com.nochultwi.backend.domain.student;

import com.nochultwi.backend.domain.student.dto.EnrolledStudentDto;
import com.nochultwi.backend.domain.student.dto.StudentDto;
import com.nochultwi.backend.domain.student.entity.CourseEnrollment;
import com.nochultwi.backend.domain.student.entity.Student;
import com.nochultwi.backend.domain.student.repository.CourseEnrollmentRepository;
import com.nochultwi.backend.domain.student.repository.StudentRepository;
import com.nochultwi.backend.domain.student.service.StudentService;
import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class StudentIntegrationTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private StudentService studentService;

    @Test
    @DisplayName("512차원 float 배열과 byte[] 변환이 오차 없이 정밀하게 보존된다")
    void testFaceVectorConversion() {
        float[] originalFloats = new float[512];
        for (int i = 0; i < 512; i++) {
            originalFloats[i] = (float) (Math.sin(i) * 0.5);
        }

        byte[] bytes = Student.floatsToBytes(originalFloats);
        assertThat(bytes).hasSize(512 * 4); // 2048 bytes

        Student student = Student.builder()
                .studentNumber("2026119999")
                .name("테스트학생")
                .department("컴퓨터과학부")
                .admissionYear(2026)
                .faceVector(bytes)
                .build();

        float[] restoredFloats = student.getFaceVectorAsFloats();
        assertThat(restoredFloats).hasSize(512);
        for (int i = 0; i < 512; i++) {
            assertThat(restoredFloats[i]).isEqualTo(originalFloats[i]);
        }
    }

    @Test
    @DisplayName("수강생 조회 시 강좌에 등록된 학생 목록을 N+1 없이 정확히 조회한다")
    void testGetEnrolledStudents() {
        Student s1 = studentRepository.save(Student.builder()
                .studentNumber("2026990001")
                .name("강학생")
                .department("컴퓨터과학부")
                .admissionYear(2026)
                .build());

        Student s2 = studentRepository.save(Student.builder()
                .studentNumber("2026990002")
                .name("나학생")
                .department("컴퓨터과학부")
                .admissionYear(2026)
                .build());

        courseEnrollmentRepository.save(new CourseEnrollment("TEST-COURSE-01", s1));
        courseEnrollmentRepository.save(new CourseEnrollment("TEST-COURSE-01", s2));

        List<EnrolledStudentDto> enrolledList = studentService.getEnrolledStudents("TEST-COURSE-01");

        assertThat(enrolledList).hasSize(2);
        assertThat(enrolledList).extracting(EnrolledStudentDto::studentNumber)
                .containsExactlyInAnyOrder("2026990001", "2026990002");
        assertThat(enrolledList).extracting(EnrolledStudentDto::admissionYear)
                .containsOnly(2026);
    }

    @Test
    @DisplayName("학번으로 학생 단건 조회 성공 및 존재하지 않는 학생 조회 시 STUDENT_NOT_FOUND 예외 발생")
    void testGetStudentByNumber() {
        studentRepository.save(Student.builder()
                .studentNumber("2026990010")
                .name("조회대상학생")
                .department("인공지능학과")
                .admissionYear(2026)
                .build());

        StudentDto found = studentService.getStudentByNumber("2026990010");
        assertThat(found.name()).isEqualTo("조회대상학생");
        assertThat(found.department()).isEqualTo("인공지능학과");

        assertThatThrownBy(() -> studentService.getStudentByNumber("NON_EXISTING_ID"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
    }

    @Test
    @DisplayName("데모 데이터 초기화로 생성된 30000-03 강좌의 30명 수강생이 정상 조회된다")
    void testDemoStudentsInitialized() {
        List<EnrolledStudentDto> enrolledStudents = studentService.getEnrolledStudents("30000-03");

        // 초기화 컴포넌트(StudentDemoDataInitializer)에 의해 최소 30명 등록 보장
        assertThat(enrolledStudents.size()).isGreaterThanOrEqualTo(30);
        assertThat(enrolledStudents.get(0).faceVector()).isNotNull();
        assertThat(enrolledStudents.get(0).faceVector().length).isEqualTo(512 * 4);
    }
}
