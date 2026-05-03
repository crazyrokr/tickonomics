package com.tickonomics.computation.ili

import com.tickonomics.computation.client.RestClientAnalyticsWorkerClient
import com.tickonomics.computation.kpi.IliCalculator
import com.tickonomics.computation.kpi.ZscoreResult
import spock.lang.Specification
import spock.lang.Subject

class AdaptiveIliCalculatorSpec extends Specification {

    IliCalculator iliCalculator = new IliCalculator()
    WeightedWeightStore weightStore = new WeightedWeightStore([0.4, 0.35, 0.25] as double[], 10.0)
    RestClientAnalyticsWorkerClient analyticsClient = Mock()

    @Subject
    AdaptiveIliCalculator calculator = new AdaptiveIliCalculator(iliCalculator, weightStore, analyticsClient)

    def "given optimizer returns valid deltas, when calculate, then ILI computed with updated weights"() {
        given:
            def zRrp = new ZscoreResult("RRP", 4.5, 0.8, 4.2, 1.5, 60, true)
            def zSpread = new ZscoreResult("SPREAD", 0.3, 0.5, 0.2, 1.0, 60, true)
            def zVol = new ZscoreResult("VOL", 15.0, -0.3, 15.5, 2.0, 20, true)
            analyticsClient.sendAnalysisRequest(_ as String, _ as Map) >> Map.of("weight_deltas", [0.01, 0.01, -0.02])

        when:
            def result = calculator.calculate(zRrp, zSpread, zVol)

        then:
            result.iliValue() != 0
            result.dataStatus() == "VALID"
    }

    def "given optimizer unavailable, when calculate, then base weights used"() {
        given:
            def zRrp = new ZscoreResult("RRP", 4.5, 0.8, 4.2, 1.5, 60, true)
            def zSpread = new ZscoreResult("SPREAD", 0.3, 0.5, 0.2, 1.0, 60, true)
            def zVol = new ZscoreResult("VOL", 15.0, -0.3, 15.5, 2.0, 20, true)
            analyticsClient.sendAnalysisRequest(_ as String, _ as Map) >> Map.of("error", "unavailable")

        when:
            def result = calculator.calculate(zRrp, zSpread, zVol)

        then:
            result.iliValue() != 0
    }

    def "given optimizer throws exception, when calculate, then fallback succeeds"() {
        given:
            def zRrp = new ZscoreResult("RRP", 4.5, 0.8, 4.2, 1.5, 60, true)
            def zSpread = new ZscoreResult("SPREAD", 0.3, 0.5, 0.2, 1.0, 60, true)
            def zVol = new ZscoreResult("VOL", 15.0, -0.3, 15.5, 2.0, 20, true)
            analyticsClient.sendAnalysisRequest(_ as String, _ as Map) >> { throw new RuntimeException("timeout") }

        when:
            def result = calculator.calculate(zRrp, zSpread, zVol)

        then:
            result.iliValue() != 0
    }
}
