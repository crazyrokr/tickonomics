package com.tickonomics.web.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class BroadcastWebSocketHandlerHeartbeatTest {

  /** Concrete subclass under test; the base class is abstract. */
  static final class TestHandler extends BroadcastWebSocketHandler<String> {
    TestHandler(ObjectMapper objectMapper) {
      super(objectMapper);
    }
  }

  @Nested
  class SendHeartbeat {

    @Test
    void givenOpenSubscriber_whenSendHeartbeat_thenPingSent() throws Exception {
      // Given a handler with one open subscriber
      var handler = new TestHandler(mock(ObjectMapper.class));
      WebSocketSession session = mock(WebSocketSession.class);
      when(session.getId()).thenReturn("s1");
      when(session.isOpen()).thenReturn(true);
      handler.afterConnectionEstablished(session);

      // When the heartbeat fires
      handler.sendHeartbeat();

      // Then a ping is sent to the open session
      verify(session).sendMessage(any(PingMessage.class));
      org.junit.jupiter.api.Assertions.assertEquals(1, handler.subscriberCount());
    }

    @Test
    void givenNoSubscribers_whenSendHeartbeat_thenNoOp() throws Exception {
      var handler = new TestHandler(mock(ObjectMapper.class));

      handler.sendHeartbeat();

      // Then nothing throws and subscriber count stays zero
      org.junit.jupiter.api.Assertions.assertEquals(0, handler.subscriberCount());
    }

    @Test
    void givenWriteFails_whenSendHeartbeat_thenDeadSessionEvicted() throws Exception {
      // Given a subscriber whose ping write fails
      var handler = new TestHandler(mock(ObjectMapper.class));
      WebSocketSession session = mock(WebSocketSession.class);
      when(session.getId()).thenReturn("s1");
      when(session.isOpen()).thenReturn(true);
      doThrow(new IOException("broken pipe")).when(session).sendMessage(any(PingMessage.class));
      handler.afterConnectionEstablished(session);

      // When the heartbeat fires
      handler.sendHeartbeat();

      // Then the dead session is evicted
      org.junit.jupiter.api.Assertions.assertEquals(0, handler.subscriberCount());
    }

    @Test
    void givenClosedSession_whenSendHeartbeat_thenNoSendAttempted() throws Exception {
      var handler = new TestHandler(mock(ObjectMapper.class));
      WebSocketSession session = mock(WebSocketSession.class);
      when(session.getId()).thenReturn("s1");
      when(session.isOpen()).thenReturn(false);
      handler.afterConnectionEstablished(session);

      handler.sendHeartbeat();

      verify(session, never()).sendMessage(any());
    }
  }
}
