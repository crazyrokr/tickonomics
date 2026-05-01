package com.tickonomics.ingestion.kernel

import spock.lang.Specification
import spock.lang.Subject

class KernelAggregatorSpec extends Specification {

  @Subject
  KernelAggregator aggregator = new KernelAggregator()

  def "Given constant prices, when computeRealizedVariance, then RV is zero"() {
    given:
        List<Double> prices = [100.0D, 100.0D, 100.0D, 100.0D, 100.0D]

    when:
        double rv = aggregator.computeRealizedVariance(prices, KernelAggregator.TUKEY_HANNING, 2)

    then:
        rv == 0.0D
  }

  def "Given random walk prices, when computeRealizedVariance, then RV is positive"() {
    given:
        Random rng = new Random(42)
        List<Double> prices = (1..200).collect { 100.0 + rng.nextGaussian() * 0.5 * it / it } // Simplified for generation, but let's be more precise
        double p = 100.0
        prices = []
        200.times {
          p += rng.nextGaussian() * 0.5
          prices << p
        }

    when:
        double rvTukey = aggregator.computeRealizedVariance(prices, KernelAggregator.TUKEY_HANNING, 10)
        double rvParzen = aggregator.computeRealizedVariance(prices, KernelAggregator.PARZEN, 10)

    then:
        rvTukey > 0
        rvParzen > 0
  }

  def "Given single price, when computeRealizedVariance, then returns zero"() {
    given:
        List<Double> prices = [100.0D]

    when:
        double rv = aggregator.computeRealizedVariance(prices, KernelAggregator.TUKEY_HANNING, 5)

    then:
        rv == 0.0D
  }

  def "Kernel function values at boundaries"() {
    expect:
        KernelAggregator.TUKEY_HANNING.apply(xT) == expectedT
        KernelAggregator.PARZEN.apply(xP) == expectedP

    where:
        xT   | expectedT | xP   | expectedP
        0.0D | 1.0D      | 0.0D | 1.0D
        1.0D | 0.0D      | 1.5D | 0.0D
  }
}
