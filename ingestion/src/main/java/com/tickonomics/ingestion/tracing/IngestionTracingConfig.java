package com.tickonomics.ingestion.tracing;

import org.springframework.context.annotation.Configuration;

/**
 * Anchor for the OpenTelemetry span names emitted across the ingestion pipeline. The span
 * lifecycle itself is managed by {@link IngestionTracer}; this class is the canonical registry of
 * the seven operations traced end-to-end: FRED/NY-Fed fetch, Finnhub WS message, TimescaleDB
 * write, quality check, anomaly detection, and disaster alert poll.
 */
@Configuration
public class IngestionTracingConfig {

  public static final String SPAN_FRED_FETCH = "ingestion.fred.fetch";
  public static final String SPAN_NYFED_FETCH = "ingestion.nyfed.fetch";
  public static final String SPAN_FINNHUB_MESSAGE = "ingestion.finnhub.message";
  public static final String SPAN_TIMESCALEDB_WRITE = "ingestion.timescaledb.write";
  public static final String SPAN_QUALITY_CHECK = "ingestion.quality.check";
  public static final String SPAN_ANOMALY_DETECT = "ingestion.anomaly.detect";
  public static final String SPAN_DISASTER_POLL = "ingestion.disaster.poll";
  public static final String SPAN_POLYMARKET_FETCH = "ingestion.polymarket.fetch";
  public static final String SPAN_OSINT_FETCH = "ingestion.osint.fetch";
}
