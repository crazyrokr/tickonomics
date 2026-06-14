package com.tickonomics.web.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Broadcasts {@link PriceTick} messages to subscribers of the {@code /ws/prices} channel. */
@Component
public class PriceWebSocketHandler extends BroadcastWebSocketHandler<PriceTick> {

  public PriceWebSocketHandler(ObjectMapper objectMapper) {
    super(objectMapper);
  }
}
