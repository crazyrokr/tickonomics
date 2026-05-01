package com.tickonomics.cdm.enums

import spock.lang.Specification
import spock.lang.Unroll

class RateTypeSpec extends Specification {

    @Unroll
    def "RateType.#rateType toInstrumentType() returns #expected"() {
        expect:
        rateType.toInstrumentType() == expected

        where:
        rateType          | expected
        RateType.SOFR     | InstrumentType.SOFR
        RateType.EFFR     | InstrumentType.EFFR
        RateType.TGCR     | InstrumentType.TGCR
        RateType.BGCR     | InstrumentType.BGCR
        RateType.IORB     | InstrumentType.IORB
        RateType.OBFR     | InstrumentType.OBFR
        RateType.RRP      | InstrumentType.REPO
        RateType.TGA      | InstrumentType.REPO
        RateType.WALCL    | InstrumentType.REPO
        RateType.TBILL_3M | InstrumentType.BILL_3M
    }

    def "none of the RateTypes return null for instrument type"() {
        expect:
        RateType.values().every { it.toInstrumentType() != null }
    }
}
