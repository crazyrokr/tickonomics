package com.tickonomics.ingestion.polymarket

import com.tickonomics.cdm.adapter.PolymarketCdmAdapter
import com.tickonomics.ingestion.tracing.IngestionTracer
import com.tickonomics.ingestion.writer.TimescaleDbWriter
import com.tickonomics.persistence.entity.PredictionMarketQuote
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Instant

class PolymarketClientSpec extends Specification {

    RestClient.Builder builder = Mock()
    TimescaleDbWriter writer = Mock()
    IngestionTracer tracer = new IngestionTracer(null, false)
    PolymarketCdmAdapter adapter = new PolymarketCdmAdapter()

    def "given outcomePrices array string, when toQuote, then first price parsed as yes probability"() {
        given:
            def raw = new PolymarketClient.PolymarketMarketRaw(
                "m1", "slug", "Will AAPL beat earnings?", '["0.72","0.28"]', "12000", "50000", true, false)

        when:
            def quote = PolymarketClient.toQuote(raw, Instant.parse("2026-06-24T12:00:00Z"))

        then:
            quote.outcomeYesPrice() == 0.72d
            quote.marketId() == "m1"
            quote.question() == "Will AAPL beat earnings?"
            quote.volume() == 12000d
            quote.liquidity() == 50000d
            quote.source() == "POLYMARKET"
    }

    def "given missing or unparseable outcomePrices, when toQuote, then null (false-positive guard)"() {
        given:
            def raw = new PolymarketClient.PolymarketMarketRaw("m1", "slug", "q?", prices, null, null, true, false)

        expect:
            PolymarketClient.toQuote(raw, Instant.parse("2026-06-24T12:00:00Z")) == null

        where:
            prices << [null, "", "[]", "not-a-price"]
    }

    def "given null market, when toQuote, then null"() {
        expect:
            PolymarketClient.toQuote(null, Instant.parse("2026-06-24T12:00:00Z")) == null
    }

    def "given blank volume and liquidity, when toQuote, then defaulted to zero"() {
        given:
            def raw = new PolymarketClient.PolymarketMarketRaw("m1", "slug", "q?", '["0.5"]', null, "", true, false)

        when:
            def quote = PolymarketClient.toQuote(raw, Instant.parse("2026-06-24T12:00:00Z"))

        then:
            quote.volume() == 0.0d
            quote.liquidity() == 0.0d
    }

    def "given mixed valid and invalid markets, when pollMarkets, then only valid ones written"() {
        given:
            def client = Spy(PolymarketClient, constructorArgs: [builder, writer, adapter, tracer])
            client.fetchMarkets() >> [
                new PolymarketClient.PolymarketMarketRaw("m1", "s", "q1?", '["0.7"]', "1", "1", true, false),
                new PolymarketClient.PolymarketMarketRaw("m2", "s", "q2?", "", "1", "1", true, false)
            ]

        when:
            client.pollMarkets()

        then:
            1 * writer.writePredictionMarketQuote({ PredictionMarketQuote q -> q.marketId() == "m1" })
    }
}
