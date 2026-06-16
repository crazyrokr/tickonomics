package com.tickonomics.web.realtime

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import spock.lang.Specification

class SignalWebSocketHandlerSpec extends Specification {

  private ObjectMapper mapper = JsonMapper.builder().build()

  def "broadcast sends serialized signal notification to each open subscriber"() {
    given: "a handler with one open subscriber"
    def handler = new SignalWebSocketHandler(mapper)
    def session = Mock(WebSocketSession)
    session.getId() >> "s1"
    session.isOpen() >> true
    handler.afterConnectionEstablished(session)
    def notification = SignalNotification.of(
        UUID.fromString("00000000-0000-0000-0000-000000000001"), "QQQ", "LONG")

    when: "the signal is broadcast"
    handler.broadcast(notification)

    then: "the subscriber receives a JSON message containing the symbol and direction"
    1 * session.sendMessage({ TextMessage message ->
      message.payload.contains('"symbol":"QQQ"') && message.payload.contains('"direction":"LONG"')
    })
  }

  def "broadcast is a no-op when no subscribers are connected"() {
    given: "a handler with no subscribers"
    def handler = new SignalWebSocketHandler(mapper)

    when: "a signal is broadcast"
    handler.broadcast(SignalNotification.of(
        UUID.fromString("00000000-0000-0000-0000-000000000002"), "SPY", "NEUTRAL"))

    then: "no exception is thrown"
    noExceptionThrown()
  }
}
