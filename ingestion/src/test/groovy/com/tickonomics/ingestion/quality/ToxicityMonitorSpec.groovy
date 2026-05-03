package com.tickonomics.ingestion.quality

import spock.lang.Specification
import spock.lang.Subject

class ToxicityMonitorSpec extends Specification {

    @Subject
    ToxicityMonitor monitor = new ToxicityMonitor()

    def "given high quote-to-trade ratio, when compute, then classified as HARMFUL"() {
        given:
            def trades = [100.0d, 50.0d, 80.0d, 200.0d, 30.0d]
            def quotes = [5000.0d, 3000.0d, 4500.0d, 6000.0d, 2000.0d]

        when:
            def result = monitor.compute(trades, quotes, 5)

        then:
            result.classification() == ToxicityMonitor.ToxicityClassification.HARMFUL
            result.score() > 0
    }

    def "given balanced volumes, when compute, then classified as BENEFICIAL"() {
        given:
            def trades = [100.0d, 100.0d, 100.0d, 100.0d]
            def quotes = [50.0d, 50.0d, 50.0d, 50.0d]

        when:
            def result = monitor.compute(trades, quotes, 4)

        then:
            result.classification() == ToxicityMonitor.ToxicityClassification.BENEFICIAL
    }

    def "given empty trades, when compute, then NEUTRAL with zero score"() {
        when:
            def result = monitor.compute([], [], 0)

        then:
            result.score() == 0.0
            result.classification() == ToxicityMonitor.ToxicityClassification.NEUTRAL
    }

    def "given no quote data, when compute, then uses zero order-to-trade ratio"() {
        given:
            def trades = [100.0d, 100.0d, 100.0d]

        when:
            def result = monitor.compute(trades, null, 3)

        then:
            result.score() >= 0
    }
}
