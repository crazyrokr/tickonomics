package com.tickonomics.cdm.adapter

import com.tickonomics.cdm.adapter.raw.FinnhubQuote
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class FinnhubEquityCdmAdapterSpec extends Specification {

  @Subject
  FinnhubEquityCdmAdapter adapter = new FinnhubEquityCdmAdapter()

  def "given valid quote, when toCdm, then map current price to tick"() {
    given:
      def quote = new FinnhubQuote(Instant.now(), "SPY", 503.5, 508.0, 500.0, 501.0, 499.0, 150000L)

    when:
      def tick = adapter.toCdm(quote)

    then:
      tick.symbol() == "SPY"
      tick.price() == 503.5
      tick.volume() == 150000L
      tick.time() == quote.time()
      tick.conditions() == null
  }

  def "given quote with zero volume, when toCdm, then tick has zero volume"() {
    given:
      def quote = new FinnhubQuote(Instant.now(), "QQQ", 402.0, 405.0, 398.0, 400.0, 399.0, 0L)

    when:
      def tick = adapter.toCdm(quote)

    then:
      tick.volume() == 0L
      tick.price() == 402.0
  }
}
