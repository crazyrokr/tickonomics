package com.tickonomics.computation.kpi

import spock.lang.Specification

class NormalizationServiceSpec extends Specification {

  NormalizationService service = new NormalizationService()

  def "normalize returns valid Z-score for valid history"() {
    given:
        double[] history = [1.0, 2.0, 3.0, 4.0, 5.0]

    when:
        def result = service.normalize(history, "Z_RRP", 4.5, LookbackTier.FLOW)

    then:
        result.valid()
        result.component() == "Z_RRP"
        result.rawValue() == 4.5D
        result.lookbackDays() == LookbackTier.FLOW.days()
  }

  def "normalize returns Z-score zero when current value is at mean"() {
    given:
        double[] history = [2.0, 4.0, 2.0, 4.0]
        double mean = service.computeMean(history)

    when:
        def result = service.normalize(history, "Z_SPREAD", mean, LookbackTier.VOLATILITY)

    then:
        result.zScore() == 0.0D
  }

  def "normalize returns invalid for null history"() {
    when:
        def result = service.normalize(null, "Z_RRP", 4.5, LookbackTier.MACRO)

    then:
        !result.valid()
        Double.isNaN(result.zScore())
  }

  def "normalize returns invalid for single value history"() {
    when:
        def result = service.normalize([4.5] as double[], "Z_RRP", 4.5, LookbackTier.FLOW)

    then:
        !result.valid()
  }

  def "normalize returns invalid for constant history"() {
    when:
        def result = service.normalize([3.0, 3.0, 3.0, 3.0] as double[], "Z_VOL", 3.0, LookbackTier.VOLATILITY)

    then:
        !result.valid()
  }

  def "normalize all tiers returns results for all tiers with sufficient history"() {
    given:
        double[] history = new double[300]
        (0..<history.length).each { history[it] = Math.sin(it * 0.1) + 4.0 }

    when:
        def results = service.normalizeAllTiers(history, "Z_RRP", 4.5)

    then:
        results.size() == 3
        results.containsKey(LookbackTier.MACRO)
        results.containsKey(LookbackTier.FLOW)
        results.containsKey(LookbackTier.VOLATILITY)
  }

  def "compute all z-scores returns correct length"() {
    given:
        double[] values = [1.0, 2.0, 3.0, 4.0, 5.0]

    when:
        double[] zscores = service.computeAllZscores(values)

    then:
        zscores.length == 5
  }

  def "compute all z-scores for constant data returns all NaN"() {
    given:
        double[] values = [3.0, 3.0, 3.0]

    when:
        double[] zscores = service.computeAllZscores(values)

    then:
        zscores.every { Double.isNaN(it) }
  }

  def "compute all z-scores for null data returns empty"() {
    expect:
        service.computeAllZscores(null).length == 0
  }

  def "compute percentile rank for known values"() {
    given:
        double[] values = [1.0, 2.0, 3.0, 4.0, 5.0]

    when:
        double rank = service.computePercentileRank(values, 3.0)

    then:
        Math.abs(rank - 40.0) < 1e-9
  }

  def "compute percentile rank for highest value"() {
    given:
        double[] values = [1.0, 2.0, 3.0]

    when:
        double rank = service.computePercentileRank(values, 3.0)

    then:
        Math.abs(rank - 66.666) < 0.1
  }

  def "compute percentile rank for lowest value"() {
    given:
        double[] values = [1.0, 2.0, 3.0]

    when:
        double rank = service.computePercentileRank(values, 0.5)

    then:
        rank == 0.0D
  }

  def "compute percentile rank for empty array returns NaN"() {
    expect:
        Double.isNaN(service.computePercentileRank(new double[0], 1.0))
  }
}
