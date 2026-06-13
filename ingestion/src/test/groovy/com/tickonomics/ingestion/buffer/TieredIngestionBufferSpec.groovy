package com.tickonomics.ingestion.buffer

import com.tickonomics.persistence.entity.RateSnapshot
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant

class TieredIngestionBufferSpec extends Specification {

    static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z")

    @TempDir
    Path tempDir

    def "given below capacity, when add, then item stored in memory"() {
        given:
            def buffer = new TieredIngestionBuffer<RateSnapshot>(10, tempDir.resolve("rates.jsonl"), RateSnapshot)

        when:
            buffer.add(new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED", null, null))

        then:
            buffer.size() == 1
            !buffer.isOverflowing()
    }

    def "given at capacity, when add, then item overflows to file"() {
        given:
            def buffer = new TieredIngestionBuffer<RateSnapshot>(2, tempDir.resolve("rates.jsonl"), RateSnapshot)

        when:
            buffer.add(new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED", null, null))
            buffer.add(new RateSnapshot(NOW.plusMillis(1), "EFFR", 4.33, "FRED", null, null))
            buffer.add(new RateSnapshot(NOW.plusMillis(2), "TGCR", 4.30, "NY_FED", null, null))

        then:
            buffer.size() == 2
    }

    def "given overflow items, when drain, then memory drained first then file"() {
        given:
            def buffer = new TieredIngestionBuffer<RateSnapshot>(2, tempDir.resolve("rates.jsonl"), RateSnapshot)
            def rate1 = new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED", null, null)
            def rate2 = new RateSnapshot(NOW.plusMillis(1), "EFFR", 4.33, "FRED", null, null)
            def rate3 = new RateSnapshot(NOW.plusMillis(2), "TGCR", 4.30, "NY_FED", null, null)

            buffer.add(rate1)
            buffer.add(rate2)
            buffer.add(rate3)

        when:
            def drained = buffer.drain(10)

        then:
            drained.size() == 3
            drained[0].rateType() == "SOFR"
            drained[1].rateType() == "EFFR"
            drained[2].rateType() == "TGCR"
    }

    def "given overflow file exists at startup, when construct, then overflow items recovered to memory"() {
        given:
            def path = tempDir.resolve("rates.jsonl")
            def buffer1 = new TieredIngestionBuffer<RateSnapshot>(1, path, RateSnapshot)
            buffer1.add(new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED", null, null))
            buffer1.add(new RateSnapshot(NOW.plusMillis(1), "EFFR", 4.33, "FRED", null, null))
            buffer1.add(new RateSnapshot(NOW.plusMillis(2), "TGCR", 4.30, "NY_FED", null, null))

        when:
            def buffer2 = new TieredIngestionBuffer<RateSnapshot>(10, path, RateSnapshot)

        then:
            buffer2.size() == 2
    }

    def "given empty buffer, when drain, then empty list returned"() {
        given:
            def buffer = new TieredIngestionBuffer<RateSnapshot>(10, tempDir.resolve("rates.jsonl"), RateSnapshot)

        when:
            def drained = buffer.drain(10)

        then:
            drained.isEmpty()
    }

    def "given drain with limit, when items exceed limit, then only limited returned"() {
        given:
            def buffer = new TieredIngestionBuffer<RateSnapshot>(10, tempDir.resolve("rates.jsonl"), RateSnapshot)
            5.times { i ->
                buffer.add(new RateSnapshot(NOW.plusMillis(i), "SOFR", 4.29 + i * 0.01, "NY_FED", null, null))
            }

        when:
            def drained = buffer.drain(3)

        then:
            drained.size() == 3
            buffer.size() == 2
    }
}
