package com.tickonomics.computation.equity

import com.tickonomics.computation.strategy.AlphaSignal
import com.tickonomics.computation.strategy.StrategyContext
import spock.lang.Specification
import spock.lang.Subject

class ClusterMeanReversionStrategySpec extends Specification {

  UniverseAggregator aggregator = new UniverseAggregator()

  @Subject
  ClusterMeanReversionStrategy strategy = new ClusterMeanReversionStrategy(aggregator)

  def "given extreme outlier above mean, when compute, then signals short reversion"() {
    given:
        Map<String, Double> aggregatorReturns = [A: 0.01d, B: 0.02d, C: 0.03d]
        aggregator.processTickCycle(aggregatorReturns)

        Map<String, Double> signalReturns = [A: 0.01d, B: 0.02d, C: 0.50d]
        StrategyContext ctx = new StrategyContext(0.95d, 0.8d)

    when:
        AlphaSignal signal = strategy.compute(signalReturns, ctx)

    then:
        signal != null
        signal.direction() == "SHORT"
        signal.strength() > 0.0
  }

  def "given uniform returns, when compute, then returns neutral"() {
    given:
        Map<String, Double> returns = [A: 0.02d, B: 0.02d, C: 0.02d]
        aggregator.processTickCycle(returns)
        StrategyContext ctx = new StrategyContext(0.95d, 0.8d)

    when:
        AlphaSignal signal = strategy.compute(returns, ctx)

    then:
        signal.direction() == "NEUTRAL"
  }

  def "given extreme outlier below mean, when compute, then signals long reversion"() {
    given:
        Map<String, Double> aggregatorReturns = [A: 0.01d, B: 0.02d, C: 0.03d]
        aggregator.processTickCycle(aggregatorReturns)

        Map<String, Double> signalReturns = [A: -0.40d, B: 0.02d, C: 0.03d]
        StrategyContext ctx = new StrategyContext(0.95d, 0.8d)

    when:
        AlphaSignal signal = strategy.compute(signalReturns, ctx)

    then:
        signal != null
        signal.direction() == "LONG"
        signal.strength() > 0.0
  }

  def "given no universe context, when compute, then returns neutral"() {
    given:
        StrategyContext ctx = new StrategyContext(0.95d, 0.8d)

    when:
        AlphaSignal signal = strategy.compute([A: 0.01d], ctx)

    then:
        signal.direction() == "NEUTRAL"
  }
}
