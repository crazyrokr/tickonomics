package com.tickonomics.computation.backtest

import spock.lang.Specification
import spock.lang.Subject

class Eq553SlippageModelSpec extends Specification {

  @Subject
  private final Eq553SlippageModel model = new Eq553SlippageModel()

  def "Given high volume and low vol, when calculateSlippageBps, then slippage is small"() {
    when:
        double slippage = model.calculateSlippageBps(0.15, 10_000_000, 1000)

    then:
        slippage < 1.0
  }

  def "Given low volume and high vol, when calculateSlippageBps, then slippage is large"() {
    when:
        double slippage = model.calculateSlippageBps(0.50, 100_000, 50000)

    then:
        slippage > 0.0
  }

  def "Given zero ADDV, when calculateSlippageBps, then returns infinity"() {
    when:
        double slippage = model.calculateSlippageBps(0.20, 0, 100)

    then:
        slippage == Double.MAX_VALUE
  }

  def "Given positive ideal return exceeding slippage, when adjustReturn, then adjusted is positive"() {
    when:
        double adjusted = model.adjustReturn(0.05, 0.15, 5_000_000, 1000)

    then:
        adjusted <= 0.05
  }

  def "Given ideal return that collapses under slippage, when isLiquidityFragile, then returns true"() {
    expect:
        model.isLiquidityFragile(0.001, -0.002)
  }

  def "Given robust return, when isLiquidityFragile, then returns false"() {
    expect:
        !model.isLiquidityFragile(0.05, 0.04)
  }
}
