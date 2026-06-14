package com.tickonomics.ingestion.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for {@code monitor.finnhub.*}. */
@ConfigurationProperties(prefix = "monitor.finnhub")
public record FinnhubProperties(
    String apiKey,
    boolean restEnabled,
    String restUrl,
    boolean wsEnabled,
    String wsUrl,
    long wsReconnectBackoffMax,
    List<String> symbols,
    boolean newsEnabled,
    Duration connectTimeout,
    Duration readTimeout) {

  public FinnhubProperties {
    MonitorValidation.requirePositive(wsReconnectBackoffMax, "wsReconnectBackoffMax");
    MonitorValidation.validateTickers(symbols, "symbols");
    MonitorValidation.requirePositive(connectTimeout, "connectTimeout");
    MonitorValidation.requirePositive(readTimeout, "readTimeout");
  }
}
