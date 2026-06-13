package com.tickonomics.ingestion.writer

import com.tickonomics.ingestion.tracing.IngestionTracer
import com.tickonomics.persistence.entity.TickData
import com.tickonomics.persistence.repository.RateSnapshotRepository
import com.tickonomics.persistence.repository.TickDataRepository
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class TimescaleDbWriterSpec extends Specification {

    TickDataRepository tickRepository = Mock()
    RateSnapshotRepository rateRepository = Mock()
    IngestionTracer tracer = new IngestionTracer(null, false)

    @Subject
    IdempotencyGuard guard = new IdempotencyGuard()

    @Subject
    TimescaleDbWriter writer

    def setup() {
        writer = new TimescaleDbWriter(tickRepository, rateRepository, guard, tracer)
        writer.batchSize = 2
    }

    private static TickData tick(String symbol, Instant time) {
        new TickData(time, symbol, 100.0, 1L, new int[0])
    }

    def "given tick below batch size, when writeTick, then buffered and not flushed"() {
        when:
            writer.writeTick(tick("SPY", Instant.parse("2026-06-13T10:00:00Z")))

        then:
            0 * tickRepository.saveAllIdempotent(_)
            writer.pendingTickCount() == 1
    }

    def "given batch-size ticks, when writeTick, then flushes idempotent rows with deterministic keys"() {
        given:
            Instant t1 = Instant.parse("2026-06-13T10:00:00Z")
            Instant t2 = Instant.parse("2026-06-13T10:01:00Z")
            def expectedKey = TimescaleDbWriter.deterministicUuid(guard.buildKey("TICK", "SPY", t1))
            List flushed = null

        when:
            writer.writeTick(tick("SPY", t1))
            writer.writeTick(tick("QQQ", t2))

        then:
            1 * tickRepository.saveAllIdempotent(_) >> { args ->
                flushed = args[0]
                new int[2]
            }
            flushed.size() == 2
            flushed[0].idempotencyKey() == expectedKey
            flushed[1].row().symbol() == "QQQ"
            writer.pendingTickCount() == 0
    }

    def "given duplicate tick, when written twice, then second skipped"() {
        given:
            Instant time = Instant.parse("2026-06-13T10:00:00Z")

        when:
            writer.writeTick(tick("SPY", time))
            writer.writeTick(tick("SPY", time))

        then:
            0 * tickRepository.saveAllIdempotent(_)
            writer.pendingTickCount() == 1
    }

    def "given empty buffers, when flushAll, then no repository call"() {
        when:
            writer.flushAll()

        then:
            0 * tickRepository.saveAllIdempotent(_)
            0 * rateRepository.saveAllIdempotent(_)
    }

    def "given repo failure, when flush ticks, then batch requeued for retry"() {
        given: "the first flush attempt fails"
            Instant t1 = Instant.parse("2026-06-13T10:00:00Z")
            Instant t2 = Instant.parse("2026-06-13T10:01:00Z")

        when: "two writes trigger a flush"
            writer.writeTick(tick("SPY", t1))
            writer.writeTick(tick("SPY", t2))

        then: "the batch is restored to the buffer for the next flush attempt"
            1 * tickRepository.saveAllIdempotent(_) >> { throw new RuntimeException("connection reset") }
            writer.pendingTickCount() == 2
    }

    def "given same key, when deterministicUuid, then identical uuid"() {
        given:
            String key = guard.buildKey("TICK", "SPY", Instant.parse("2026-06-13T10:00:00Z"))

        expect: "a retried write of the same event yields the same key for ON CONFLICT dedup"
            TimescaleDbWriter.deterministicUuid(key) == TimescaleDbWriter.deterministicUuid(key)
    }

    def "given different keys, when deterministicUuid, then distinct uuid"() {
        given:
            String a = guard.buildKey("TICK", "SPY", Instant.parse("2026-06-13T10:00:00Z"))
            String b = guard.buildKey("TICK", "QQQ", Instant.parse("2026-06-13T10:00:00Z"))

        expect:
            TimescaleDbWriter.deterministicUuid(a) != TimescaleDbWriter.deterministicUuid(b)
    }
}
