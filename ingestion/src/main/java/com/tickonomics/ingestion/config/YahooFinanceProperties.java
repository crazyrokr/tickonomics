package com.tickonomics.ingestion.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for {@code monitor.yahoo-finance.*}. */
@ConfigurationProperties(prefix = "monitor.yahoo-finance")
public record YahooFinanceProperties(boolean enabled, String baseUrl, Duration connectTimeout, Duration readTimeout) {

  public YahooFinanceProperties {
    MonitorValidation.requirePositive(connectTimeout, "connectTimeout");
    MonitorValidation.requirePositive(readTimeout, "readTimeout");
  }
}
