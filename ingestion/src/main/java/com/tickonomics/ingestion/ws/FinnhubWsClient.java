package com.tickonomics.ingestion.ws;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tickonomics.cdm.adapter.raw.FinnhubTrade;
import com.tickonomics.contracts.client.EquityWsClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Finnhub WebSocket client for real-time equity trades. Replaces the paid Polygon WebSocket feed
 * with the free Finnhub alternative. Provides near-real-time trade data (~0.5s latency).
 *
 * <p>Reconnection uses exponential backoff (1s to configured max, default 60s).</p>
 */
@Component
@ConditionalOnProperty(name = "monitor.finnhub.ws-enabled", havingValue = "true", matchIfMissing = false)
public class FinnhubWsClient implements EquityWsClient {

  private static final Logger log = LoggerFactory.getLogger(FinnhubWsClient.class);

  private final StandardWebSocketClient wsClient;
  private final ObjectMapper objectMapper;
  private final String wsUrl;
  private final int reconnectBackoffMaxMs;
  private final ExecutorService asyncExecutor;
  private final ScheduledExecutorService reconnectScheduler;

  private volatile WebSocketSession session;
  private volatile int reconnectDelayMs = 1000;
  private volatile boolean intentionalDisconnect = false;

  private final Set<Consumer<FinnhubTrade>> handlers = new CopyOnWriteArraySet<>();
  private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();

  public FinnhubWsClient(
      ObjectMapper objectMapper,
      @Value("${monitor.finnhub.ws-url:wss://ws.finnhub.io}") String wsUrl,
      @Value("${monitor.finnhub.ws-reconnect-backoff-max:60000}") int reconnectBackoffMaxMs) {
    this.wsClient = new StandardWebSocketClient();
    this.objectMapper = objectMapper;
    this.wsUrl = wsUrl;
    this.reconnectBackoffMaxMs = reconnectBackoffMaxMs;
    this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      var t = new Thread(r, "finnhub-ws-reconnect");
      t.setDaemon(true);
      return t;
    });
  }

  @PreDestroy
  public void destroy() {
    reconnectScheduler.shutdownNow();
    asyncExecutor.close();
  }

  @Override
  public void connect(String apiKey) {
    intentionalDisconnect = false;
    reconnectDelayMs = 1000;
    doConnect(apiKey);
  }

  private void doConnect(String apiKey) {
    try {
      var handler = new FinnhubWsHandler(apiKey);
      wsClient.execute(handler, buildConnectUrl(wsUrl, apiKey))
          .whenComplete((webSocketSession, throwable) -> {
            if (throwable != null) {
              log.error("Finnhub WebSocket connection failed: {}", throwable.getMessage());
              scheduleReconnect(apiKey);
            }
          });
    } catch (Exception e) {
      log.error("Failed to initiate Finnhub WebSocket connection: {}", e.getMessage());
      scheduleReconnect(apiKey);
    }
  }

  /**
   * Builds the Finnhub WebSocket connect URL with the API token appended as a query parameter.
   * Finnhub requires {@code wss://ws.finnhub.io?token=API_KEY}; without it the server rejects the
   * handshake. Extracted as a static pure function for unit testing.
   */
  static String buildConnectUrl(String wsUrl, String apiKey) {
    String separator = wsUrl.contains("?") ? "&" : "?";
    return wsUrl + separator + "token=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
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
  public void onTrade(Consumer<FinnhubTrade> handler) {
    handlers.add(handler);
  }

  @Override
  public void disconnect() {
    intentionalDisconnect = true;
    if (session != null && session.isOpen()) {
      try {
        session.close(CloseStatus.NORMAL);
      } catch (IOException e) {
        log.error("Error closing Finnhub WebSocket: {}", e.getMessage());
      }
    }
  }

  @Override
  public boolean isConnected() {
    return session != null && session.isOpen();
  }

  private void sendSubscribe(String symbol) {
    sendMessage("""
        {"type":"subscribe","symbol":"%s"}""".formatted(symbol));
  }

  private void sendUnsubscribe(String symbol) {
    sendMessage("""
        {"type":"unsubscribe","symbol":"%s"}""".formatted(symbol));
  }

  private void sendMessage(String message) {
    if (session != null && session.isOpen()) {
      try {
        session.sendMessage(new TextMessage(message));
      } catch (IOException e) {
        log.error("Failed to send Finnhub WebSocket message: {}", e.getMessage());
      }
    }
  }

  private void processTextMessage(String payload) {
    for (FinnhubTrade trade : parseFinnhubMessage(payload)) {
      for (Consumer<FinnhubTrade> handler : handlers) {
        asyncExecutor.execute(() -> handler.accept(trade));
      }
    }
  }

  /**
   * Parses a Finnhub trade message payload into typed trades. Package-private for direct unit
   * testing without a live WebSocket.
   */
  List<FinnhubTrade> parseFinnhubMessage(String payload) {
    try {
      JsonNode root = objectMapper.readTree(payload);
      if (!root.has("type") || !"trade".equals(root.get("type").asText())) {
        return List.of();
      }
      JsonNode data = root.path("data");
      if (!data.isArray()) {
        return List.of();
      }
      List<FinnhubTrade> trades = new ArrayList<>();
      for (JsonNode trade : data) {
        trades.add(parseTrade(trade));
      }
      return trades;
    } catch (JacksonException e) {
      log.error("Failed to parse Finnhub message: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Exponential backoff step (doubles the delay, capped at {@code maxDelayMs}). Extracted for unit
   * testing the 1s {@code ->} 60s reconnect schedule.
   */
  static int computeNextDelay(int currentDelayMs, int maxDelayMs) {
    return Math.min(currentDelayMs * 2, maxDelayMs);
  }

  private FinnhubTrade parseTrade(JsonNode trade) {
    Instant time = Instant.ofEpochMilli(trade.path("t").asLong());
    String symbol = trade.path("s").asText();
    double price = trade.path("p").asDouble();
    long volume = trade.path("v").asLong();
    double tickVolume = trade.path("x").asDouble(0);
    return new FinnhubTrade(time, symbol, price, volume, tickVolume);
  }

  private void scheduleReconnect(String apiKey) {
    if (intentionalDisconnect) {
      return;
    }
    log.info("Scheduling Finnhub WebSocket reconnect in {}ms", reconnectDelayMs);
    reconnectScheduler.schedule(() -> doConnect(apiKey), reconnectDelayMs, TimeUnit.MILLISECONDS);
    reconnectDelayMs = computeNextDelay(reconnectDelayMs, reconnectBackoffMaxMs);
  }

  class FinnhubWsHandler extends TextWebSocketHandler {

    private final String apiKey;

    FinnhubWsHandler(String apiKey) {
      this.apiKey = apiKey;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) {
      session = webSocketSession;
      reconnectDelayMs = 1000;
      log.info("Finnhub WebSocket connected");
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
      log.warn("Finnhub WebSocket closed: {}", status);
      session = null;
      scheduleReconnect(apiKey);
    }

    @Override
    public void handleTransportError(WebSocketSession webSocketSession, Throwable exception) {
      log.error("Finnhub WebSocket transport error: {}", exception.getMessage());
    }
  }
}
