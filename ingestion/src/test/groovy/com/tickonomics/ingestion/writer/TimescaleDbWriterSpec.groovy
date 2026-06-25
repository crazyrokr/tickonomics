package com.tickonomics.ingestion.writer

import com.tickonomics.ingestion.tracing.IngestionMetrics
import com.tickonomics.ingestion.tracing.IngestionTracer
import com.tickonomics.persistence.entity.NewsEvent
import com.tickonomics.persistence.entity.PredictionMarketQuote
import com.tickonomics.persistence.entity.TickData
import com.tickonomics.persistence.repository.NewsEventRepository
import com.tickonomics.persistence.repository.PredictionMarketQuoteRepository
import com.tickonomics.persistence.repository.RateSnapshotRepository
import com.tickonomics.persistence.repository.TickDataRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant
import java.math.BigDecimal;

class TimescaleDbWriterSpec extends Specification {

    TickDataRepository tickRepository = Mock()
    RateSnapshotRepository rateRepository = Mock()
    PredictionMarketQuoteRepository predictionMarketRepository = Mock()
    NewsEventRepository newsRepository = Mock()
    IngestionTracer tracer = new IngestionTracer(null, false)
    IngestionMetrics metrics = new IngestionMetrics(new SimpleMeterRegistry())

    @Subject
    IdempotencyGuard guard = new IdempotencyGuard()

    @Subject
    TimescaleDbWriter writer

    def setup() {
        writer = new TimescaleDbWriter(tickRepository, rateRepository, predictionMarketRepository, newsRepository, guard, tracer, metrics, 2, 500)
    }

    private static TickData tick(String symbol, Instant time) {
        new TickData(time, symbol, BigDecimal.valueOf(100.0), 1L, new int[0])
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
            0 * predictionMarketRepository.saveAllIdempotent(_)
            0 * newsRepository.saveAllIdempotent(_)
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

    def "given batch-size prediction-market quotes, when writePredictionMarketQuote, then flushes"() {
        given:
            Instant t1 = Instant.parse("2026-06-13T10:00:00Z")
            Instant t2 = Instant.parse("2026-06-13T10:01:00Z")
            def expectedKey = TimescaleDbWriter.deterministicUuid(guard.buildKey("PREDICTION_MARKET", "m1", t1))
            List flushed = null

        when:
            writer.writePredictionMarketQuote(new PredictionMarketQuote(t1, "m1", "q?", 0.6d, 1d, 1d, "POLYMARKET"))
            writer.writePredictionMarketQuote(new PredictionMarketQuote(t2, "m2", "q?", 0.4d, 1d, 1d, "POLYMARKET"))

        then:
            1 * predictionMarketRepository.saveAllIdempotent(_) >> { args ->
                flushed = args[0]
                new int[2]
            }
            flushed.size() == 2
            flushed[0].idempotencyKey() == expectedKey
            writer.pendingPredictionMarketCount() == 0
    }

    def "given duplicate news event, when written twice, then second skipped"() {
        given:
            Instant time = Instant.parse("2026-06-13T10:00:00Z")
            def event = new NewsEvent(time, "e1", "GDELT", "headline", -3.0d, "T", "A", "u")

        when:
            writer.writeNewsEvent(event)
            writer.writeNewsEvent(event)

        then:
            0 * newsRepository.saveAllIdempotent(_)
            writer.pendingNewsCount() == 1
    }
}
