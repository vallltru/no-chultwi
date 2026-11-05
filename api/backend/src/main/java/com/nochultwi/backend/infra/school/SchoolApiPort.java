package com.nochultwi.backend.infra.school;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface SchoolApiPort {

    Optional<SchoolCourseDto> getCourseInfo(String courseId);

    default List<SchoolCourseDto> getCoursesByClassroom(String classroom) {
        return Collections.emptyList();
    }

    default boolean isSameClassroom(String roomA, String roomB) {
        return false;
    }

    default boolean isCourseScheduledAt(SchoolCourseDto course, OffsetDateTime time) {
        return false;
    }
}
