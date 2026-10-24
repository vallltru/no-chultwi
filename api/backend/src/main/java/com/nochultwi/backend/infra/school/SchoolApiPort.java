package com.nochultwi.backend.infra.school;

import java.util.Optional;

public interface SchoolApiPort {

    Optional<SchoolCourseDto> getCourseInfo(String courseId);
}
