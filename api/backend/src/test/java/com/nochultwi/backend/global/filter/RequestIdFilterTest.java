package com.nochultwi.backend.global.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    @Test
    @DisplayName("안전한 형식의 클라이언트 요청 ID는 그대로 사용한다")
    void acceptsSafeRequestId() {
        assertThat(RequestIdFilter.resolveRequestId("2c265719-d4c5-4aba-a69d-33844792f0b8"))
                .isEqualTo("2c265719-d4c5-4aba-a69d-33844792f0b8");
    }

    @Test
    @DisplayName("개행/특수문자/과도한 길이의 요청 ID는 로그 인젝션 방지를 위해 새 UUID로 대체한다")
    void replacesUnsafeRequestId() {
        assertThat(RequestIdFilter.resolveRequestId("abc12345\r\nFAKE LOG LINE")).doesNotContain("FAKE");
        assertThat(RequestIdFilter.resolveRequestId("a".repeat(65))).hasSize(36);
        assertThat(RequestIdFilter.resolveRequestId("short")).hasSize(36);
        assertThat(RequestIdFilter.resolveRequestId(null)).hasSize(36);
    }
}
