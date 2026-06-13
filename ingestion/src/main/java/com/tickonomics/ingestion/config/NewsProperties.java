package com.tickonomics.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for {@code monitor.news.*}. */
@ConfigurationProperties(prefix = "monitor.news")
public record NewsProperties(boolean enabled, long pollIntervalMs) {

  public NewsProperties {
    MonitorValidation.requirePositive(pollIntervalMs, "pollIntervalMs");
  }
}
