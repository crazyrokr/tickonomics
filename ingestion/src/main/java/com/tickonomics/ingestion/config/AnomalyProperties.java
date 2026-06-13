package com.tickonomics.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for {@code monitor.anomaly.*}. */
@ConfigurationProperties(prefix = "monitor.anomaly")
public record AnomalyProperties(boolean enabled, long asyncTimeoutSeconds, boolean fallbackToThreshold) {

  public AnomalyProperties {
    MonitorValidation.requirePositive(asyncTimeoutSeconds, "asyncTimeoutSeconds");
  }
}
