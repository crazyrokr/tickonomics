package com.tickonomics.web.realtime;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Base handler that tracks connected subscribers and broadcasts serialized payloads to all of
 * them. Subclasses bind a payload type and are registered under a specific channel path.
 *
 * @param <T> the payload type broadcast on the channel
 */
public abstract class BroadcastWebSocketHandler<T> extends TextWebSocketHandler {

  private final ObjectMapper objectMapper;
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  protected BroadcastWebSocketHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.put(session.getId(), session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session.getId());
  }

  public void broadcast(T payload) {
    if (sessions.isEmpty()) {
      return;
    }
    TextMessage message = new TextMessage(serialize(payload));
    for (WebSocketSession session : sessions.values()) {
      send(session, message);
    }
  }

  public int subscriberCount() {
    return sessions.size();
  }

  /**
   * Sends a WebSocket ping to every connected subscriber. Drives the heartbeat scheduled by
   * {@link WebSocketHeartbeatScheduler}; dead sessions are evicted when the ping write fails.
   */
  public void sendHeartbeat() {
    if (sessions.isEmpty()) {
      return;
    }
    PingMessage ping = new PingMessage();
    for (WebSocketSession session : sessions.values()) {
      try {
        synchronized (session) {
          if (session.isOpen()) {
            session.sendMessage(ping);
          }
        }
      } catch (IOException e) {
        sessions.remove(session.getId());
      }
    }
  }

  private String serialize(T payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialize broadcast payload", e);
    }
  }

  private void send(WebSocketSession session, TextMessage message) {
    try {
      synchronized (session) {
        if (session.isOpen()) {
          session.sendMessage(message);
        }
      }
    } catch (IOException e) {
      sessions.remove(session.getId());
    }
  }
}
