package com.tickonomics.ingestion.filter

import com.tickonomics.persistence.entity.TickData
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class TimePeriodicityFilterSpec extends Specification {

    static final Instant WHOLE_SECOND = Instant.parse("2026-05-24T12:00:00.000000000Z")
    static final Instant MID_SECOND = Instant.parse("2026-05-24T12:00:00.500000000Z")

    @Subject
    TimePeriodicityFilter filter = new TimePeriodicityFilter(100)

    def "given tick at whole second with price spike, when filter, then smoothed"() {
        given:
            def tick = new TickData(WHOLE_SECOND, "SPY", 460.00, 5000, new int[0])
            def recent = [
                new TickData(WHOLE_SECOND.minusMillis(500), "SPY", 450.00, 100, new int[0]),
                new TickData(WHOLE_SECOND.minusMillis(200), "SPY", 450.50, 110, new int[0])
            ]

        when:
            def result = filter.apply(tick, recent)

        then:
            result.price() < tick.price()
            result.volume() < tick.volume()
    }

    def "given tick mid-second, when filter, then unchanged"() {
        given:
            def tick = new TickData(MID_SECOND, "SPY", 460.00, 5000, new int[0])
            def recent = [
                new TickData(MID_SECOND.minusMillis(500), "SPY", 450.00, 100, new int[0])
            ]

        when:
            def result = filter.apply(tick, recent)

        then:
            result == tick
    }

    def "given tick at whole second without price spike, when filter, then unchanged"() {
        given:
            def tick = new TickData(WHOLE_SECOND, "SPY", 450.51, 100, new int[0])
            def recent = [
                new TickData(WHOLE_SECOND.minusMillis(500), "SPY", 450.50, 100, new int[0]),
                new TickData(WHOLE_SECOND.minusMillis(200), "SPY", 450.50, 105, new int[0])
            ]

        when:
            def result = filter.apply(tick, recent)

        then:
            result == tick
    }

    def "given insufficient context, when filter, then unchanged"() {
        given:
            def tick = new TickData(WHOLE_SECOND, "SPY", 460.00, 5000, new int[0])

        when:
            def result = filter.apply(tick, [])

        then:
            result == tick
    }
}
