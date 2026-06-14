package com.tickonomics.ingestion.config

import spock.lang.Specification

import java.time.Duration

class MonitorPropertiesSpec extends Specification {

  def "valid equity-price properties construct without error"() {
    when: "a fully valid equity-price binding is constructed"
    def props = new EquityPriceProperties(true, 21600000L, ["SPY", "QQQ", "IWM"])

    then: "no validation fires"
    noExceptionThrown()
    props.symbols == ["SPY", "QQQ", "IWM"]
  }

  def "non-positive poll interval is rejected"() {
    when: "an equity-price binding with a zero poll interval is constructed"
    new EquityPriceProperties(true, 0L, ["SPY"])

    then: "construction fails referencing the offending field"
    def error = thrown(IllegalArgumentException)
    error.message.contains("pollIntervalMs")
  }

  def "empty symbol list is rejected"() {
    when: "an equity-price binding with no symbols is constructed"
    new EquityPriceProperties(true, 1000L, [])

    then: "construction fails referencing symbols"
    def error = thrown(IllegalArgumentException)
    error.message.contains("symbols")
  }

  def "symbol that is not an uppercase ticker is rejected"() {
    when: "an equity-price binding contains a malformed symbol"
    new EquityPriceProperties(true, 1000L, ["SPY", "natural_gas"])

    then: "construction fails with the pattern hint"
    def error = thrown(IllegalArgumentException)
    error.message.contains("[A-Z]{1,5}")
  }

  def "alpha-vantage permits non-ticker commodity identifiers"() {
    when: "alpha-vantage binding carries commodities such as NATURAL_GAS"
    def props = new AlphaVantageProperties(
        true, "key", "https://example.com", 86400000L, ["SPY"], ["GOLD", "NATURAL_GAS"], 12100L, true)

    then: "construction succeeds because commodities are not ticker-validated"
    noExceptionThrown()
    props.commodities.contains("NATURAL_GAS")
  }

  def "non-positive duration is rejected"() {
    when: "a yahoo-finance binding with a zero connect timeout is constructed"
    new YahooFinanceProperties(true, "https://example.com", Duration.ZERO, Duration.ofSeconds(30))

    then: "construction fails referencing connectTimeout"
    def error = thrown(IllegalArgumentException)
    error.message.contains("connectTimeout")
  }

  def "valid anomaly properties construct without error"() {
    when: "a valid anomaly binding is constructed"
    def props = new AnomalyProperties(true, 5L, true)

    then: "no validation fires"
    noExceptionThrown()
    props.enabled
  }

  def "nested ingestion last-known-good staleness is validated"() {
    when: "an ingestion binding has a non-positive cache staleness"
    new IngestionMonitorProperties(
        new IngestionMonitorProperties.LkgCache(0L),
        new IngestionMonitorProperties.Bulkhead(30000L))

    then: "construction fails referencing maxStalenessSeconds"
    def error = thrown(IllegalArgumentException)
    error.message.contains("maxStalenessSeconds")
  }

  def "finnhub binding rejects a bad ticker while validating reconnect backoff"() {
    when: "a finnhub binding mixes a bad symbol with otherwise valid values"
    new FinnhubProperties(
        "key", true, "https://finnhub.io", true, "wss://ws.finnhub.io", 60000L,
        ["spy"], true, Duration.ofSeconds(5), Duration.ofSeconds(30))

    then: "construction fails with the ticker pattern hint"
    def error = thrown(IllegalArgumentException)
    error.message.contains("[A-Z]{1,5}")
  }
}
