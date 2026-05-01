package com.tickonomics.computation.equity

import com.tickonomics.computation.strategy.AlphaSignal
import com.tickonomics.computation.strategy.StrategyContext
import spock.lang.Specification
import spock.lang.Subject

class MomentumDecileStrategySpec extends Specification {

  UniverseAggregator aggregator = new UniverseAggregator()

  @Subject
  MomentumDecileStrategy strategy = new MomentumDecileStrategy(aggregator)

  def "given positive momentum spread, when compute, then direction is long"() {
    given:
        Map<String, Double> returns = (0..99).collectEntries { ["SYM$it", it * 0.001d] }
        aggregator.processTickCycle(returns)

        StrategyContext ctx = new StrategyContext(0.95d, 0.8d)

    when:
        AlphaSignal signal = strategy.compute(returns, ctx)

    then:
        signal != null
        signal.direction() == "LONG"
        signal.strength() > 0.0
  }

  def "given low ir score, when compute, then returns neutral"() {
    given:
        Map<String, Double> returns = [A: 0.01d]
        aggregator.processTickCycle(returns)

        StrategyContext ctx = new StrategyContext(0.8d, 0.5d)

    when:
        AlphaSignal signal = strategy.compute(returns, ctx)

    then:
        signal.direction() == "NEUTRAL"
        signal.strength() == 0.0D
  }

  def "given empty returns, when compute, then returns neutral"() {
    given:
        aggregator.processTickCycle([A: 0.01d])
        StrategyContext ctx = new StrategyContext(0.95d, 0.8d)

    when:
        AlphaSignal signal = strategy.compute([:], ctx)

    then:
        signal.direction() == "NEUTRAL"
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
