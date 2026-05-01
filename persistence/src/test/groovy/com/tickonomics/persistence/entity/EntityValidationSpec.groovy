package com.tickonomics.persistence.entity

import spock.lang.Specification

import java.time.Instant

class EntityValidationSpec extends Specification {
  private static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z")

  def "TickData: given valid args, when construct, then fields set"() {
    when:
        var tick = new TickData(NOW, "SPY", 450.50, 1000, [0, 1] as int[])
    then:
        tick.time() == NOW
        tick.symbol() == "SPY"
        tick.price() == 450.50D
        tick.volume() == 1000
        tick.conditions() == [0, 1] as int[]
  }

  def "TickData: given null time, when construct, then throws"() {
    when:
        new TickData(null, "SPY", 450.50, 1000, [] as int[])
    then:
        thrown(NullPointerException)
  }

  def "TickData: given blank symbol, when construct, then throws"() {
    when:
        new TickData(NOW, "", 450.50, 1000, [] as int[])
    then:
        thrown(IllegalArgumentException)
  }

  def "TickData: given negative price, when construct, then throws"() {
    when:
        new TickData(NOW, "SPY", -1.0, 1000, [] as int[])
    then:
        thrown(IllegalArgumentException)
  }

  def "TickData: given negative volume, when construct, then throws"() {
    when:
        new TickData(NOW, "SPY", 450.50, -1, [] as int[])
    then:
        thrown(IllegalArgumentException)
  }

  def "RateSnapshot: given valid args, when construct, then fields set"() {
    when:
        var snapshot = new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED")
    then:
        snapshot.time() == NOW
        snapshot.rateType() == "SOFR"
        snapshot.value() == 4.29D
        snapshot.source() == "NY_FED"
  }

  def "RateSnapshot: given null time, when construct, then throws"() {
    when:
        new RateSnapshot(null, "SOFR", 4.29, "NY_FED")
    then:
        thrown(NullPointerException)
  }

  def "RateSnapshot: given blank rateType, when construct, then throws"() {
    when:
        new RateSnapshot(NOW, "", 4.29, "NY_FED")
    then:
        thrown(IllegalArgumentException)
  }

  def "RateSnapshot: given NaN value, when construct, then throws"() {
    when:
        new RateSnapshot(NOW, "SOFR", Double.NaN, "NY_FED")
    then:
        thrown(IllegalArgumentException)
  }

  def "IliHistory: given valid args, when construct, then fields set"() {
    when:
        var entry = new IliHistory(NOW, 0.85, 1.2, -0.5, 0.3, "VALID", "{\"w1\":0.4}", null, null)
    then:
        entry.time() == NOW
        entry.iliValue() == 0.85D
        entry.dataStatus() == "VALID"
  }

  def "IliHistory: given null time, when construct, then throws"() {
    when:
        new IliHistory(null, 0.85, 1.2, -0.5, 0.3, "VALID", null, null, null)
    then:
        thrown(NullPointerException)
  }

  def "IliHistory: given blank dataStatus, when construct, then throws"() {
    when:
        new IliHistory(NOW, 0.85, 1.2, -0.5, 0.3, "", null, null, null)
    then:
        thrown(IllegalArgumentException)
  }

  def "ZscoreSeries: given valid args, when construct, then fields set"() {
    when:
        var entry = new ZscoreSeries(NOW, "Z_RRP", 0.05, 1.2, 252)
    then:
        entry.time() == NOW
        entry.component() == "Z_RRP"
        entry.lookbackDays() == 252
  }

  def "ZscoreSeries: given null time, when construct, then throws"() {
    when:
        new ZscoreSeries(null, "Z_RRP", 0.05, 1.2, 252)
    then:
        thrown(NullPointerException)
  }

  def "ZscoreSeries: given zero lookback, when construct, then throws"() {
    when:
        new ZscoreSeries(NOW, "Z_RRP", 0.05, 1.2, 0)
    then:
        thrown(IllegalArgumentException)
  }

  def "CorrelationOutput: given valid args, when construct, then fields set"() {
    when:
        var output = new CorrelationOutput(NOW, "SPY", "PEARSON_CORRELATION", 0.85, 0.01, 100, 5, "POSITIVE")
    then:
        output.time() == NOW
        output.symbol() == "SPY"
        output.metric() == "PEARSON_CORRELATION"
  }

  def "CorrelationOutput: given null time, when construct, then throws"() {
    when:
        new CorrelationOutput(null, "SPY", "PEARSON_CORRELATION", 0.85, null, null, null, null)
    then:
        thrown(NullPointerException)
  }

  def "SignalLog: given valid args, when construct, then fields set"() {
    when:
        var signal = new SignalLog(NOW, "SPY", "BUY", "ACTIONABLE", 85.0, 0.45, 2.5, 0.15, null)
    then:
        signal.symbol() == "SPY"
        signal.direction() == "BUY"
        signal.status() == "ACTIONABLE"
  }

  def "SignalLog: given blank symbol, when construct, then throws"() {
    when:
        new SignalLog(NOW, "", "BUY", "ACTIONABLE", 85.0, 0.45, 2.5, 0.15, null)
    then:
        thrown(IllegalArgumentException)
  }

  def "SignalLog: given null direction, when construct, then throws"() {
    when:
        new SignalLog(NOW, "SPY", null, "ACTIONABLE", 85.0, 0.45, 2.5, 0.15, null)
    then:
        thrown(IllegalArgumentException)
  }

  def "IngestionDlqEntry: given valid args, when construct, then fields set"() {
    when:
        var entry = new IngestionDlqEntry(NOW, "FRED", "{\"key\":\"value\"}", "timeout", null)
    then:
        entry.source() == "FRED"
        entry.payload() == "{\"key\":\"value\"}"
  }

  def "IngestionDlqEntry: given blank source, when construct, then throws"() {
    when:
        new IngestionDlqEntry(NOW, "", "{}", "error", null)
    then:
        thrown(IllegalArgumentException)
  }

  def "IngestionDlqEntry: given null payload, when construct, then throws"() {
    when:
        new IngestionDlqEntry(NOW, "FRED", null, "error", null)
    then:
        thrown(IllegalArgumentException)
  }
}
