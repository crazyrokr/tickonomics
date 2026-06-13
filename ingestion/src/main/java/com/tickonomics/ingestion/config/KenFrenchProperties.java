package com.tickonomics.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for {@code monitor.ken-french.*}. */
@ConfigurationProperties(prefix = "monitor.ken-french")
public record KenFrenchProperties(boolean enabled, String baseUrl, long pollIntervalMs) {

  public KenFrenchProperties {
    MonitorValidation.requirePositive(pollIntervalMs, "pollIntervalMs");
  }
}
