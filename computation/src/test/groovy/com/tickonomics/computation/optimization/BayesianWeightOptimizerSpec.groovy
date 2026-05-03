package com.tickonomics.computation.optimization

import com.tickonomics.computation.ili.WeightedWeightStore
import spock.lang.Specification
import spock.lang.Subject

class BayesianWeightOptimizerSpec extends Specification {

    WeightedWeightStore weightStore = new WeightedWeightStore([0.4, 0.35, 0.25] as double[], 10.0)

    @Subject
    BayesianWeightOptimizer optimizer = new BayesianWeightOptimizer(weightStore)

    def "given insufficient data, when optimize, then returns INSUFFICIENT_DATA"() {
        given:
            def sharpe = [0.1d, 0.2d] as double[]

        when:
            def result = optimizer.optimize(OptimizationProfile.PROFIT_MAXIMIZER, sharpe)

        then:
            result.status() == "INSUFFICIENT_DATA"
    }

    def "given sufficient data, when optimize, then weights sum to 1.0"() {
        given:
            def sharpe = (1..30).collect { 0.5d + Math.random() * 0.5 } as double[]

        when:
            def result = optimizer.optimize(OptimizationProfile.PROFIT_MAXIMIZER, sharpe)

        then:
            def sum = result.weights().sum()
            Math.abs(sum - 1.0) < 0.01
    }

    def "given throughput profile, when optimize, then completes"() {
        given:
            def sharpe = (1..20).collect { 0.3d + Math.random() * 0.3 } as double[]

        when:
            def result = optimizer.optimize(OptimizationProfile.THROUGHPUT_MAXIMIZER, sharpe)

        then:
            result.iterations() > 0
    }
}
