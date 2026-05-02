package com.tickonomics.ingestion.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);
    private static final long DEFAULT_TTL_MS = 86_400_000L;

    private final ConcurrentHashMap<String, Long> seenKeys = new ConcurrentHashMap<>();
    private final long ttlMs;

    public IdempotencyGuard() {
        this(DEFAULT_TTL_MS);
    }

    public IdempotencyGuard(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public boolean isDuplicate(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        Long existing = seenKeys.putIfAbsent(key, System.currentTimeMillis());
        return existing != null;
    }

    public int evictExpired() {
        long cutoff = System.currentTimeMillis() - ttlMs;
        int evicted = 0;
        Iterator<Map.Entry<String, Long>> it = seenKeys.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < cutoff) {
                it.remove();
                evicted++;
            }
        }
        if (evicted > 0) {
            log.debug("Evicted {} expired idempotency keys", evicted);
        }
        return evicted;
    }

    public int size() {
        return seenKeys.size();
    }

    public String buildKey(String source, String identifier, Instant time) {
        return source + ":" + identifier + ":" + time.toString();
    }
}
