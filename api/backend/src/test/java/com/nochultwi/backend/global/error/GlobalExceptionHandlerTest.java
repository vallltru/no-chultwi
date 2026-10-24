package com.nochultwi.backend.global.error;

import com.nochultwi.backend.global.filter.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("BusinessException은 ErrorCode의 HTTP 상태와 명세 형식(code, message, requestId)으로 변환된다")
    void handleBusinessException() {
        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleBusinessException(new BusinessException(ErrorCode.RECOGNITION_EXPIRED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RECOGNITION_EXPIRED");
        assertThat(response.getBody().message()).isEqualTo("얼굴을 다시 인식해 주세요.");
        assertThat(response.getBody().requestId()).isNotBlank();
        assertThat(response.getBody().errors()).isNull();
    }

    @Test
    @DisplayName("응답의 requestId는 요청 필터가 MDC에 넣은 값과 같다")
    void requestIdComesFromMdc() {
        MDC.put(RequestIdFilter.MDC_KEY, "test-request-id-0001");

        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleBusinessException(new BusinessException(ErrorCode.SESSION_CLOSED));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().requestId()).isEqualTo("test-request-id-0001");
    }

    @Test
    @DisplayName("인증 예외는 401 UNAUTHORIZED, 인가 예외는 403 FORBIDDEN으로 변환된다 (500 아님)")
    void handleSecurityExceptions() {
        ResponseEntity<ErrorResponse> unauthorized =
                exceptionHandler.handleAuthenticationException(new InsufficientAuthenticationException("no token"));
        ResponseEntity<ErrorResponse> forbidden =
                exceptionHandler.handleAccessDeniedException(new AccessDeniedException("denied"));

        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.getBody()).isNotNull();
        assertThat(unauthorized.getBody().code()).isEqualTo("UNAUTHORIZED");
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody()).isNotNull();
        assertThat(forbidden.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("예상하지 못한 예외의 내부 메시지는 클라이언트 응답에 노출되지 않는다")
    void unexpectedExceptionDoesNotLeakInternalMessage() {
        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleUnexpectedException(new IllegalStateException("jdbc password=secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }
}
