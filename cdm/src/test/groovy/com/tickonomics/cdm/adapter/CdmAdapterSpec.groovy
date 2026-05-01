package com.tickonomics.cdm.adapter

import com.tickonomics.cdm.adapter.raw.FredObservation
import com.tickonomics.cdm.adapter.raw.NyFedRateResponse
import com.tickonomics.cdm.adapter.raw.PolygonTick
import com.tickonomics.cdm.enums.InstrumentType
import spock.lang.Specification

import java.time.Instant

class CdmAdapterSpec extends Specification {

  private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z")

  def "FredCdmAdapter adapts Fred EFFR observation to CdmRateSnapshot"() {
    given:
        def adapter = new FredCdmAdapter()
        def raw = new FredObservation(NOW, "EFFR", 4.33, "FRED")

    when:
        def result = adapter.toCdm(raw)

    then:
        result.time() == NOW
        result.instrumentType() == InstrumentType.EFFR
        result.value() == 4.33
        result.source() == "FRED"
  }

  def "FredCdmAdapter adapts Fred RRP observation to Repo instrument type"() {
    given:
        def adapter = new FredCdmAdapter()
        def raw = new FredObservation(NOW, "RRPONTSYD", 0.0, "FRED")

    when:
        def result = adapter.toCdm(raw)

    then:
        result.instrumentType() == InstrumentType.REPO
  }

  def "FredCdmAdapter adapts Fred IORB observation to Iorb instrument type"() {
    given:
        def adapter = new FredCdmAdapter()
        def raw = new FredObservation(NOW, "IORB", 4.40, "FRED")

    when:
        def result = adapter.toCdm(raw)

    then:
        result.instrumentType() == InstrumentType.IORB
  }

  def "NyFedCdmAdapter adapts NyFed SOFR response to CdmRateSnapshot"() {
    given:
        def adapter = new NyFedCdmAdapter()
        def raw = new NyFedRateResponse(NOW, "sofr", 4.29, "NY_FED")

    when:
        def result = adapter.toCdm(raw)

    then:
        result.time() == NOW
        result.instrumentType() == InstrumentType.SOFR
        result.value() == 4.29
        result.source() == "NY_FED"
  }

  def "NyFedCdmAdapter adapts NyFed TGCR response to Tgcr instrument type"() {
    given:
        def adapter = new NyFedCdmAdapter()
        def raw = new NyFedRateResponse(NOW, "tgcr", 4.28, "NY_FED")

    when:
        def result = adapter.toCdm(raw)

    then:
        result.instrumentType() == InstrumentType.TGCR
  }

  def "NyFedCdmAdapter adapts NyFed BGCR response to Bgcr instrument type"() {
    given:
        def adapter = new NyFedCdmAdapter()
        def raw = new NyFedRateResponse(NOW, "bgcr", 4.30, "NY_FED")

    when:
        def result = adapter.toCdm(raw)

    then:
        result.instrumentType() == InstrumentType.BGCR
  }

  def "PolygonTickCdmAdapter adapts PolygonTick to CdmTick"() {
    given:
        def adapter = new PolygonTickCdmAdapter()
        def raw = new PolygonTick(NOW, "SPY", 450.50, 1000, [0, 1] as int[])

    when:
        def result = adapter.toCdm(raw)

    then:
        result.time() == NOW
        result.symbol() == "SPY"
        result.price() == 450.50
        result.volume() == 1000
        result.conditions() == [0, 1] as int[]
  }

  def "PolygonTickCdmAdapter adapts PolygonTick with zero volume"() {
    given:
        def adapter = new PolygonTickCdmAdapter()
        def raw = new PolygonTick(NOW, "QQQ", 380.0, 0, [] as int[])

    when:
        def result = adapter.toCdm(raw)

    then:
        result.volume() == 0
  }
}
