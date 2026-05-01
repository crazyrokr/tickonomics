package com.tickonomics.cdm.model

import com.tickonomics.cdm.enums.DayCountConvention
import com.tickonomics.cdm.enums.OptionType
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant
import java.time.LocalDate

class CdmOptionSnapshotSpec extends Specification {

    def "valid option data sets all fields correctly"() {
        given:
        def id = UUID.randomUUID()
        def now = Instant.now()
        def expiry = LocalDate.of(2026, 9, 19)

        when:
        def snapshot = new CdmOptionSnapshot(
                id,
                "AAPL",
                200.00G,
                expiry,
                OptionType.PUT,
                DayCountConvention.ACT_360,
                0.45,
                0.10,
                -0.02,
                0.70,
                0.10,
                0.18,
                0.25,
                3.50,
                3.70,
                800L,
                now
        )

        then:
        snapshot.id() == id
        snapshot.underlyingSymbol() == "AAPL"
        snapshot.strike() == 200.00G
        snapshot.expiryDate() == expiry
        snapshot.type() == OptionType.PUT
        snapshot.dayCountConvention() == DayCountConvention.ACT_360
        snapshot.delta() == 0.45
        snapshot.gamma() == 0.10
        snapshot.theta() == -0.02
        snapshot.vega() == 0.70
        snapshot.rho() == 0.10
        snapshot.impliedVol() == 0.18
        snapshot.ttmYears() == 0.25
        snapshot.bid() == 3.50
        snapshot.ask() == 3.70
        snapshot.openInterest() == 800L
        snapshot.observationTime() == now
    }

    @Unroll
    def "constructor throws IllegalArgumentException when #desc"() {
        when:
        new CdmOptionSnapshot(
                UUID.randomUUID(), "SPY", strike,
                LocalDate.of(2026, 6, 20), OptionType.CALL,
                DayCountConvention.ACT_365_FIXED,
                0.5, 0.1, -0.01, 0.5, 0.1,
                0.2, ttm, bid, ask, 100L, Instant.now()
        )

        then:
        thrown(IllegalArgumentException)

        where:
        desc                     | strike   | ttm   | bid  | ask
        "strike is zero"         | 0.00G    | 0.1   | 1.0  | 1.2
        "strike is negative"     | -100.00G | 0.1   | 1.0  | 1.2
        "bid > ask"              | 450.00G  | 0.1   | 5.5  | 5.0
        "ttmYears is negative"   | 450.00G  | -0.01 | 5.0  | 5.2
    }
}
