package com.tickonomics.cdm.adapter

import com.tickonomics.cdm.adapter.raw.AlphaVantageDailyBar
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class AlphaVantageCdmAdapterSpec extends Specification {

  @Subject
  AlphaVantageCdmAdapter adapter = new AlphaVantageCdmAdapter()

  def "given valid daily bar, when toCdm, then map adjusted close to tick"() {
    given:
      def bar = new AlphaVantageDailyBar(Instant.now(), "SPY",
          500.0, 505.0, 498.0, 503.0, 503.5, 10000000L, 0.0, 1.0)

    when:
      def tick = adapter.toCdm(bar)

    then:
      tick.symbol() == "SPY"
      tick.price() == 503.5
      tick.volume() == 10000000L
      tick.conditions() == null
  }

  def "given commodity bar with same value for all OHLC, when toCdm, then use adjusted close"() {
    given:
      def bar = new AlphaVantageDailyBar(Instant.now(), "GOLD",
          2345.6, 2345.6, 2345.6, 2345.6, 2345.6, 0L, 0.0, 1.0)

    when:
      def tick = adapter.toCdm(bar)

    then:
      tick.symbol() == "GOLD"
      tick.price() == 2345.6
      tick.volume() == 0L
  }
}
