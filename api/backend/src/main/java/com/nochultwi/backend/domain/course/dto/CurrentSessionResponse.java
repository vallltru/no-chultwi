package com.nochultwi.backend.domain.course.dto;

import java.time.OffsetDateTime;

public record CurrentSessionResponse(
        KioskCourseDto course,
        OffsetDateTime serverTime,
        KioskSessionDto session
) {
}
