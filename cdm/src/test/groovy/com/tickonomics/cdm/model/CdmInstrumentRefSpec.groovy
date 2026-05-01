package com.tickonomics.cdm.model

import com.tickonomics.cdm.enums.InstrumentType
import spock.lang.Specification

class CdmInstrumentRefSpec extends Specification {

    def "given valid args, when construct, then fields set"() {
        when:
        def ref = new CdmInstrumentRef("SPY", InstrumentType.EQUITY, "POLYGON")

        then:
        ref.identifier() == "SPY"
        ref.instrumentType() == InstrumentType.EQUITY
        ref.source() == "POLYGON"
    }

    def "given null identifier, when construct, then throws"() {
        when:
        new CdmInstrumentRef(null, InstrumentType.EQUITY, "POLYGON")

        then:
        thrown(NullPointerException)
    }

    def "given null instrument type, when construct, then throws"() {
        when:
        new CdmInstrumentRef("SPY", null, "POLYGON")

        then:
        thrown(NullPointerException)
    }

    def "given null source, when construct, then throws"() {
        when:
        new CdmInstrumentRef("SPY", InstrumentType.EQUITY, null)

        then:
        thrown(NullPointerException)
    }
}
