package com.tickonomics.ingestion.filter

import com.tickonomics.persistence.entity.TickData
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant
import java.math.BigDecimal;

class DeRoundingFilterSpec extends Specification {

    static final Instant ROUND_MARK = Instant.parse("2026-05-24T12:00:00Z")
    static final Instant NON_ROUND = Instant.parse("2026-05-24T12:02:30Z")

    @Subject
    DeRoundingFilter filter = new DeRoundingFilter(30, 5)

    def "given tick at round mark with volume spike, when filter, then volume smoothed"() {
        given:
            def tick = new TickData(ROUND_MARK, "SPY", BigDecimal.valueOf(450.50), 10000, new int[0])
            def recent = [
                new TickData(ROUND_MARK.minusSeconds(5), "SPY", BigDecimal.valueOf(450.48), 100, new int[0]),
                new TickData(ROUND_MARK.minusSeconds(3), "SPY", BigDecimal.valueOf(450.49), 120, new int[0]),
                new TickData(ROUND_MARK.minusSeconds(1), "SPY", BigDecimal.valueOf(450.50), 90, new int[0])
            ]

        when:
            def result = filter.apply(tick, recent)

        then:
            result.volume() < tick.volume()
            result.symbol() == "SPY"
    }

    def "given tick not at round mark, when filter, then unchanged"() {
        given:
            def tick = new TickData(NON_ROUND, "SPY", BigDecimal.valueOf(450.50), 10000, new int[0])
            def recent = [
                new TickData(NON_ROUND.minusSeconds(1), "SPY", BigDecimal.valueOf(450.49), 100, new int[0])
            ]

        when:
            def result = filter.apply(tick, recent)

        then:
            result.volume() == tick.volume()
    }

    def "given tick at round mark without spike, when filter, then unchanged"() {
        given:
            def tick = new TickData(ROUND_MARK, "SPY", BigDecimal.valueOf(450.50), 100, new int[0])
            def recent = [
                new TickData(ROUND_MARK.minusSeconds(2), "SPY", BigDecimal.valueOf(450.49), 95, new int[0]),
                new TickData(ROUND_MARK.minusSeconds(1), "SPY", BigDecimal.valueOf(450.50), 105, new int[0])
            ]

        when:
            def result = filter.apply(tick, recent)

        then:
            result.volume() == tick.volume()
    }

    def "given insufficient context ticks, when filter, then unchanged"() {
        given:
            def tick = new TickData(ROUND_MARK, "SPY", BigDecimal.valueOf(450.50), 10000, new int[0])
            def recent = [
                new TickData(ROUND_MARK.minusSeconds(1), "SPY", BigDecimal.valueOf(450.50), 100, new int[0])
            ]

        when:
            def result = filter.apply(tick, recent)

        then:
            result == tick
    }
}
