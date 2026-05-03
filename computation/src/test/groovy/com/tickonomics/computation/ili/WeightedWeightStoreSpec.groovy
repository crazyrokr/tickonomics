package com.tickonomics.computation.ili

import spock.lang.Specification
import spock.lang.Subject

class WeightedWeightStoreSpec extends Specification {

    @Subject
    WeightedWeightStore store = new WeightedWeightStore([0.4, 0.35, 0.25] as double[], 10.0)

    def "given base weights, when constructed, then calibrated equals base"() {
        expect:
            store.calibratedWeights == [0.4, 0.35, 0.25] as double[]
    }

    def "given valid deltas within drift, when applyDelta, then weights updated and normalized"() {
        when:
            store.applyDelta([0.01, 0.01, -0.02] as double[])

        then:
            def weights = store.calibratedWeights
            Math.abs(weights[0] + weights[1] + weights[2] - 1.0) < 0.01
    }

    def "given delta exceeding max drift, when applyDelta, then delta clamped"() {
        when:
            store.applyDelta([1.0, 0.0, 0.0] as double[])

        then:
            def weights = store.calibratedWeights
            weights[0] < 0.5
    }

    def "given wrong length deltas, when applyDelta, then returns false"() {
        expect:
            !store.applyDelta([0.01, 0.02] as double[])
    }

    def "given modified weights, when resetToBase, then returns to base"() {
        given:
            store.applyDelta([0.01, 0.01, -0.02] as double[])

        when:
            store.resetToBase()

        then:
            store.calibratedWeights == [0.4, 0.35, 0.25] as double[]
    }
}
