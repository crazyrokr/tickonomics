package com.tickonomics.ingestion.cache

import spock.lang.Specification
import spock.lang.Subject

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LastKnownGoodCacheSpec extends Specification {

    @Subject
    LastKnownGoodCache cache = new LastKnownGoodCache(2)

    static final Instant T0 = Instant.parse("2026-06-13T10:00:00Z")

    private LastKnownGoodCache cacheFixedAt(Instant instant, long maxStalenessSeconds) {
        LastKnownGoodCache fixedClockCache = new LastKnownGoodCache(maxStalenessSeconds)
        fixedClockCache.setClock(Clock.fixed(instant, ZoneOffset.UTC))
        fixedClockCache
    }

    def "given no entry, when get, then empty returned"() {
        expect:
            cache.get("FRED").isEmpty()
    }

    def "given fresh entry, when get, then entry returned with FRESH status"() {
        given:
            cache.put("FRED", "rate-data")

        when:
            def result = cache.get("FRED")

        then:
            result.isPresent()
            result.get().source() == "FRED"
            result.get().value() == "rate-data"
            result.get().stalenessStatus() == CacheEntry.FRESH
    }

    def "given source already cached, when put, then value updated"() {
        given:
            cache.put("FRED", "old-data")

        when:
            cache.put("FRED", "new-data")

        then:
            def result = cache.get("FRED")
            result.isPresent()
            result.get().value() == "new-data"
    }

    def "given isExpired called on missing source, when checked, then true"() {
        expect:
            cache.isExpired("MISSING")
    }

    def "given cached entry, when evict, then removed"() {
        given:
            cache.put("FRED", "data")

        when:
            cache.evict("FRED")

        then:
            cache.get("FRED").isEmpty()
    }

    def "given multiple sources, when size, then correct count"() {
        when:
            cache.put("FRED", "data1")
            cache.put("NYFED", "data2")

        then:
            cache.size() == 2
    }

    def "given entries, when clear, then all removed"() {
        given:
            cache.put("FRED", "data1")
            cache.put("NYFED", "data2")

        when:
            cache.clear()

        then:
            cache.size() == 0
    }

    def "given null source, when CacheEntry constructed, then exception"() {
        when:
            new CacheEntry(null, "value", Instant.now(), "FRESH")

        then:
            thrown(IllegalArgumentException)
    }

    def "given null value, when CacheEntry constructed, then exception"() {
        when:
            new CacheEntry("FRED", null, Instant.now(), "FRESH")

        then:
            thrown(IllegalArgumentException)
    }

    def "given fresh entry, when headerValue, then fresh header returned"() {
        given:
            def fixedClockCache = cacheFixedAt(T0, 10)
            fixedClockCache.put("FRED", 5.25)

        expect:
            fixedClockCache.headerValue("FRED").get() == CacheEntry.FRESH
    }

    def "given absent source, when headerValue, then empty and expired"() {
        given:
            def fixedClockCache = cacheFixedAt(T0, 10)

        expect:
            fixedClockCache.get("NOPE").isEmpty()
            fixedClockCache.headerValue("NOPE").isEmpty()
            fixedClockCache.isExpired("NOPE")
    }

    def "given entry aged beyond half staleness, when get, then stale status and stale header"() {
        given: "6s old with a 10s max staleness (half-life 5s)"
            def fixedClockCache = cacheFixedAt(T0, 10)
            fixedClockCache.put("FRED", 5.25)
            fixedClockCache.setClock(Clock.fixed(T0.plusSeconds(6), ZoneOffset.UTC))

        expect:
            fixedClockCache.get("FRED").get().stalenessStatus() == CacheEntry.STALE
            fixedClockCache.headerValue("FRED").get() == CacheEntry.STALE
    }

    def "given entry aged beyond max staleness, when get, then evicted and expired"() {
        given: "11s old with a 10s max staleness"
            def fixedClockCache = cacheFixedAt(T0, 10)
            fixedClockCache.put("FRED", 5.25)
            fixedClockCache.setClock(Clock.fixed(T0.plusSeconds(11), ZoneOffset.UTC))

        expect:
            fixedClockCache.get("FRED").isEmpty()
            fixedClockCache.headerValue("FRED").isEmpty()
            fixedClockCache.isExpired("FRED")
            fixedClockCache.size() == 0
    }

    def "given header constant, then equals X-Data-Age"() {
        expect:
            CacheEntry.X_DATA_AGE_HEADER == "X-Data-Age"
    }
}
