package com.tickonomics.cdm.adapter

import com.tickonomics.cdm.adapter.raw.OsintEvent
import com.tickonomics.cdm.adapter.raw.PolymarketQuote
import com.tickonomics.cdm.model.CdmNewsEvent
import com.tickonomics.cdm.model.CdmPredictionMarketQuote
import spock.lang.Specification

import java.time.Instant

class PerspexCdmAdapterSpec extends Specification {

  private static final Instant NOW = Instant.parse("2026-06-24T12:00:00Z")

  def "PolymarketCdmAdapter maps quote and drops provider slug"() {
    given:
        def adapter = new PolymarketCdmAdapter()
        def raw = new PolymarketQuote(NOW, "0xabc", "will-aapl-beat-earnings",
            "Will AAPL beat Q3 earnings?", 0.72d, 12000d, 50000d, "POLYMARKET")

    when:
        def result = adapter.toCdm(raw)

    then:
        result.getClass() == CdmPredictionMarketQuote
        result.time() == NOW
        result.marketId() == "0xabc"
        result.question() == "Will AAPL beat Q3 earnings?"
        result.outcomeYesPrice() == 0.72d
        result.volume() == 12000d
        result.liquidity() == 50000d
        result.source() == "POLYMARKET"
  }

  def "OsintCdmAdapter preserves GDELT tone signal"() {
    given:
        def adapter = new OsintCdmAdapter()
        def raw = new OsintEvent(NOW, "12345", "GDELT",
            "Apple suppliers signal weak demand", -6.4d, "ECON;TECH", "USACORP", "https://example.org")

    when:
        def result = adapter.toCdm(raw)

    then:
        result.getClass() == CdmNewsEvent
        result.eventId() == "12345"
        result.source() == "GDELT"
        result.headline() == "Apple suppliers signal weak demand"
        result.avgTone() == -6.4d
        result.themes() == "ECON;TECH"
  }

  def "PolymarketQuote rejects out-of-range price (false-positive guard)"() {
    when:
        new PolymarketQuote(NOW, "m1", "slug", "q?", price, 1d, 1d, "POLYMARKET")

    then:
        thrown(IllegalArgumentException)

    where:
        price << [-0.01d, 1.01d, Double.NaN, Double.POSITIVE_INFINITY]
  }

  def "PolymarketQuote rejects null required fields and negative volume/liquidity"() {
    when:
        new PolymarketQuote(time, marketId, "slug", "q?", 0.5d, volume, liquidity, "POLYMARKET")

    then:
        def ex = thrown(expected)
        ex != null

    where:
        time | marketId | volume | liquidity | expected
        null | "m1"      | 1d     | 1d        | NullPointerException
        NOW  | null      | 1d     | 1d        | NullPointerException
        NOW  | "m1"      | -1d    | 1d        | IllegalArgumentException
        NOW  | "m1"      | 1d     | -1d       | IllegalArgumentException
  }

  def "OsintEvent rejects non-finite tone and null required fields"() {
    when:
        new OsintEvent(time, eventId, "GDELT", "headline", tone, "T", "A", "url")

    then:
        def ex = thrown(expected)
        ex != null

    where:
        time | eventId | tone                  | expected
        null | "1"     | 0d                    | NullPointerException
        NOW  | null    | 0d                    | NullPointerException
        NOW  | "1"     | Double.NaN            | IllegalArgumentException
        NOW  | "1"     | Double.POSITIVE_INFINITY | IllegalArgumentException
  }

  def "CdmPredictionMarketQuote and CdmNewsEvent reject invalid construction"() {
    when:
        construct()

    then:
        def ex = thrown(expected)
        ex != null

    where:
        construct << [
            { -> new CdmPredictionMarketQuote(NOW, "m1", "q?", 1.5d, 1d, 1d, "S") },
            { -> new CdmPredictionMarketQuote(NOW, null, "q?", 0.5d, 1d, 1d, "S") },
            { -> new CdmNewsEvent(NOW, "1", "GDELT", "h", Double.NaN, "T", "A", "u") },
            { -> new CdmNewsEvent(NOW, "1", "GDELT", null, 0d, "T", "A", "u") }
        ]
        expected << [
            IllegalArgumentException,
            NullPointerException,
            IllegalArgumentException,
            NullPointerException
        ]
  }
}
