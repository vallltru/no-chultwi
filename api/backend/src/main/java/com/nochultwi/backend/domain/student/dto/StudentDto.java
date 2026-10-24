package com.nochultwi.backend.domain.student.dto;

import com.nochultwi.backend.domain.student.entity.Student;

public record StudentDto(
        Long id,
        String studentNumber,
        String name,
        String department,
        int admissionYear,
        boolean hasFaceVector
) {
    public static StudentDto from(Student student) {
        return new StudentDto(
                student.getId(),
                student.getStudentNumber(),
                student.getName(),
                student.getDepartment(),
                student.getAdmissionYear(),
                student.getFaceVector() != null && student.getFaceVector().length > 0
        );
    }
}
