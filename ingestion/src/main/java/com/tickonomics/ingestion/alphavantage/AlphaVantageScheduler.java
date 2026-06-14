package com.tickonomics.ingestion.alphavantage;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled polling coordinator for Alpha Vantage data. Runs daily at 06:00 UTC (after US market
 * close). On first run, performs a full 20+ year historical load; subsequent runs use compact mode
 * (last 100 data points) for incremental updates. Rate-limited to respect the 5 req/min free tier.
 */
@Component
@ConditionalOnProperty(name = "monitor.alpha-vantage.enabled", havingValue = "true", matchIfMissing = false)
public class AlphaVantageScheduler {

  private static final Logger log = LoggerFactory.getLogger(AlphaVantageScheduler.class);

  private final AlphaVantageClient client;

  @Value("${monitor.alpha-vantage.initial-full-load:true}")
  private boolean initialFullLoad;

  private boolean firstRun = true;

  public AlphaVantageScheduler(AlphaVantageClient client) {
    this.client = client;
  }

  @Scheduled(fixedDelayString = "${monitor.alpha-vantage.poll-interval-ms:86400000}")
  @Bulkhead(name = "highVolumeIngestion")
  public void pollAlphaVantage() {
    boolean fullOutput = firstRun && initialFullLoad;

    for (String symbol : client.getSymbols()) {
      try {
        client.fetchAndWriteSymbol(symbol, fullOutput);
      } catch (Exception e) {
        log.error("Alpha Vantage fetch failed for symbol {}: {}", symbol, e.getMessage());
      }
    }

    for (String commodity : client.getCommodities()) {
      try {
        client.fetchAndWriteCommodity(commodity);
      } catch (Exception e) {
        log.error("Alpha Vantage fetch failed for commodity {}: {}", commodity, e.getMessage());
      }
    }

    if (firstRun) {
      firstRun = false;
      log.info("Alpha Vantage initial full load completed");
    }
  }
}
