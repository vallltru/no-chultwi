package com.nochultwi.backend.domain.attendance.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyStoreTest {

    private Clock clock;
    private IdempotencyStore idempotencyStore;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-25T09:00:00Z"), ZoneId.of("Asia/Seoul"));
        idempotencyStore = new IdempotencyStore(clock);
    }

    @Test
    @DisplayName("멱등성 캐시 저장 후 TTL 내에는 동일한 응답 본문과 상태 코드가 정상 조회된다")
    void saveAndGet() {
        String key = "test-idempotency-key";
        idempotencyStore.save(key, "payload-hash", 201, "RESPONSE_BODY", 86400);

        Optional<IdempotencyStore.IdempotencyEntry> found = idempotencyStore.get(key);

        assertThat(found).isPresent();
        assertThat(found.get().idempotencyKey()).isEqualTo(key);
        assertThat(found.get().httpStatus()).isEqualTo(201);
        assertThat(found.get().responseBody()).isEqualTo("RESPONSE_BODY");
    }

    @Test
    @DisplayName("만료된 멱등성 엔트리는 cleanupExpiredEntries 호출 시 일괄 회수된다")
    void cleanupExpiredEntries() {
        // 이미 만료된 과거 키 생성 (ttl = -1초)
        idempotencyStore.save("expired-key", "hash", 200, "OLD", -1);

        // 정상 유효 키 (ttl = 86400초)
        idempotencyStore.save("valid-key", "hash", 200, "VALID", 86400);

        int removed = idempotencyStore.cleanupExpiredEntries();
        assertThat(removed).isGreaterThanOrEqualTo(1);
    }
}
