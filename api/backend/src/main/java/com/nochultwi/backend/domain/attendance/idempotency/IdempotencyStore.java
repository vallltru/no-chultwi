package com.nochultwi.backend.domain.attendance.idempotency;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키오스크 단말기의 네트워크 불안정 및 재시도로 인한 중복 출석을 방지하기 위한
 * Idempotency-Key 기반의 멱등성 응답 캐시 저장소 (Spec 6.4).
 */
@Component
public class IdempotencyStore {

    private final Map<String, IdempotencyEntry> cache = new ConcurrentHashMap<>();
    private final Clock clock;

    public IdempotencyStore(Clock clock) {
        this.clock = clock;
    }

    public record IdempotencyEntry(
            String idempotencyKey,
            String requestPayloadHash,
            int httpStatus,
            Object responseBody,
            Instant expiresAt
    ) {
        public boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    public Optional<IdempotencyEntry> get(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        IdempotencyEntry entry = cache.get(idempotencyKey);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(clock.instant())) {
            cache.remove(idempotencyKey);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public void save(String idempotencyKey, String requestPayloadHash, int httpStatus, Object responseBody, long ttlSeconds) {
        Instant expiresAt = clock.instant().plusSeconds(ttlSeconds);
        cache.put(idempotencyKey, new IdempotencyEntry(idempotencyKey, requestPayloadHash, httpStatus, responseBody, expiresAt));
    }

    public void clear() {
        cache.clear();
    }
}
