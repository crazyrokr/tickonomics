package com.tickonomics.cdm.adapter

import com.tickonomics.cdm.adapter.raw.DataHubPriceRow
import com.tickonomics.cdm.adapter.raw.ShillerSp500Row
import com.tickonomics.cdm.enums.InstrumentType
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class DataHubAdaptersSpec extends Specification {

  @Subject
  VixCdmAdapter vixAdapter = new VixCdmAdapter()

  @Subject
  OilPriceCdmAdapter wtiAdapter = new OilPriceCdmAdapter("DATAHUB_OIL_WTI")

  @Subject
  OilPriceCdmAdapter brentAdapter = new OilPriceCdmAdapter("DATAHUB_OIL_BRENT")

  @Subject
  GoldPriceCdmAdapter goldAdapter = new GoldPriceCdmAdapter()

  @Subject
  ShillerSp500CdmAdapter shillerAdapter = new ShillerSp500CdmAdapter()

  def "VIX adapter maps to EQUITY instrument type"() {
    given:
      def row = new DataHubPriceRow(Instant.now(), 17.49)

    when:
      def snapshot = vixAdapter.toCdm(row)

    then:
      snapshot.instrumentType() == InstrumentType.EQUITY
      snapshot.value() == 17.49
      snapshot.source() == "DATAHUB_VIX"
  }

  def "WTI oil adapter maps to COMMODITY_OIL with WTI source"() {
    given:
      def row = new DataHubPriceRow(Instant.now(), 75.32)

    when:
      def snapshot = wtiAdapter.toCdm(row)

    then:
      snapshot.instrumentType() == InstrumentType.COMMODITY_OIL
      snapshot.value() == 75.32
      snapshot.source() == "DATAHUB_OIL_WTI"
  }

  def "Brent oil adapter maps to COMMODITY_OIL with Brent source"() {
    given:
      def row = new DataHubPriceRow(Instant.now(), 78.50)

    when:
      def snapshot = brentAdapter.toCdm(row)

    then:
      snapshot.instrumentType() == InstrumentType.COMMODITY_OIL
      snapshot.source() == "DATAHUB_OIL_BRENT"
  }

  def "Gold adapter maps to COMMODITY_GOLD"() {
    given:
      def row = new DataHubPriceRow(Instant.now(), 2345.60)

    when:
      def snapshot = goldAdapter.toCdm(row)

    then:
      snapshot.instrumentType() == InstrumentType.COMMODITY_GOLD
      snapshot.value() == 2345.60
      snapshot.source() == "DATAHUB_GOLD"
  }

  def "Shiller adapter is identity adapter"() {
    given:
      def row = new ShillerSp500Row(Instant.now(), 4.44, 0.26, 0.4,
          12.46, 5.32, 4.44, 0.26, 0.4, 0.0)

    when:
      def result = shillerAdapter.toCdm(row)

    then:
      result.is(row)
      result.price() == 4.44
      result.cape() == 0.0
  }
}
