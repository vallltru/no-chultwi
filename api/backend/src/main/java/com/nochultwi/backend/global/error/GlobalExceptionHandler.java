package com.nochultwi.backend.global.error;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts every exception into the standard {@link ErrorResponse} (Spec 7).
 * <p>
 * Extends {@link ResponseEntityExceptionHandler} so that all Spring MVC infrastructure exceptions
 * (unknown path 404, wrong method 405, wrong content type 415, upload size 413, malformed JSON 400, ...)
 * keep their correct HTTP status instead of falling through to the generic 500 handler.
 * Internal exception messages are logged on the server only and never returned to the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("Business exception: code={}, message={}", errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ErrorResponse.of(errorCode, e.getMessage()));
    }

    /** Also used by the security entry point (unauthenticated requests). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException e) {
        log.warn("Authentication required or failed: type={}", e.getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus()).body(ErrorResponse.of(ErrorCode.UNAUTHORIZED));
    }

    /** Also used by the security access denied handler; prevents method-security denials from becoming 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("Access denied: type={}", e.getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus()).body(ErrorResponse.of(ErrorCode.FORBIDDEN));
    }

    /** Bean validation on service-layer methods (@Validated). Violating values are not echoed back. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
        List<ErrorResponse.FieldErrorDetail> errors = e.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldErrorDetail(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        log.warn("Constraint violation: count={}", errors.size());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, errors));
    }

    /** Parameter/path/header validation on controller methods (@Min, @Max, @NotBlank on @RequestParam, etc.). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<ErrorResponse.FieldErrorDetail> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String paramName = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String field = (error instanceof FieldError fieldError)
                        ? fieldError.getField()
                        : (paramName != null ? paramName : "parameter");
                errors.add(new ErrorResponse.FieldErrorDetail(field, error.getDefaultMessage()));
            }
        });
        log.warn("Handler method validation failed: count={}", errors.size());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .headers(headers)
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, errors));
    }

    /** Type mismatch on query/path parameters (e.g. ?size=abc where integer is expected). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("Method argument type mismatch: parameter={}", e.getName());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, "요청 파라미터 타입이 올바르지 않습니다: " + e.getName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ErrorResponse.FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorResponse.FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        log.warn("Request validation failed: count={}", errors.size());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .headers(headers)
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, errors));
    }

    /** Single exit point for every Spring MVC exception handled by the parent class. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        ErrorCode errorCode = resolveErrorCode(statusCode);
        if (statusCode.is5xxServerError()) {
            log.error("Spring MVC exception: status={}", statusCode.value(), ex);
        } else {
            log.warn("Spring MVC exception: type={}, status={}", ex.getClass().getSimpleName(), statusCode.value());
        }
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(ErrorResponse.of(errorCode, resolveMessage(ex, errorCode)));
    }

    private ErrorCode resolveErrorCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 413 -> ErrorCode.IMAGE_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> statusCode.is5xxServerError() ? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.INVALID_REQUEST;
        };
    }

    /** Only names of missing inputs are exposed; values and internal messages never are. */
    private String resolveMessage(Exception ex, ErrorCode errorCode) {
        if (ex instanceof MissingRequestHeaderException missingHeader) {
            return "필수 헤더가 누락되었습니다: " + missingHeader.getHeaderName();
        }
        if (ex instanceof MissingServletRequestParameterException missingParameter) {
            return "필수 요청 파라미터가 누락되었습니다: " + missingParameter.getParameterName();
        }
        if (ex instanceof MissingServletRequestPartException missingPart) {
            return "필수 멀티파트 항목이 누락되었습니다: " + missingPart.getRequestPartName();
        }
        return errorCode.getMessage();
    }
}
