package com.tickonomics.web.perspex

import com.tickonomics.computation.audit.CodingRule
import com.tickonomics.computation.audit.IntersubjectiveAuditService
import com.tickonomics.contracts.client.AnalyticsWorkerClient
import com.tickonomics.persistence.entity.NewsEvent
import com.tickonomics.persistence.entity.PredictionMarketQuote
import com.tickonomics.persistence.repository.NewsEventRepository
import com.tickonomics.persistence.repository.PredictionMarketQuoteRepository
import com.tickonomics.web.realtime.SignalNotification
import com.tickonomics.web.realtime.SignalWebSocketHandler
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class PerspexSignalGeneratorSpec extends Specification {

    PredictionMarketQuoteRepository quoteRepository = Mock()
    NewsEventRepository newsRepository = Mock()
    AnalyticsWorkerClient workerClient = Mock()
    IntersubjectiveAuditService auditService = Mock()
    SignalWebSocketHandler signalHandler = Mock()

    @Subject
    PerspexSignalGenerator generator

    def setup() {
        generator = new PerspexSignalGenerator(quoteRepository, newsRepository, workerClient, auditService, signalHandler)
    }

    static Map mismatch(String dir = "bearish", tickers = ["AAPL"]) {
        return [
            market_id     : "m1",
            question      : "q",
            tickers       : tickers,
            market_probability: 0.85,
            news_probability : 0.05,
            divergence    : 0.8,
            direction     : dir,
            confidence    : 1.0,
            supporting_event_ids: ["e1"]
        ]
    }

    def "given actionable mismatch, when emitSignal, then broadcast per ticker through the deterministic rule"() {
        given:
            auditService.isActionable(_) >> true

        when:
            def emitted = generator.emitSignal(mismatch("bearish"))

        then:
            emitted
            1 * auditService.logTransformation(_, CodingRule.PERSPECTIVE_MISMATCH_DETERMINISTIC, "m1:AAPL", _)
            1 * signalHandler.broadcast({ SignalNotification s ->
                s.symbol() == "AAPL" && s.direction() == "SHORT" && s.strategyId() != null
            })
    }

    def "given bullish mismatch, when emitSignal, then direction mapped to BULLISH"() {
        given:
            auditService.isActionable(_) >> true

        when:
            generator.emitSignal(mismatch("bullish"))

        then:
            1 * signalHandler.broadcast({ SignalNotification s -> s.direction() == "LONG" })
    }

    def "given trust gate rejects, when emitSignal, then no broadcast (false-positive guard)"() {
        given:
            auditService.isActionable(_) >> false

        when:
            def emitted = generator.emitSignal(mismatch())

        then:
            !emitted
            1 * auditService.logTransformation(_, _, _, _)
            0 * signalHandler.broadcast(_)
    }

    def "given mismatch with no tickers, when emitSignal, then nothing logged or broadcast"() {
        when:
            def emitted = generator.emitSignal(mismatch("bearish", []))

        then:
            !emitted
            0 * auditService.logTransformation(_, _, _, _)
            0 * signalHandler.broadcast(_)
    }

    def "given worker returns a mismatch, when generate, then a signal is broadcast"() {
        given:
            quoteRepository.findRecent(_, _) >> [
                new PredictionMarketQuote(Instant.parse("2026-06-24T12:00:00Z"), "m1", 'Will $AAPL beat earnings?', 0.85, 10000.0, 50000.0, "POLYMARKET")]
            newsRepository.findRecent(_, _) >> [
                new NewsEvent(Instant.parse("2026-06-24T12:00:00Z"), "e1", "GDELT", "Apple earnings weak", -9.0, null, "US", "u")]
            workerClient.sendAnalysisRequest(_, _) >> [mismatches: [mismatch()], evaluated_markets: 1]
            auditService.isActionable(_) >> true

        when:
            generator.generate()

        then:
            1 * signalHandler.broadcast(_ as SignalNotification)
    }

    def "given worker returns error, when generate, then no broadcast"() {
        given:
            quoteRepository.findRecent(_, _) >> [
                new PredictionMarketQuote(Instant.parse("2026-06-24T12:00:00Z"), "m1", "q", 0.5, 1.0, 1.0, "S")]
            newsRepository.findRecent(_, _) >> [
                new NewsEvent(Instant.parse("2026-06-24T12:00:00Z"), "e1", "GDELT", "h", 0.0, null, null, "u")]
            workerClient.sendAnalysisRequest(_, _) >> [error: "worker down"]

        when:
            generator.generate()

        then:
            0 * signalHandler.broadcast(_)
    }

    def "given no quotes, when generate, then worker not called"() {
        given:
            quoteRepository.findRecent(_, _) >> []
            newsRepository.findRecent(_, _) >> [
                new NewsEvent(Instant.parse("2026-06-24T12:00:00Z"), "e1", "GDELT", "h", 0.0, null, null, "u")]

        when:
            generator.generate()

        then:
            0 * workerClient.sendAnalysisRequest(_, _)
    }
}
