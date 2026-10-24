package com.nochultwi.backend.domain.student.dto;

import com.nochultwi.backend.domain.student.entity.CourseEnrollment;
import com.nochultwi.backend.domain.student.entity.Student;

public record EnrolledStudentDto(
        Long studentId,
        String studentNumber,
        String name,
        String department,
        int admissionYear,
        byte[] faceVector
) {
    public static EnrolledStudentDto from(CourseEnrollment enrollment) {
        Student s = enrollment.getStudent();
        return new EnrolledStudentDto(
                s.getId(),
                s.getStudentNumber(),
                s.getName(),
                s.getDepartment(),
                s.getAdmissionYear(),
                s.getFaceVector()
        );
    }
}
