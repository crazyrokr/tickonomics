package com.tickonomics.cdm.mapper

import com.tickonomics.cdm.enums.InstrumentType
import com.tickonomics.cdm.enums.RateType
import spock.lang.Specification
import spock.lang.Unroll

class CdmInstrumentMapperSpec extends Specification {

  @Unroll
  def "maps Fred series #series to expected instrument"() {
    when:
        def ref = CdmInstrumentMapper.fromFredSeries(series)

    then:
        ref.identifier() == expectedId
        ref.instrumentType() == expectedType
        ref.source() == "FRED"

    where:
        series      | expectedId | expectedType
        "EFFR"      | "EFFR"     | InstrumentType.EFFR
        "RRPONTSYD" | "RRP"      | InstrumentType.REPO
        "WTREGEN"   | "TGA"      | InstrumentType.REPO
        "WALCL"     | "WALCL"    | InstrumentType.REPO
        "IORB"      | "IORB"     | InstrumentType.IORB
  }

  def "throws IllegalArgumentException for unknown Fred series"() {
    when:
        CdmInstrumentMapper.fromFredSeries("UNKNOWN")

    then:
        thrown(IllegalArgumentException)
  }

  @Unroll
  def "maps NyFed rate #rate to expected instrument"() {
    when:
        def ref = CdmInstrumentMapper.fromNyFedRate(rate)

    then:
        ref.identifier() == expectedId
        ref.instrumentType() == expectedType
        ref.source() == "NY_FED"

    where:
        rate   | expectedId | expectedType
        "sofr" | "SOFR"     | InstrumentType.SOFR
        "tgcr" | "TGCR"     | InstrumentType.TGCR
        "bgcr" | "BGCR"     | InstrumentType.BGCR
  }

  def "throws IllegalArgumentException for unknown NyFed rate"() {
    when:
        CdmInstrumentMapper.fromNyFedRate("unknown")

    then:
        thrown(IllegalArgumentException)
  }

  def "maps Polygon symbol to Equity instrument"() {
    when:
        def ref = CdmInstrumentMapper.fromPolygonSymbol("SPY")

    then:
        ref.identifier() == "SPY"
        ref.instrumentType() == InstrumentType.EQUITY
        ref.source() == "POLYGON"
  }

  def "maps all RateTypes to correct instruments"() {
    expect:
        RateType.values().each { rt ->
          def ref = CdmInstrumentMapper.fromRateType(rt)
          assert ref != null
          assert ref.identifier() == rt.name()
          assert ref.instrumentType() == rt.toInstrumentType()
        }
  }
}
