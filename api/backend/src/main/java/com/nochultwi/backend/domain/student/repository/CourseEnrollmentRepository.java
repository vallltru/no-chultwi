package com.nochultwi.backend.domain.student.repository;

import com.nochultwi.backend.domain.student.entity.CourseEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {

    List<CourseEnrollment> findByCourseId(String courseId);

    @Query("SELECT e FROM CourseEnrollment e JOIN FETCH e.student WHERE e.courseId = :courseId ORDER BY e.student.name ASC")
    List<CourseEnrollment> findByCourseIdWithStudent(@Param("courseId") String courseId);

    Optional<CourseEnrollment> findByCourseIdAndStudentId(String courseId, Long studentId);
}
