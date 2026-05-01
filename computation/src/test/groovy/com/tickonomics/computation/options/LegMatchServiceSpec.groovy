package com.tickonomics.computation.options

import com.tickonomics.cdm.enums.DayCountConvention
import com.tickonomics.cdm.enums.OptionType
import com.tickonomics.cdm.model.CdmOptionSnapshot
import com.tickonomics.computation.strategy.AlphaSignal
import com.tickonomics.computation.strategy.StrategyContext
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant
import java.time.LocalDate

class LegMatchServiceSpec extends Specification {

  private static final Instant NOW = Instant.now()
  private static final LocalDate EXPIRY = LocalDate.of(2026, 6, 20)

  @Subject
  LegMatchService service = new LegMatchService()

  private static CdmOptionSnapshot option(double strike, OptionType type, LocalDate expiry = EXPIRY) {
    return new CdmOptionSnapshot(
        UUID.randomUUID(), "SPY", strike as BigDecimal, expiry, type,
        DayCountConvention.ACT_365_FIXED, 0.5d, 0.1d, -0.02d, 0.15d, 0.01d,
        0.25d, 0.5d, 1.0d, 2.0d, 100, NOW
    )
  }

  def "Given strikes [95, 100, 110], when findButterflySpreads, then result is EMPTY (asymmetry)"() {
    given:
        List<CdmOptionSnapshot> chain = [
            option(95.0d, OptionType.CALL),
            option(100.0d, OptionType.CALL),
            option(110.0d, OptionType.CALL)
        ]

    when:
        List<LegGroup> result = service.findButterflySpreads("SPY", chain)

    then:
        result.isEmpty()
  }

  def "Given strikes [95, 100, 105], when findButterflySpreads, then finds one butterfly"() {
    given:
        List<CdmOptionSnapshot> chain = [
            option(95.0d, OptionType.CALL),
            option(100.0d, OptionType.CALL),
            option(105.0d, OptionType.CALL)
        ]

    when:
        List<LegGroup> result = service.findButterflySpreads("SPY", chain)

    then:
        result.size() == 1
        result.first().legCount() == 3
        result.first().strategyType() == StrategyType.CALL_BUTTERFLY
  }

  def "Given 2 options, when findVerticalSpreads, then finds one spread"() {
    given:
        List<CdmOptionSnapshot> chain = [
            option(100.0d, OptionType.CALL),
            option(105.0d, OptionType.CALL)
        ]

    when:
        List<LegGroup> result = service.findVerticalSpreads("SPY", chain)

    then:
        result.size() == 1
        result.first().legCount() == 2
  }

  def "Given 4 symmetric strikes, when findCondors, then finds one condor"() {
    given:
        List<CdmOptionSnapshot> chain = [
            option(95.0d, OptionType.CALL),
            option(100.0d, OptionType.CALL),
            option(105.0d, OptionType.CALL),
            option(110.0d, OptionType.CALL)
        ]

    when:
        List<LegGroup> result = service.findCondors("SPY", chain)

    then:
        !result.isEmpty()
        result.first().legCount() == 4
  }

  def "Given options on different expiries, when findButterflySpreads, then groups by expiry"() {
    given:
        LocalDate expiry1 = LocalDate.of(2026, 6, 20)
        LocalDate expiry2 = LocalDate.of(2026, 7, 20)

        List<CdmOptionSnapshot> chain = [
            option(95.0d, OptionType.CALL, expiry1),
            option(100.0d, OptionType.CALL, expiry1),
            option(105.0d, OptionType.CALL, expiry1),
            option(95.0d, OptionType.CALL, expiry2),
            option(100.0d, OptionType.CALL, expiry2),
            option(105.0d, OptionType.CALL, expiry2)
        ]

    when:
        List<LegGroup> result = service.findButterflySpreads("SPY", chain)

    then:
        result.size() == 2
  }

  def "Given StrategyContext with irScore 0.8, when compute, then returns NEUTRAL"() {
    given:
        def strategy = new BaseOptionStrategy(StrategyType.BULL_CALL_SPREAD) {
          @Override
          protected double calculateFormula(LegGroup legs) {
            return 0.5d
          }
        }

        LegGroup legs = new LegGroup([
            option(100.0d, OptionType.CALL),
            option(105.0d, OptionType.CALL)
        ], StrategyType.BULL_CALL_SPREAD)

        StrategyContext lowIrCtx = new StrategyContext(0.8d, 0.9d)

    when:
        AlphaSignal signal = strategy.compute(legs, lowIrCtx)

    then:
        signal.direction() == "NEUTRAL"
        signal.strength() == 0.0d
  }

  def "Given StrategyContext with irScore 0.95, when compute, then returns actual signal"() {
    given:
        def strategy = new BaseOptionStrategy(StrategyType.BULL_CALL_SPREAD) {
          @Override
          protected double calculateFormula(LegGroup legs) {
            return 0.5d
          }
        }

        LegGroup legs = new LegGroup([
            option(100.0d, OptionType.CALL),
            option(105.0d, OptionType.CALL)
        ], StrategyType.BULL_CALL_SPREAD)

        StrategyContext goodCtx = new StrategyContext(0.95d, 0.9d)

    when:
        AlphaSignal signal = strategy.compute(legs, goodCtx)

    then:
        signal.direction() == "LONG"
        signal.strength() > 0
        signal.metrics().containsKey("net_delta")
        signal.metrics().containsKey("net_theta")
        signal.metrics().containsKey("spread_efficiency")
  }
}
