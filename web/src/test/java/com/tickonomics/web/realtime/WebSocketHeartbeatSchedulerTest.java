package com.tickonomics.web.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;

class WebSocketHeartbeatSchedulerTest {

  @Test
  @SuppressWarnings("unchecked")
  void givenHandlers_whenSendHeartbeats_thenEachHandlerPinged() {
    // Given two broadcast handlers
    BroadcastWebSocketHandler<String> priceHandler = mock(BroadcastWebSocketHandler.class);
    BroadcastWebSocketHandler<String> signalHandler = mock(BroadcastWebSocketHandler.class);
    var scheduler = new WebSocketHeartbeatScheduler(List.of(priceHandler, signalHandler));

    // When the scheduled heartbeat fires
    scheduler.sendHeartbeats();

    // Then every handler receives a ping
    verify(priceHandler).sendHeartbeat();
    verify(signalHandler).sendHeartbeat();
  }

  @Test
  void givenNoHandlers_whenSendHeartbeats_thenCompletesWithoutThrowing() {
    // Given a scheduler with no registered handlers
    var scheduler = new WebSocketHeartbeatScheduler(List.of());

    // When the scheduled heartbeat fires
    // Then it completes without throwing (defensive against an empty handler list)
    scheduler.sendHeartbeats();
  }
}
