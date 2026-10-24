package com.nochultwi.backend.infra.school;

public record SchoolCourseDto(
        String courseId,
        String courseCode,
        String name,
        String professorName,
        String department,
        String dvclNo,
        String classTimeAndRoom,
        String dayOfWeek,
        String classroom,
        Integer startPeriod,
        Integer endPeriod
) {
    public SchoolCourseDto(
            String courseId,
            String courseCode,
            String name,
            String professorName,
            String department,
            String dvclNo
    ) {
        this(courseId, courseCode, name, professorName, department, dvclNo, null, null, null, null, null);
    }
}
