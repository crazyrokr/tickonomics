package com.tickonomics.web.realtime

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import spock.lang.Specification

import java.time.Instant

class PriceWebSocketHandlerSpec extends Specification {

  private ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()

  def "broadcast sends serialized tick to each open subscriber"() {
    given: "a handler with one open subscriber"
    def handler = new PriceWebSocketHandler(mapper)
    def session = Mock(WebSocketSession)
    session.getId() >> "s1"
    session.isOpen() >> true
    handler.afterConnectionEstablished(session)

    when: "a tick is broadcast"
    handler.broadcast(PriceTick.of("SPY", 500.25, Instant.parse("2026-06-13T12:00:00Z")))

    then: "the subscriber receives a JSON message containing the symbol and price"
    1 * session.sendMessage({ TextMessage message ->
      message.payload.contains('"symbol":"SPY"') && message.payload.contains('"price":500.25')
    })
  }

  def "broadcast is a no-op when no subscribers are connected"() {
    given: "a handler with no subscribers"
    def handler = new PriceWebSocketHandler(mapper)

    when: "a tick is broadcast"
    handler.broadcast(PriceTick.of("SPY", 1.0, Instant.EPOCH))

    then: "no exception is thrown and nothing is sent"
    noExceptionThrown()
    handler.subscriberCount() == 0
  }

  def "afterConnectionClosed removes the subscriber from the broadcast set"() {
    given: "a handler with one subscriber"
    def handler = new PriceWebSocketHandler(mapper)
    def session = Mock(WebSocketSession)
    session.getId() >> "s1"
    handler.afterConnectionEstablished(session)
    assert handler.subscriberCount() == 1

    when: "the subscriber disconnects"
    handler.afterConnectionClosed(session, CloseStatus.NORMAL)

    then: "the subscriber is no longer tracked"
    handler.subscriberCount() == 0

    and: "a subsequent broadcast reaches nobody"
    handler.broadcast(PriceTick.of("SPY", 2.0, Instant.EPOCH))
    0 * session.sendMessage(_)
  }
}
