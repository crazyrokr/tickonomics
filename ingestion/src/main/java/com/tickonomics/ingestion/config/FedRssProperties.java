package com.tickonomics.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for {@code monitor.fed-rss.*}. */
@ConfigurationProperties(prefix = "monitor.fed-rss")
public record FedRssProperties(boolean enabled, String speechesUrl, String fomcUrl, long pollIntervalMs) {

  public FedRssProperties {
    MonitorValidation.requirePositive(pollIntervalMs, "pollIntervalMs");
  }
}
