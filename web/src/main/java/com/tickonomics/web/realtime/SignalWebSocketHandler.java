package com.tickonomics.web.realtime;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Broadcasts {@link SignalNotification} messages to subscribers of the {@code /ws/signals} channel. */
@Component
public class SignalWebSocketHandler extends BroadcastWebSocketHandler<SignalNotification> {

  public SignalWebSocketHandler(ObjectMapper objectMapper) {
    super(objectMapper);
  }
}
