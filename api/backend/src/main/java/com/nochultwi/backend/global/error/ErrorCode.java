package com.nochultwi.backend.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Error codes shared with the frontend (Spec 7). Codes are a contract: the screens branch on them,
 * so do not rename existing values. Domain-specific codes are added in the feature branch that needs them.
 */
@Getter
public enum ErrorCode {

    // 400
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    DEVICE_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "DEVICE_UNAUTHORIZED", "단말기 인증에 실패했습니다."),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다."),
    COURSE_FORBIDDEN(HttpStatus.FORBIDDEN, "COURSE_FORBIDDEN", "해당 강의에 대한 접근 권한이 없습니다."),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "강의를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "차시 정보를 찾을 수 없습니다."),
    STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "수강생 정보를 찾을 수 없습니다."),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),

    // 409
    SESSION_NOT_OPEN(HttpStatus.CONFLICT, "SESSION_NOT_OPEN", "아직 출석 가능 시간이 아닙니다."),
    SESSION_CLOSED(HttpStatus.CONFLICT, "SESSION_CLOSED", "출석 마감 시간이 지났습니다."),
    RECOGNITION_EXPIRED(HttpStatus.CONFLICT, "RECOGNITION_EXPIRED", "얼굴을 다시 인식해 주세요."),
    RECOGNITION_CONSUMED(HttpStatus.CONFLICT, "RECOGNITION_CONSUMED", "이미 사용된 인식 결과입니다. 얼굴을 다시 인식해 주세요."),
    RECOGNITION_DEVICE_MISMATCH(HttpStatus.CONFLICT, "RECOGNITION_DEVICE_MISMATCH", "인식을 요청한 단말기와 일치하지 않습니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "동일한 멱등성 키로 다른 요청이 전송되었습니다."),
    ATTENDANCE_LOCKED(HttpStatus.CONFLICT, "ATTENDANCE_LOCKED", "관리자에게 출결 상태를 문의해 주세요."),

    // 413 / 415
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE", "이미지 파일 크기가 제한을 초과했습니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE", "지원하지 않는 이미지 형식입니다. (JPEG/PNG)"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type입니다."),

    // 500 / 503
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    RECOGNITION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "RECOGNITION_UNAVAILABLE", "얼굴 인식 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
