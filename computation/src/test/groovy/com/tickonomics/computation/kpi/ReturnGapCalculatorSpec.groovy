package com.tickonomics.computation.kpi

import spock.lang.Specification
import spock.lang.Subject

class ReturnGapCalculatorSpec extends Specification {

    @Subject
    ReturnGapCalculator calculator = new ReturnGapCalculator()

    def "given positive execution alpha, when calculate, then positive return gap"() {
        when:
            def result = calculator.calculate(0.08d, 0.05d)

        then:
            result.returnGap() > 0
            result.interpretation() == "POSITIVE_EXECUTION_ALPHA"
    }

    def "given negative execution drag, when calculate, then negative return gap"() {
        when:
            def result = calculator.calculate(0.03d, 0.07d)

        then:
            result.returnGap() < 0
            result.interpretation() == "NEGATIVE_EXECUTION_DRAG"
    }

    def "given similar returns, when calculate, then neutral interpretation"() {
        when:
            def result = calculator.calculate(0.05d, 0.052d)

        then:
            result.interpretation() == "NEUTRAL"
    }

    def "given return arrays, when calculate, then cumulative return gap computed"() {
        given:
            def investorReturns = [0.01d, 0.02d, 0.015d, 0.005d] as double[]
            def holdingsReturns = [0.01d, 0.02d, 0.01d, 0.005d] as double[]

        when:
            def result = calculator.calculate(investorReturns, holdingsReturns)

        then:
            result.returnGap() != 0
    }

    def "given mismatched arrays, when calculate, then INSUFFICIENT_DATA"() {
        given:
            def investorReturns = [0.01d, 0.02d] as double[]
            def holdingsReturns = [0.01d] as double[]

        when:
            def result = calculator.calculate(investorReturns, holdingsReturns)

        then:
            result.interpretation() == "INSUFFICIENT_DATA"
    }
}
