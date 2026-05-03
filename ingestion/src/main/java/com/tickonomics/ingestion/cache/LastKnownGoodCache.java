package com.tickonomics.ingestion.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LastKnownGoodCache {

    private static final Logger log = LoggerFactory.getLogger(LastKnownGoodCache.class);

    private final ConcurrentHashMap<String, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Duration maxStaleness;

    public LastKnownGoodCache(
            @Value("${monitor.ingestion.lkg-cache.max-staleness-seconds:3600}") long maxStalenessSeconds) {
        this.maxStaleness = Duration.ofSeconds(maxStalenessSeconds);
    }

    public void put(String source, Object value) {
        CacheEntry entry = new CacheEntry(source, value, Instant.now(), CacheEntry.FRESH);
        entries.put(source, entry);
        log.debug("Cached last known good value for source={}", source);
    }

    public Optional<CacheEntry> get(String source) {
        CacheEntry entry = entries.get(source);
        if (entry == null) {
            return Optional.empty();
        }

        Duration age = Duration.between(entry.cachedAt(), Instant.now());
        if (age.compareTo(maxStaleness) > 0) {
            entries.remove(source, entry);
            log.debug("Evicted expired LKG cache entry for source={}, age={}", source, age);
            return Optional.empty();
        }

        String status = age.toSeconds() < maxStaleness.toSeconds() / 2
                ? CacheEntry.FRESH
                : CacheEntry.STALE;

        CacheEntry updated = new CacheEntry(source, entry.value(), entry.cachedAt(), status);
        return Optional.of(updated);
    }

    public boolean isExpired(String source) {
        CacheEntry entry = entries.get(source);
        if (entry == null) {
            return true;
        }
        Duration age = Duration.between(entry.cachedAt(), Instant.now());
        return age.compareTo(maxStaleness) > 0;
    }

    public void evict(String source) {
        entries.remove(source);
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
