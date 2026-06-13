package com.tickonomics.ingestion.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for {@code monitor.alpha-vantage.*}. Commodity identifiers are not ticker-validated. */
@ConfigurationProperties(prefix = "monitor.alpha-vantage")
public record AlphaVantageProperties(
    boolean enabled,
    String apiKey,
    String baseUrl,
    long pollIntervalMs,
    List<String> symbols,
    List<String> commodities,
    long rateLimitDelayMs,
    boolean initialFullLoad) {

  public AlphaVantageProperties {
    MonitorValidation.requirePositive(pollIntervalMs, "pollIntervalMs");
    MonitorValidation.validateTickers(symbols, "symbols");
    MonitorValidation.requirePositive(rateLimitDelayMs, "rateLimitDelayMs");
  }
}
