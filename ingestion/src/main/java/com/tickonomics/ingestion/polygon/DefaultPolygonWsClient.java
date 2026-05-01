package com.tickonomics.ingestion.polygon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickonomics.cdm.adapter.raw.PolygonTick;
import com.tickonomics.contracts.client.PolygonWsClient;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class DefaultPolygonWsClient implements PolygonWsClient {

  private static final Logger log = LoggerFactory.getLogger(DefaultPolygonWsClient.class);
  private static final String WS_URL = "wss://socket.polygon.io/stocks";

  private final StandardWebSocketClient wsClient;
  private final ObjectMapper objectMapper;
  private final Executor asyncExecutor;

  private volatile WebSocketSession session;
  private final Set<Consumer<PolygonTick>> handlers = new CopyOnWriteArraySet<>();
  private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();

  public DefaultPolygonWsClient(ObjectMapper objectMapper, Executor asyncExecutor) {
    this.wsClient = new StandardWebSocketClient();
    this.objectMapper = objectMapper;
    this.asyncExecutor = asyncExecutor;
  }

  @Override
  public void connect(String apiKey) {
    try {
      var handler = new PolygonWsHandler(apiKey);
      wsClient
          .execute(handler, WS_URL)
          .whenComplete((webSocketSession, throwable) -> {
            if (throwable != null) {
              log.error("WebSocket connection failed: {}", throwable.getMessage());
            }
          });
    } catch (Exception e) {
      log.error("Failed to initiate WebSocket connection: {}", e.getMessage());
    }
  }

  @Override
  public void subscribe(String symbol) {
    subscribedSymbols.add(symbol);
    if (session != null && session.isOpen()) {
      sendSubscribe(symbol);
    }
  }

  @Override
  public void unsubscribe(String symbol) {
    subscribedSymbols.remove(symbol);
    if (session != null && session.isOpen()) {
      sendUnsubscribe(symbol);
    }
  }

  @Override
  public void onTick(Consumer<PolygonTick> handler) {
    handlers.add(handler);
  }

  @Override
  public void disconnect() {
    if (session != null && session.isOpen()) {
      try {
        session.close(CloseStatus.NORMAL);
      } catch (IOException e) {
        log.error("Error closing WebSocket: {}", e.getMessage());
      }
    }
  }

  @Override
  public boolean isConnected() {
    return session != null && session.isOpen();
  }

  private void sendSubscribe(String symbol) {
    sendMessage("{\"action\":\"subscribe\",\"params\":\"T." + symbol + "\"}");
  }

  private void sendUnsubscribe(String symbol) {
    sendMessage("{\"action\":\"unsubscribe\",\"params\":\"T." + symbol + "\"}");
  }

  private void sendMessage(String message) {
    if (session != null && session.isOpen()) {
      try {
        session.sendMessage(new TextMessage(message));
      } catch (IOException e) {
        log.error("Failed to send WebSocket message: {}", e.getMessage());
      }
    }
  }

  private void processTextMessage(String payload) {
    try {
      JsonNode root = objectMapper.readTree(payload);
      if (root.isArray()) {
        for (JsonNode event : root) {
          String ev = event.has("ev") ? event
              .get("ev")
              .asText() : "";
          if ("T".equals(ev)) {
            var tick = parseTick(event);
            for (Consumer<PolygonTick> handler : handlers) {
              asyncExecutor.execute(() -> handler.accept(tick));
            }
          } else if ("status".equals(ev)) {
            log.debug("Polygon status: {}", event);
          }
        }
      }
    } catch (JsonProcessingException e) {
      log.error("Failed to parse Polygon message: {}", e.getMessage());
    }
  }

  private PolygonTick parseTick(JsonNode event) {
    Instant time = Instant.ofEpochMilli(event
        .path("t")
        .asLong());
    String symbol = event
        .path("sym")
        .asText();
    double price = event
        .path("p")
        .asDouble();
    long size = event
        .path("s")
        .asLong();
    JsonNode conditionsNode = event.path("c");
    int[] conditions;
    if (conditionsNode.isArray()) {
      conditions = new int[conditionsNode.size()];
      for (int i = 0; i < conditionsNode.size(); i++) {
        conditions[i] = conditionsNode
            .get(i)
            .asInt();
      }
    } else {
      conditions = new int[]{};
    }
    return new PolygonTick(time, symbol, price, size, conditions);
  }

  class PolygonWsHandler extends TextWebSocketHandler {

    private final String apiKey;

    PolygonWsHandler(String apiKey) {
      this.apiKey = apiKey;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) {
      session = webSocketSession;
      log.info("Polygon WebSocket connected");
      sendMessage("{\"action\":\"auth\",\"params\":\"" + apiKey + "\"}");
      for (String symbol : subscribedSymbols) {
        sendSubscribe(symbol);
      }
    }

    @Override
    protected void handleTextMessage(WebSocketSession webSocketSession, TextMessage message) {
      processTextMessage(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status) {
      log.warn("Polygon WebSocket closed: {}", status);
      session = null;
    }

    @Override
    public void handleTransportError(WebSocketSession webSocketSession, Throwable exception) {
      log.error("Polygon WebSocket transport error: {}", exception.getMessage());
    }
  }
}
