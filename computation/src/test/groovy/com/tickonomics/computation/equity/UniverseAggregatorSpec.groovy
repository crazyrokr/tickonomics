package com.tickonomics.computation.equity

import spock.lang.Specification
import spock.lang.Subject

class UniverseAggregatorSpec extends Specification {

  @Subject
  UniverseAggregator aggregator = new UniverseAggregator()

  def "given known symbol returns, when processTickCycle, then universe mean and std are correct"() {
    given:
        Map<String, Double> returns = [AAPL: 0.02d, MSFT: 0.04d, GOOG: 0.06d]

    when:
        aggregator.processTickCycle(returns)
        UniverseContext context = aggregator.getContext()

    then:
        context != null
        context.symbolCount() == 3

        double expectedMean = (0.02d + 0.04d + 0.06d) / 3.0d
        Math.abs(context.universeMean() - expectedMean) < 1e-9

        double expectedVariance = (Math.pow(0.02d - expectedMean, 2)
            + Math.pow(0.04d - expectedMean, 2)
            + Math.pow(0.06d - expectedMean, 2)) / 3.0d
        double expectedStd = Math.sqrt(expectedVariance)
        Math.abs(context.universeStd() - expectedStd) < 1e-9
  }

  def "given symbol count exceeds 1000, when processTickCycle, then throws IllegalArgumentException"() {
    given:
        Map<String, Double> returns = (0..1000).collectEntries { i -> ["SYM$i", 0.01d] }

    when:
        aggregator.processTickCycle(returns)

    then:
        IllegalArgumentException e = thrown()
        e.message.contains("Symbol count exceeds cap")
  }

  def "given all same returns, when processTickCycle, then universe std is zero"() {
    given:
        Map<String, Double> returns = [A: 0.05d, B: 0.05d, C: 0.05d]

    when:
        aggregator.processTickCycle(returns)
        UniverseContext context = aggregator.getContext()

    then:
        context != null
        Math.abs(context.universeMean() - 0.05d) < 1e-9
        Math.abs(context.universeStd() - 0.0d) < 1e-9
  }

  def "given two sequential calls, when getContext, then returns latest context"() {
    given:
        Map<String, Double> first = [A: 0.01d, B: 0.02d]
        Map<String, Double> second = [X: 0.10d, Y: 0.20d]

    when:
        aggregator.processTickCycle(first)
        aggregator.processTickCycle(second)
        UniverseContext context = aggregator.getContext()

    then:
        context != null
        context.symbolCount() == 2
        Math.abs(context.universeMean() - 0.15d) < 1e-9
  }
}
