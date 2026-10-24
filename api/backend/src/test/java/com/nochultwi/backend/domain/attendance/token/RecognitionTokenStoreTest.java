package com.nochultwi.backend.domain.attendance.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecognitionTokenStoreTest {

    private Clock clock;
    private RecognitionTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-25T09:00:00Z"), ZoneId.of("Asia/Seoul"));
        tokenStore = new RecognitionTokenStore(clock);
    }

    @Test
    @DisplayName("토큰 생성 후 유효 시간(60초) 내에는 정상 조회되며 admissionYear 등 메타데이터가 보존된다")
    void createAndFindToken() {
        RecognitionTokenStore.RecognitionTokenData tokenData = tokenStore.createToken(
                1L, "2024920001", "홍길동", "인공지능학과", 2024,
                "session-01", "course-01", "kiosk-01", 60
        );

        Optional<RecognitionTokenStore.RecognitionTokenData> found = tokenStore.findToken(tokenData.token());

        assertThat(found).isPresent();
        assertThat(found.get().studentId()).isEqualTo(1L);
        assertThat(found.get().studentNumber()).isEqualTo("2024920001");
        assertThat(found.get().admissionYear()).isEqualTo(2024);
        assertThat(found.get().token()).isEqualTo(tokenData.token());
    }

    @Test
    @DisplayName("토큰 소비(tryConsume)는 오직 최초 1회만 true를 반환하고 두 번째부터는 false를 반환한다")
    void tryConsumeAtomic() {
        RecognitionTokenStore.RecognitionTokenData tokenData = tokenStore.createToken(
                1L, "2024920001", "홍길동", "인공지능학과", 2024,
                "session-01", "course-01", "kiosk-01", 60
        );

        assertThat(tokenData.tryConsume()).isTrue();
        assertThat(tokenData.tryConsume()).isFalse();
    }

    @Test
    @DisplayName("TTL 만료 토큰은 cleanupExpiredTokens 호출 시 메모리에서 즉시 일괄 회수된다")
    void cleanupExpiredTokens() {
        // 이미 만료된 과거 토큰 생성 (ttl = -1초)
        tokenStore.createToken(1L, "2024920001", "만료학생", "컴퓨터과학부", 2024,
                "session-01", "course-01", "kiosk-01", -1);

        // 정상 유효 토큰 생성 (ttl = 60초)
        tokenStore.createToken(2L, "2025920002", "유효학생", "전자전기컴퓨터공학부", 2025,
                "session-01", "course-01", "kiosk-01", 60);

        int removedCount = tokenStore.cleanupExpiredTokens();
        assertThat(removedCount).isGreaterThanOrEqualTo(1);
    }
}
