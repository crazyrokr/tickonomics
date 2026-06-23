package com.tickonomics.web.realtime;

import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends periodic WebSocket pings to every connected subscriber so idle connections are kept alive
 * and half-open sockets are detected. Ping cadence is configurable via
 * {@code websocket.heartbeat-interval-ms} (default 30s).
 */
@Component
public class WebSocketHeartbeatScheduler {

  private final List<BroadcastWebSocketHandler<?>> handlers;

  public WebSocketHeartbeatScheduler(List<BroadcastWebSocketHandler<?>> handlers) {
    this.handlers = handlers;
  }

  @Scheduled(fixedDelayString = "${websocket.heartbeat-interval-ms:30000}")
  public void sendHeartbeats() {
    for (BroadcastWebSocketHandler<?> handler : handlers) {
      handler.sendHeartbeat();
    }
  }
}
