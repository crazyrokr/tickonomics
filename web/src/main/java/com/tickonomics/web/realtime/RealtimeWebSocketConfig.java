package com.tickonomics.web.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the real-time WebSocket channels declared in {@code asyncapi.yaml}: {@code /ws/prices}
 * and {@code /ws/signals}. Origins are left open for the browser dashboard; production deployments
 * should restrict the allowed origin patterns.
 */
@Configuration
@EnableWebSocket
public class RealtimeWebSocketConfig implements WebSocketConfigurer {

  private final PriceWebSocketHandler priceHandler;
  private final SignalWebSocketHandler signalHandler;

  public RealtimeWebSocketConfig(PriceWebSocketHandler priceHandler, SignalWebSocketHandler signalHandler) {
    this.priceHandler = priceHandler;
    this.signalHandler = signalHandler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(priceHandler, "/ws/prices").setAllowedOriginPatterns("*");
    registry.addHandler(signalHandler, "/ws/signals").setAllowedOriginPatterns("*");
  }
}
