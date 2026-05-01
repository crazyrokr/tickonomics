package com.tickonomics.computation.kpi

import spock.lang.Specification

class SignalGeneratorSpec extends Specification {

  SignalGenerator generator = new SignalGenerator(new NormalizationService())

  def setup() {
    generator.setCooldownMs(0)
    generator.setTransactionCostBps(0.1)
  }

  private static double[] generateHistory(int count, double base, double step) {
    double[] values = new double[count]
    for (int i = 0; i < count; i++) {
      values[i] = base + i * step
    }
    return values
  }

  private static IliResult validIli(double value) {
    return new IliResult(value, 1.0, -0.5, 0.3, IliResult.STATUS_VALID,
        [0.4, 0.35, 0.25] as double[], null, null)
  }

  private static IliResult degradedIli(double value) {
    return new IliResult(value, 1.0, -0.5, 0.3, IliResult.STATUS_DEGRADED,
        [0.4, 0.35, 0.25] as double[], null, null)
  }

  private static IliResult dislocatedIli(double value) {
    return new IliResult(value, 1.0, -0.5, 0.3, IliResult.STATUS_DISLOCATED,
        [0.4, 0.35, 0.25] as double[], "DIVERGENT", 3.5)
  }

  def "evaluate returns buy signal for high percentile"() {
    given:
        def history = generateHistory(100, 0.0, 0.01)
        def ili = validIli(2.0)

    when:
        def result = generator.evaluate("SPY", ili, history)

    then:
        result.isPresent()
        result.get().direction() == SignalResult.DIR_BUY
        result.get().status() == SignalResult.STATUS_ACTIONABLE
  }

  def "evaluate returns sell signal for low percentile"() {
    given:
        def history = generateHistory(100, 1.0, 0.01)
        def ili = validIli(-1.0)

    when:
        def result = generator.evaluate("SPY", ili, history)

    then:
        result.isPresent()
        result.get().direction() == SignalResult.DIR_SELL
  }

  def "evaluate returns empty for mid percentile"() {
    given:
        def history = generateHistory(100, 0.0, 0.01)
        def ili = validIli(0.5)

    when:
        def result = generator.evaluate("SPY", ili, history)

    then:
        result.isEmpty()
  }

  def "evaluate returns empty for dislocated ILI"() {
    given:
        def history = generateHistory(100, 0.0, 0.01)
        def ili = dislocatedIli(2.0)

    when:
        def result = generator.evaluate("SPY", ili, history)

    then:
        result.isEmpty()
  }

  def "evaluate returns insufficient status for insufficient history"() {
    given:
        def history = generateHistory(10, 0.0, 0.01)
        def ili = validIli(2.0)

    when:
        def result = generator.evaluate("SPY", ili, history)

    then:
        result.isPresent()
        result.get().status() == SignalResult.STATUS_INSUFFICIENT
  }

  def "evaluate returns speculative status for degraded ILI"() {
    given:
        def history = generateHistory(100, 0.0, 0.01)
        def ili = degradedIli(2.0)

    when:
        def result = generator.evaluate("SPY", ili, history)

    then:
        result.isPresent()
        result.get().status() == SignalResult.STATUS_SPECULATIVE
  }

  def "evaluate returns insufficient status for null history"() {
    given:
        def ili = validIli(2.0)

    when:
        def result = generator.evaluate("SPY", ili, null)

    then:
        result.isPresent()
        result.get().status() == SignalResult.STATUS_INSUFFICIENT
  }

  def "determine direction based on percentile"() {
    expect:
        generator.determineDirection(percentile) == direction

    where:
        percentile || direction
        85.0       || SignalResult.DIR_BUY
        15.0       || SignalResult.DIR_SELL
        50.0       || null
        80.0       || SignalResult.DIR_BUY
        20.0       || SignalResult.DIR_SELL
  }

  def "estimate expected move returns positive for high percentile"() {
    given:
        def history = generateHistory(50, 0.0, 0.1)

    when:
        double move = generator.estimateExpectedMove(history, 90.0)

    then:
        move > 0
  }

  def "estimate expected move returns zero for mid percentile"() {
    given:
        def history = generateHistory(50, 0.0, 0.1)

    when:
        double move = generator.estimateExpectedMove(history, 50.0)

    then:
        move == 0.0D
  }
}
