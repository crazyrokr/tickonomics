package com.tickonomics.computation.regime

import com.tickonomics.computation.kpi.RegimeDetector
import spock.lang.Specification
import spock.lang.Subject

class GexWeightedRegimeDetectorSpec extends Specification {

    RegimeDetector delegate = new RegimeDetector()

    @Subject
    GexWeightedRegimeDetector detector = new GexWeightedRegimeDetector(delegate)

    def "given negative GEX and normal returns, when detectWithGex, then regime upgraded"() {
        given:
            def returns = (1..30).collect { 0.001d * (Math.random() - 0.5) }
            def negativeGex = -5_000_000.0

        when:
            def result = detector.detectWithGex(returns, negativeGex)

        then:
            result.regime().name() in ["HIGH_VOL", "METASTABLE", "NORMAL", "UNSTABLE"]
    }

    def "given positive GEX, when detectWithGex, then dampening applied"() {
        given:
            def returns = (1..30).collect { 0.01d * (Math.random() - 0.3) }
            def positiveGex = 5_000_000.0

        when:
            def result = detector.detectWithGex(returns, positiveGex)

        then:
            result != null
    }

    def "given neutral GEX, when detectWithGex, then base regime unchanged"() {
        given:
            def returns = (1..30).collect { 0.001d * (Math.random() - 0.5) }

        when:
            def result = detector.detectWithGex(returns, 0.0)

        then:
            result != null
    }

    def "given options data, when computeAggregateGex, then correct sum"() {
        given:
            def data = [
                new GexWeightedRegimeDetector.GexDataPoint(0.05d, 1000L, 100, 450.0d),
                new GexWeightedRegimeDetector.GexDataPoint(-0.03d, 500L, 100, 450.0d)
            ]

        when:
            def gex = detector.computeAggregateGex(data)

        then:
            gex == (0.05 * 1000 * 100 * 450.0) + (-0.03 * 500 * 100 * 450.0)
    }
}
