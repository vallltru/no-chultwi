package com.nochultwi.backend.domain.attendance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckInResponse(
        String result,
        String status,
        OffsetDateTime checkedInAt,
        String sessionId
) {
    public static CheckInResponse checkedIn(String status, OffsetDateTime checkedInAt, String sessionId) {
        return new CheckInResponse("CHECKED_IN", status, checkedInAt, sessionId);
    }

    public static CheckInResponse alreadyCheckedIn(String status, OffsetDateTime checkedInAt, String sessionId) {
        return new CheckInResponse("ALREADY_CHECKED_IN", status, checkedInAt, sessionId);
    }
}
