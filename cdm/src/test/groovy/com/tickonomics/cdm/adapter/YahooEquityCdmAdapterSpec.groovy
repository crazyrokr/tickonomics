package com.tickonomics.cdm.adapter

import com.tickonomics.cdm.adapter.raw.YahooOhlcv
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class YahooEquityCdmAdapterSpec extends Specification {

  @Subject
  YahooEquityCdmAdapter adapter = new YahooEquityCdmAdapter()

  def "given valid OHLCV, when toCdm, then map close price to tick"() {
    given:
      def ohlcv = new YahooOhlcv(Instant.now(), "SPY", 500.0, 505.0, 498.0, 503.0, 100000L)

    when:
      def tick = adapter.toCdm(ohlcv)

    then:
      tick.symbol() == "SPY"
      tick.price() == 503.0
      tick.volume() == 100000L
      tick.time() == ohlcv.time()
      tick.conditions() == null
  }

  def "given zero volume, when toCdm, then tick has zero volume"() {
    given:
      def ohlcv = new YahooOhlcv(Instant.now(), "QQQ", 400.0, 405.0, 398.0, 402.0, 0L)

    when:
      def tick = adapter.toCdm(ohlcv)

    then:
      tick.volume() == 0L
      tick.price() == 402.0
  }
}
