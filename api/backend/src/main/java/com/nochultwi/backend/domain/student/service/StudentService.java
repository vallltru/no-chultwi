package com.nochultwi.backend.domain.student.service;

import com.nochultwi.backend.domain.student.dto.EnrolledStudentDto;
import com.nochultwi.backend.domain.student.dto.StudentDto;
import com.nochultwi.backend.domain.student.entity.CourseEnrollment;
import com.nochultwi.backend.domain.student.entity.Student;
import com.nochultwi.backend.domain.student.repository.CourseEnrollmentRepository;
import com.nochultwi.backend.domain.student.repository.StudentRepository;
import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentService {

    private final StudentRepository studentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public List<EnrolledStudentDto> getEnrolledStudents(String courseId) {
        return courseEnrollmentRepository.findByCourseIdWithStudent(courseId)
                .stream()
                .map(EnrolledStudentDto::from)
                .toList();
    }

    public StudentDto getStudentByNumber(String studentNumber) {
        return studentRepository.findByStudentNumber(studentNumber)
                .map(StudentDto::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND, "학생을 찾을 수 없습니다."));
    }

    public StudentDto getStudentById(Long studentId) {
        return studentRepository.findById(studentId)
                .map(StudentDto::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND, "학생을 찾을 수 없습니다."));
    }

    @Transactional
    public void updateFaceVector(Long studentId, byte[] faceVector) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND, "학생을 찾을 수 없습니다."));
        student.updateFaceVector(faceVector);
        log.info("Face vector updated for student: studentNumber={}", student.getStudentNumber());
    }

    @Transactional
    public void enrollStudent(String courseId, Long studentId) {
        if (courseEnrollmentRepository.findByCourseIdAndStudentId(courseId, studentId).isPresent()) {
            return;
        }
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND, "학생을 찾을 수 없습니다."));
        courseEnrollmentRepository.save(new CourseEnrollment(courseId, student));
        log.info("Student enrolled: courseId={}, studentNumber={}", courseId, student.getStudentNumber());
    }
}
