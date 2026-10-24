package com.nochultwi.backend.domain.attendance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecognitionResponse(
        String result,
        String recognitionToken,
        OffsetDateTime expiresAt,
        CandidateDto candidate,
        String status,
        OffsetDateTime checkedInAt,
        String message
) {
    public static RecognitionResponse matched(String token, OffsetDateTime expiresAt, CandidateDto candidate) {
        return new RecognitionResponse("MATCHED", token, expiresAt, candidate, null, null, null);
    }

    public static RecognitionResponse alreadyCheckedIn(String status, OffsetDateTime checkedInAt) {
        return new RecognitionResponse("ALREADY_CHECKED_IN", null, null, null, status, checkedInAt, null);
    }

    public static RecognitionResponse attendanceLocked(String message) {
        return new RecognitionResponse("ATTENDANCE_LOCKED", null, null, null, null, null, message);
    }

    public static RecognitionResponse retry(String message) {
        return new RecognitionResponse("RETRY", null, null, null, null, null, message);
    }
}
