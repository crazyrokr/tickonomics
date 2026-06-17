package com.tickonomics.ingestion.ws

import tools.jackson.databind.ObjectMapper
import com.tickonomics.cdm.adapter.raw.FinnhubTrade
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class FinnhubWsClientSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  @Subject
  FinnhubWsClient client

  def setup() {
    client = new FinnhubWsClient(objectMapper, "wss://ws.finnhub.io", 60000)
  }

  def "given connected, when subscribe, then symbol is tracked"() {
    when:
      client.subscribe("SPY")

    then:
      client.subscribedSymbols.contains("SPY")
  }

  def "given subscribed, when unsubscribe, then symbol is removed"() {
    given:
      client.subscribe("SPY")

    when:
      client.unsubscribe("SPY")

    then:
      !client.subscribedSymbols.contains("SPY")
  }

  def "given not connected, when isConnected, then return false"() {
    expect:
      !client.isConnected()
  }

  def "given intentional disconnect, when disconnect, then no reconnect"() {
    when:
      client.disconnect()

    then:
      client.intentionalDisconnect
  }

  def "given valid trade JSON, when processTextMessage, then handler receives trade"() {
    given:
      def json = '''{"type":"trade","data":[{"s":"SPY","p":503.5,"t":1716422400000,"v":100,"x":1}]}'''
      def latch = new CountDownLatch(1)
      FinnhubTrade received = null
      Consumer<FinnhubTrade> handler = { t -> received = t; latch.countDown() }
      client.onTrade(handler)

    when:
      client.processTextMessage(json)
      latch.await(2, TimeUnit.SECONDS)

    then:
      received != null
      received.symbol() == "SPY"
      received.price() == 503.5
      received.volume() == 100L
  }

  def "given non-trade message, when processTextMessage, then no handler invocation"() {
    given:
      def json = '{"type":"ping"}'
      FinnhubTrade received = null
      Consumer<FinnhubTrade> handler = { t -> received = t }
      client.onTrade(handler)

    when:
      client.processTextMessage(json)
      Thread.sleep(100)

    then:
      received == null
  }

  def "given multiple trades in one message, when processTextMessage, then all are dispatched"() {
    given:
      def json = '''{"type":"trade","data":[
        {"s":"SPY","p":503.5,"t":1716422400000,"v":100,"x":1},
        {"s":"QQQ","p":402.0,"t":1716422401000,"v":200,"x":2}
      ]}'''
      def latch = new CountDownLatch(2)
      List<FinnhubTrade> received = Collections.synchronizedList([])
      Consumer<FinnhubTrade> handler = { t -> received.add(t); latch.countDown() }
      client.onTrade(handler)

    when:
      client.processTextMessage(json)
      latch.await(2, TimeUnit.SECONDS)

    then:
      received.size() == 2
      received.collect { it.symbol() }.toSet() == ["SPY", "QQQ"].toSet()
  }

  def "given malformed JSON, when processTextMessage, then no exception thrown"() {
    when:
      client.processTextMessage("not valid json{{{")

    then:
      noExceptionThrown()
  }

  def "given reconnect delay, when computeNextDelay, then doubles and caps at max"() {
    expect:
      FinnhubWsClient.computeNextDelay(1000, 60000) == 2000
      FinnhubWsClient.computeNextDelay(32000, 60000) == 60000
      FinnhubWsClient.computeNextDelay(30000, 60000) == 60000
  }

  def "given ws url and api key, when buildConnectUrl, then token appended as query param"() {
    expect:
      FinnhubWsClient.buildConnectUrl("wss://ws.finnhub.io", "abc123") ==
          "wss://ws.finnhub.io?token=abc123"
  }

  def "given ws url with existing query, when buildConnectUrl, then token appended with ampersand"() {
    expect:
      FinnhubWsClient.buildConnectUrl("wss://ws.finnhub.io?foo=bar", "abc123") ==
          "wss://ws.finnhub.io?foo=bar&token=abc123"
  }

  def "given api key with special characters, when buildConnectUrl, then url-encoded"() {
    expect:
      FinnhubWsClient.buildConnectUrl("wss://ws.finnhub.io", "a b&c") ==
          "wss://ws.finnhub.io?token=a+b%26c"
  }
}
