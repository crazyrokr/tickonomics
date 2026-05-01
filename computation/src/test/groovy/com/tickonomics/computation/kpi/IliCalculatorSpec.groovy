package com.tickonomics.computation.kpi

import spock.lang.Specification

class IliCalculatorSpec extends Specification {

  IliCalculator calculator = new IliCalculator()

  private static ZscoreResult validZ(String component, double zScore) {
    return new ZscoreResult(component, 1.0, zScore, 0.5, 0.2, 60, true)
  }

  private static ZscoreResult invalidZ(String component) {
    return ZscoreResult.invalid(component, 1.0, 60)
  }

  def "calculate with all valid components returns valid status"() {
    given:
        def rrp = validZ("Z_RRP", 1.2)
        def spread = validZ("Z_SPREAD", -0.5)
        def vol = validZ("Z_VOL", 0.3)

    when:
        def result = calculator.calculate(rrp, spread, vol)

    then:
        result.dataStatus() == IliResult.STATUS_VALID
        Math.abs(result.iliValue() - (0.4 * 1.2 + 0.35 * (-0.5) - 0.25 * 0.3)) < 1e-9
  }

  def "calculate with custom weights"() {
    given:
        double[] weights = [0.5, 0.3, 0.2]
        def rrp = validZ("Z_RRP", 1.0)
        def spread = validZ("Z_SPREAD", 1.0)
        def vol = validZ("Z_VOL", 1.0)

    when:
        def result = calculator.calculate(rrp, spread, vol, weights)

    then:
        Math.abs(result.iliValue() - (0.5 * 1.0 + 0.3 * 1.0 - 0.2 * 1.0)) < 1e-9
  }

  def "calculate with one invalid component returns degraded and redistributed"() {
    given:
        def rrp = validZ("Z_RRP", 1.0)
        def spread = validZ("Z_SPREAD", 1.0)
        def vol = invalidZ("Z_VOL")

    when:
        def result = calculator.calculate(rrp, spread, vol)

    then:
        result.dataStatus() == IliResult.STATUS_DEGRADED
        result.iliValue() != 0.0D
  }

  def "calculate with all invalid components returns degraded status"() {
    given:
        def rrp = invalidZ("Z_RRP")
        def spread = invalidZ("Z_SPREAD")
        def vol = invalidZ("Z_VOL")

    when:
        def result = calculator.calculate(rrp, spread, vol)

    then:
        result.dataStatus() == IliResult.STATUS_DEGRADED
        result.iliValue() == 0.0D
  }

  def "apply proxy divergence results in dislocated status when divergent"() {
    given:
        def base = calculator.calculate(validZ("Z_RRP", 1.0), validZ("Z_SPREAD", 1.0), validZ("Z_VOL", 1.0))

    when:
        def result = calculator.withProxyDivergence(base, true, 3.5)

    then:
        result.dataStatus() == IliResult.STATUS_DISLOCATED
        result.proxyDivergenceStatus() == "DIVERGENT"
        result.proxyDivergenceScore() == 3.5
  }

  def "apply proxy divergence preserves valid status when not divergent"() {
    given:
        def base = calculator.calculate(validZ("Z_RRP", 1.0), validZ("Z_SPREAD", 1.0), validZ("Z_VOL", 1.0))

    when:
        def result = calculator.withProxyDivergence(base, false, 0.5)

    then:
        result.dataStatus() == IliResult.STATUS_VALID
        result.proxyDivergenceStatus() == "NORMAL"
  }

  def "redistribute weights returns unchanged when all valid"() {
    when:
        def result = calculator.redistributeWeights([0.4, 0.35, 0.25] as double[], [true, true, true] as boolean[])

    then:
        result.weights().sum() == 1.0D
  }

  def "redistribute weights redistributes to others when one invalid"() {
    when:
        def result = calculator.redistributeWeights([0.4, 0.35, 0.25] as double[], [true, true, false] as boolean[])

    then:
        result.weights()[2] == 0.0D
        Math.abs(result.weights()[0] + result.weights()[1] - 1.0) < 1e-9
        result.weights()[0] > 0.4
        result.weights()[1] > 0.35
  }

  def "redistribute weights returns unchanged when all invalid"() {
    when:
        def result = calculator.redistributeWeights([0.4, 0.35, 0.25] as double[], [false, false, false] as boolean[])

    then:
        result.weights()[0] == 0.4D
  }

  def "redistribute weights sums to one when weights sum to one"() {
    when:
        def result = calculator.redistributeWeights([0.5, 0.3, 0.2] as double[], [false, true, true] as boolean[])

    then:
        Math.abs(result.weights()[1] + result.weights()[2] - 1.0) < 1e-9
  }

  def "create IliResult with weights not summing to one throws exception"() {
    when:
        new IliResult(0.5, 1.0, -0.5, 0.3, "VALID", [0.5, 0.5, 0.5] as double[], null, null)

    then:
        thrown(IllegalArgumentException)
  }
}
