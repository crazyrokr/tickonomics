package com.tickonomics.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for {@code monitor.ingestion.*} (last-known-good cache and bulkhead cadence). */
@ConfigurationProperties(prefix = "monitor.ingestion")
public record IngestionMonitorProperties(LkgCache lkgCache, Bulkhead bulkhead) {

  public IngestionMonitorProperties {
    if (lkgCache == null) {
      lkgCache = new LkgCache(0);
    }
    if (bulkhead == null) {
      bulkhead = new Bulkhead(0);
    }
  }

  public record LkgCache(long maxStalenessSeconds) {
    public LkgCache {
      MonitorValidation.requirePositive(maxStalenessSeconds, "maxStalenessSeconds");
    }
  }

  public record Bulkhead(long pressureCheckMs) {
    public Bulkhead {
      MonitorValidation.requirePositive(pressureCheckMs, "pressureCheckMs");
    }
  }
}
