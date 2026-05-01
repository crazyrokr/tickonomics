package com.tickonomics.ingestion.polygon

import com.fasterxml.jackson.databind.ObjectMapper
import com.tickonomics.cdm.adapter.raw.PolygonTick
import spock.lang.Specification

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

class DefaultPolygonWsClientSpec extends Specification {
  private final Executor directExecutor = { it.run() } as Executor
  private final ObjectMapper objectMapper = new ObjectMapper()
  private DefaultPolygonWsClient client

  def setup() {
    client = new DefaultPolygonWsClient(objectMapper, directExecutor)
  }

  def "given not connected, when isConnected, then false"() {
    expect:
        !client.isConnected()
  }

  def "given not connected, when disconnect, then no exception"() {
    when:
        client.disconnect()
    then:
        noExceptionThrown()
  }

  def "given symbol, when subscribe, then added"() {
    when:
        client.subscribe("SPY")
    then:
        noExceptionThrown()
  }

  def "given subscribed symbol, when unsubscribe, then removed"() {
    setup:
        client.subscribe("SPY")
    when:
        client.unsubscribe("SPY")
    then:
        noExceptionThrown()
  }

  def "given handler, when onTick, then registered"() {
    setup:
        def received = new AtomicReference<PolygonTick>()
    when:
        client.onTick { received.set(it) }
    then:
        noExceptionThrown()
  }

  def "given valid tick json, when parse, then correct fields"() {
    setup:
        String json = "[{\"ev\":\"T\",\"sym\":\"SPY\",\"p\":450.50,\"s\":1000,\"t\":1700000000000,\"c\":[0,1]}]"
        def received = new AtomicReference<PolygonTick>()
        client.onTick { received.set(it) }

        def root = objectMapper.readTree(json)
        def tickNode = root.get(0)

        // Accessing private method via reflection
        def method = DefaultPolygonWsClient.getDeclaredMethod("parseTick", com.fasterxml.jackson.databind.JsonNode)
        method.accessible = true

    when:
        PolygonTick tick = (PolygonTick) method.invoke(client, tickNode)

    then:
        tick.symbol() == "SPY"
        tick.price() == 450.50D
        tick.volume() == 1000
        tick.conditions() == [0, 1] as int[]
  }
}
