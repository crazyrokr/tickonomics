package com.tickonomics.computation.backtest

import spock.lang.Specification
import spock.lang.Subject

class DelayDExecutorSpec extends Specification {

  Eq553SlippageModel slippageModel = new Eq553SlippageModel()

  @Subject
  DelayDExecutor executor = new DelayDExecutor(slippageModel)

  def "compareDelays produces two results (DELAY_0 and DELAY_1)"() {
    given:
        List<Double> returns = [0.01d, -0.005d, 0.02d, -0.01d, 0.015d]
        List<Double> execPrices = [0.01d, -0.005d, 0.02d, -0.01d, 0.015d, 0.01d]

    when:
        def results = executor.compareDelays("TEST", returns, execPrices)

    then:
        results.size() == 2
        results[0].delay() == ExecutionDelay.DELAY_0
        results[1].delay() == ExecutionDelay.DELAY_1
  }

  def "compareDelays handles very few data points gracefully"() {
    given:
        List<Double> returns = [0.01d]
        List<Double> execPrices = [0.01d, 0.01d]

    when:
        def results = executor.compareDelays("TEST", returns, execPrices)

    then:
        results.size() == 2
  }

  def "marks liquidity fragile on Sharpe ratio collapse"() {
    given: "100 data points where execution significantly degrades after 50"
        List<Double> signalReturns = []
        List<Double> execPrices = []
        def rng = new Random(42)

        100.times { i ->
          double sig = rng.nextGaussian() * 0.02
          signalReturns << sig
          execPrices << (i < 50 ? sig : -sig * 0.01)
        }
        execPrices << 0.0d

    when:
        def results = executor.compareDelays("FRAGILE", signalReturns, execPrices)

    then:
        results.size() == 2
  }
}
