package com.tickonomics.ingestion.equity

import com.tickonomics.cdm.adapter.FinnhubEquityCdmAdapter
import com.tickonomics.cdm.adapter.YahooEquityCdmAdapter
import com.tickonomics.cdm.adapter.raw.YahooOhlcv
import com.tickonomics.ingestion.writer.TimescaleDbWriter
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class EquityPriceSchedulerSpec extends Specification {

  TimescaleDbWriter writer = Mock()
  YahooEquityCdmAdapter yahooAdapter = new YahooEquityCdmAdapter()
  FinnhubEquityCdmAdapter finnhubAdapter = new FinnhubEquityCdmAdapter()
  List<String> symbols = ["SPY"]

  @Subject
  EquityPriceScheduler scheduler

  def setup() {
    scheduler = new EquityPriceScheduler([], writer, yahooAdapter, finnhubAdapter, symbols)
  }

  def "given Yahoo returns data, when pollEquityPrices, then write ticks via writer"() {
    given:
      def yahooBars = [
          new YahooOhlcv(Instant.now(), "SPY", 500.0, 505.0, 498.0, 503.0, 100000L)
      ]
      def yahooClient = Mock(EquityPriceClient) {
        sourceName() >> "YAHOO_FINANCE"
        fetchHistoricalOhlcv("SPY", "1d") >> yahooBars
      }
      scheduler = new EquityPriceScheduler([yahooClient], writer, yahooAdapter, finnhubAdapter, symbols)

    when:
      scheduler.pollEquityPrices()

    then:
      1 * writer.writeTick(_)
  }

  def "given Yahoo fails and Finnhub succeeds, when pollEquityPrices, then use Finnhub data"() {
    given:
      def yahooClient = Mock(EquityPriceClient) {
        sourceName() >> "YAHOO_FINANCE"
        fetchHistoricalOhlcv("SPY", "1d") >> { throw new RuntimeException("Yahoo down") }
      }
      def finnhubBars = [
          new YahooOhlcv(Instant.now(), "SPY", 501.0, 506.0, 499.0, 504.0, 95000L)
      ]
      def finnhubClient = Mock(EquityPriceClient) {
        sourceName() >> "FINNHUB"
        fetchHistoricalOhlcv("SPY", "1d") >> finnhubBars
      }
      scheduler = new EquityPriceScheduler([yahooClient, finnhubClient], writer, yahooAdapter, finnhubAdapter, symbols)

    when:
      scheduler.pollEquityPrices()

    then:
      1 * writer.writeTick(_)
  }

  def "given all clients fail, when pollEquityPrices, then no ticks written"() {
    given:
      def yahooClient = Mock(EquityPriceClient) {
        sourceName() >> "YAHOO_FINANCE"
        fetchHistoricalOhlcv("SPY", "1d") >> { throw new RuntimeException("Yahoo down") }
      }
      def finnhubClient = Mock(EquityPriceClient) {
        sourceName() >> "FINNHUB"
        fetchHistoricalOhlcv("SPY", "1d") >> { throw new RuntimeException("Finnhub down") }
      }
      scheduler = new EquityPriceScheduler([yahooClient, finnhubClient], writer, yahooAdapter, finnhubAdapter, symbols)

    when:
      scheduler.pollEquityPrices()

    then:
      0 * writer.writeTick(_)
  }

  def "given Yahoo returns empty, when pollEquityPrices, then try next client"() {
    given:
      def yahooClient = Mock(EquityPriceClient) {
        sourceName() >> "YAHOO_FINANCE"
        fetchHistoricalOhlcv("SPY", "1d") >> []
      }
      def finnhubBars = [
          new YahooOhlcv(Instant.now(), "SPY", 501.0, 506.0, 499.0, 504.0, 95000L)
      ]
      def finnhubClient = Mock(EquityPriceClient) {
        sourceName() >> "FINNHUB"
        fetchHistoricalOhlcv("SPY", "1d") >> finnhubBars
      }
      scheduler = new EquityPriceScheduler([yahooClient, finnhubClient], writer, yahooAdapter, finnhubAdapter, symbols)

    when:
      scheduler.pollEquityPrices()

    then:
      1 * writer.writeTick(_)
  }

  def "given multiple bars, when pollEquityPrices, then write all ticks"() {
    given:
      def yahooBars = [
          new YahooOhlcv(Instant.now(), "SPY", 500.0, 505.0, 498.0, 503.0, 100000L),
          new YahooOhlcv(Instant.now().minusSeconds(86400), "SPY", 498.0, 503.0, 496.0, 500.0, 95000L)
      ]
      def yahooClient = Mock(EquityPriceClient) {
        sourceName() >> "YAHOO_FINANCE"
        fetchHistoricalOhlcv("SPY", "1d") >> yahooBars
      }
      scheduler = new EquityPriceScheduler([yahooClient], writer, yahooAdapter, finnhubAdapter, symbols)

    when:
      scheduler.pollEquityPrices()

    then:
      2 * writer.writeTick(_)
  }
}
