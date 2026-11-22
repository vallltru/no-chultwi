package com.nochultwi.backend.domain.course.port;

import com.nochultwi.backend.domain.course.dto.SchoolCourseDto;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 외부 학교 시간표 시스템 연동을 위한 도메인 포트 인터페이스.
 */
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

    default boolean isCourseScheduledAtInClassroom(SchoolCourseDto course, String classroom, OffsetDateTime time) {
        return isCourseScheduledAt(course, time);
    }
}
