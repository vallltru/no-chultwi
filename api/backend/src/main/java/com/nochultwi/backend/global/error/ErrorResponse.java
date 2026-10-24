package com.nochultwi.backend.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nochultwi.backend.global.filter.RequestIdFilter;

import java.util.List;

/**
 * Standard error body (Spec 7): {@code code}, {@code message}, {@code requestId}.
 * {@code errors} is an optional, additive field for validation failures. It intentionally contains only
 * the field name and the reason, never the rejected value, so passwords/tokens/keys are never echoed back.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String requestId, List<FieldErrorDetail> errors) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        String resolvedMessage = message != null ? message : errorCode.getMessage();
        return new ErrorResponse(errorCode.getCode(), resolvedMessage, RequestIdFilter.currentRequestId(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> errors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), RequestIdFilter.currentRequestId(),
                errors == null || errors.isEmpty() ? null : List.copyOf(errors));
    }

    public record FieldErrorDetail(String field, String reason) {
    }
}
