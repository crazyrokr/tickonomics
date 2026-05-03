package com.tickonomics.ingestion.cache

import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class LastKnownGoodCacheSpec extends Specification {

    @Subject
    LastKnownGoodCache cache = new LastKnownGoodCache(2)

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
}
