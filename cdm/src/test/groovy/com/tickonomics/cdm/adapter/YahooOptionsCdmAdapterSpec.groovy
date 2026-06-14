package com.tickonomics.cdm.adapter

import com.tickonomics.cdm.adapter.raw.YahooOptionContract
import com.tickonomics.cdm.enums.DayCountConvention
import com.tickonomics.cdm.enums.OptionType
import spock.lang.Specification
import spock.lang.Subject

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class YahooOptionsCdmAdapterSpec extends Specification {

  @Subject
  YahooOptionsCdmAdapter adapter = new YahooOptionsCdmAdapter()

  def "given call contract, when toCdm, then map to CDM with CALL type"() {
    given:
      def contract = new YahooOptionContract(
          Instant.now(), "SPY", BigDecimal.valueOf(500), LocalDate.now().plusMonths(1),
          "CALL", 5.5, 6.0, 5.75, 0.18, 0.55, 0.02, -0.05, 0.3, 0.1, 15000, 503.0)

    when:
      def snapshot = adapter.toCdm(contract)

    then:
      snapshot.underlyingSymbol() == "SPY"
      snapshot.type() == OptionType.CALL
      snapshot.strike() == BigDecimal.valueOf(500)
      snapshot.bid() == 5.5
      snapshot.ask() == 6.0
      snapshot.impliedVol() == 0.18
      snapshot.delta() == 0.55
      snapshot.openInterest() == 15000
      snapshot.dayCountConvention() == DayCountConvention.ACT_365_FIXED
      snapshot.ttmYears() > 0
  }

  def "given put contract, when toCdm, then map to CDM with PUT type"() {
    given:
      def contract = new YahooOptionContract(
          Instant.now(), "QQQ", BigDecimal.valueOf(400), LocalDate.now().plusMonths(1),
          "PUT", 2.5, 3.0, 2.75, 0.19, -0.45, 0.02, -0.04, 0.28, -0.08, 12000, 402.0)

    when:
      def snapshot = adapter.toCdm(contract)

    then:
      snapshot.type() == OptionType.PUT
      snapshot.underlyingSymbol() == "QQQ"
  }

  def "given contract with bid > ask, when toCdm, then ask is corrected to bid"() {
    given:
      def contract = new YahooOptionContract(
          Instant.now(), "SPY", BigDecimal.valueOf(500), LocalDate.now().plusMonths(1),
          "CALL", 6.0, 5.0, 5.5, 0.2, 0.5, 0.02, -0.04, 0.25, 0.08, 10000, 503.0)

    when:
      def snapshot = adapter.toCdm(contract)

    then:
      snapshot.ask() >= snapshot.bid()
  }

  def "given expired contract, when toCdm, then ttmYears is zero"() {
    given:
      def contract = new YahooOptionContract(
          Instant.now(), "SPY", BigDecimal.valueOf(500), LocalDate.now().minusDays(1),
          "CALL", 0.01, 0.02, 0.01, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 503.0)

    when:
      def snapshot = adapter.toCdm(contract)

    then:
      snapshot.ttmYears() == 0.0
  }
}
