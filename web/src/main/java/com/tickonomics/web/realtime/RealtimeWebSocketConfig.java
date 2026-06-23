package com.tickonomics.web.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the real-time WebSocket channels declared in {@code asyncapi.yaml}: {@code /ws/prices}
 * and {@code /ws/signals}. Allowed origins reuse the REST CORS allow-list
 * ({@code security.cors.allowed-origins}) so the WebSocket surface is no wider than HTTP.
 */
@Configuration
@EnableWebSocket
public class RealtimeWebSocketConfig implements WebSocketConfigurer {

  private final PriceWebSocketHandler priceHandler;
  private final SignalWebSocketHandler signalHandler;
  private final String[] allowedOrigins;

  public RealtimeWebSocketConfig(
      PriceWebSocketHandler priceHandler,
      SignalWebSocketHandler signalHandler,
      @Value("${security.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
      String[] allowedOrigins) {
    this.priceHandler = priceHandler;
    this.signalHandler = signalHandler;
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(priceHandler, "/ws/prices").setAllowedOrigins(allowedOrigins);
    registry.addHandler(signalHandler, "/ws/signals").setAllowedOrigins(allowedOrigins);
  }
}
