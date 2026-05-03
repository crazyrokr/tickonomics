package com.tickonomics.computation.signal

import com.tickonomics.computation.client.RestClientAnalyticsWorkerClient
import spock.lang.Specification
import spock.lang.Subject

class DiscreteMonitoringCorrectionSpec extends Specification {

    RestClientAnalyticsWorkerClient analyticsClient = Mock()

    @Subject
    DiscreteMonitoringCorrection correction = new DiscreteMonitoringCorrection(analyticsClient)

    def "given valid inputs, when applyCorrection, then threshold adjusted upward"() {
        when:
            def result = correction.applyCorrection(100.0, 15.0, 252)

        then:
            result.adjustedThreshold() > result.continuousThreshold()
            result.beta() == 0.5826
            result.monitoringPoints() == 252
    }

    def "given zero sigma, when applyCorrection, then no adjustment"() {
        when:
            def result = correction.applyCorrection(100.0, 0.0, 252)

        then:
            result.adjustedThreshold() == result.continuousThreshold()
    }

    def "given zero monitoring points, when applyCorrection, then no adjustment"() {
        when:
            def result = correction.applyCorrection(100.0, 15.0, 0)

        then:
            result.adjustedThreshold() == result.continuousThreshold()
    }

    def "given more monitoring points, when applyCorrection, then smaller correction"() {
        when:
            def result252 = correction.applyCorrection(100.0, 15.0, 252)
            def result12 = correction.applyCorrection(100.0, 15.0, 12)

        then:
            result252.correctionFactor() < result12.correctionFactor()
    }

    def "given worker returns valid response, when applyCorrectionFromWorker, then uses worker result"() {
        given:
            analyticsClient.sendAnalysisRequest(_ as String, _ as Map) >>
                Map.of("adjusted_threshold", 100.5d)

        when:
            def result = correction.applyCorrectionFromWorker(100.0, 15.0, 252)

        then:
            result.adjustedThreshold() == 100.5d
    }

    def "given worker fails, when applyCorrectionFromWorker, then falls back to local"() {
        given:
            analyticsClient.sendAnalysisRequest(_ as String, _ as Map) >>
                { throw new RuntimeException("unavailable") }

        when:
            def result = correction.applyCorrectionFromWorker(100.0, 15.0, 252)

        then:
            result.adjustedThreshold() > 100.0
    }
}
