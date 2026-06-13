package com.tickonomics.ingestion.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for {@code monitor.equity-price.*}. */
@ConfigurationProperties(prefix = "monitor.equity-price")
public record EquityPriceProperties(boolean enabled, long pollIntervalMs, List<String> symbols) {

  public EquityPriceProperties {
    MonitorValidation.requirePositive(pollIntervalMs, "pollIntervalMs");
    MonitorValidation.validateTickers(symbols, "symbols");
  }
}
