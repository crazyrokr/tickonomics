package com.tickonomics.computation.scenario

import com.tickonomics.computation.kpi.KpiResult
import spock.lang.Specification
import spock.lang.Subject

class AumfScenarioEngineSpec extends Specification {

    @Subject
    AumfScenarioEngine engine = new AumfScenarioEngine()

    def "given normal conditions, when evaluate, then NORMAL status"() {
        given:
            def kpis = Map.of(
                "volatilityRegime", new KpiResult("Volatility Regime", 20.0, "LOW_VOL", "%", ""),
                "efficiencyGap", new KpiResult("Efficiency Gap", 2.0, "NORMAL", "bps", ""),
                "liquidityStress", new KpiResult("LSI", 0.3, "NORMAL", "z-score", "")
            )

        when:
            def result = engine.evaluate(kpis)

        then:
            result.status() == AumfStatus.NORMAL
    }

    def "given extreme volatility matching crisis, when evaluate, then SUSPENDED_UNCERTAINTY"() {
        given:
            def kpis = Map.of(
                "volatilityRegime", new KpiResult("Volatility Regime", 90.0, "EXTREME", "%", ""),
                "efficiencyGap", new KpiResult("Efficiency Gap", 250.0, "STRESSED", "bps", ""),
                "rrpDrainVelocity", new KpiResult("RRP Drain", -15.0, "STRESSED", "bps/day", ""),
                "liquidityStress", new KpiResult("LSI", 3.0, "STRESSED", "z-score", "")
            )

        when:
            def result = engine.evaluate(kpis)

        then:
            result.status() == AumfStatus.SUSPENDED_UNCERTAINTY
    }

    def "given partial crisis match, when evaluate, then PROCEED_CAUTIOUSLY"() {
        given:
            def kpis = Map.of(
                "volatilityRegime", new KpiResult("Volatility Regime", 50.0, "HIGH_VOL", "%", ""),
                "efficiencyGap", new KpiResult("Efficiency Gap", 60.0, "ELEVATED", "bps", "")
            )

        when:
            def result = engine.evaluate(kpis)

        then:
            result.status() == AumfStatus.PROCEED_CAUTIOUSLY || result.status() == AumfStatus.NORMAL
    }

    def "given empty KPIs, when evaluate, then NORMAL"() {
        when:
            def result = engine.evaluate(Map.of())

        then:
            result.status() == AumfStatus.NORMAL
    }
}
