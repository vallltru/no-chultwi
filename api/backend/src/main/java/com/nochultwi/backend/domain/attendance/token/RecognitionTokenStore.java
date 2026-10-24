package com.nochultwi.backend.domain.attendance.token;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 키오스크 단말기 얼굴 인식 후 전자출결 확정 전까지 60초간 유효한
 * 1회용 임시 인증 토큰(RecognitionToken)을 관리하는 인메모리 저장소.
 */
@Component
public class RecognitionTokenStore {

    private final Map<String, RecognitionTokenData> store = new ConcurrentHashMap<>();
    private final Clock clock;

    public RecognitionTokenStore(Clock clock) {
        this.clock = clock;
    }

    public record RecognitionTokenData(
            String token,
            Long studentId,
            String studentNumber,
            String studentName,
            String department,
            int admissionYear,
            String sessionId,
            String courseId,
            String deviceId,
            Instant expiresAt,
            AtomicBoolean consumed
    ) {
        public boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }

        public boolean tryConsume() {
            return consumed.compareAndSet(false, true);
        }
    }

    public RecognitionTokenData createToken(
            Long studentId,
            String studentNumber,
            String studentName,
            String department,
            int admissionYear,
            String sessionId,
            String courseId,
            String deviceId,
            long ttlSeconds) {

        String token = UUID.randomUUID().toString();
        Instant expiresAt = clock.instant().plusSeconds(ttlSeconds);
        RecognitionTokenData data = new RecognitionTokenData(
                token,
                studentId,
                studentNumber,
                studentName,
                department,
                admissionYear,
                sessionId,
                courseId,
                deviceId,
                expiresAt,
                new AtomicBoolean(false)
        );
        // 메모리 누수 방어: 토큰이 50개 이상 누적 시 만료된 토큰 능동적 회수
        if (store.size() > 50) {
            cleanupExpiredTokens();
        }

        store.put(token, data);
        return data;
    }

    /**
     * 만료 시각이 지난 토큰들을 인메모리 맵에서 일괄 제거합니다.
     */
    public int cleanupExpiredTokens() {
        Instant now = clock.instant();
        int initialSize = store.size();
        store.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        return initialSize - store.size();
    }

    public Optional<RecognitionTokenData> findToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        RecognitionTokenData data = store.get(token);
        if (data == null) {
            return Optional.empty();
        }
        if (data.isExpired(clock.instant())) {
            store.remove(token);
            return Optional.empty();
        }
        return Optional.of(data);
    }

    public void removeToken(String token) {
        if (token != null) {
            store.remove(token);
        }
    }

    public void clear() {
        store.clear();
    }
}
