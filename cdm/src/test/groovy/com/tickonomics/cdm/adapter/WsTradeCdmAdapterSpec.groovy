package com.tickonomics.cdm.adapter

import com.tickonomics.cdm.adapter.raw.FinnhubTrade
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class WsTradeCdmAdapterSpec extends Specification {

  @Subject
  WsTradeCdmAdapter adapter = new WsTradeCdmAdapter()

  def "given valid trade, when toCdm, then map to CdmTick"() {
    given:
      def trade = new FinnhubTrade(Instant.now(), "SPY", 503.5, 100L, 1.0)

    when:
      def tick = adapter.toCdm(trade)

    then:
      tick.symbol() == "SPY"
      tick.price() == 503.5
      tick.volume() == 100L
      tick.time() == trade.time()
      tick.conditions() == null
  }

  def "given trade with large volume, when toCdm, then preserve volume"() {
    given:
      def trade = new FinnhubTrade(Instant.now(), "QQQ", 402.0, 999999999L, 0)

    when:
      def tick = adapter.toCdm(trade)

    then:
      tick.volume() == 999999999L
  }
}
