package com.nochultwi.backend.domain.course.dto;

import com.nochultwi.backend.domain.course.entity.Session;

import java.time.OffsetDateTime;

public record KioskSessionDto(
        String sessionId,
        int week,
        int sessionNumber,
        OffsetDateTime openAt,
        OffsetDateTime lateFrom,
        OffsetDateTime closeAt
) {
    public static KioskSessionDto from(Session session) {
        return new KioskSessionDto(
                session.getSessionId(),
                session.getWeek(),
                session.getSessionNumber(),
                session.getOpenAt(),
                session.getLateFrom(),
                session.getCloseAt()
        );
    }
}
