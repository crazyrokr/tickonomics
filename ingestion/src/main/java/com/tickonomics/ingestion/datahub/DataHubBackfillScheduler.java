package com.tickonomics.ingestion.datahub;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for DataHub historical CSV backfill. Performs a full load on application startup when
 * target tables are empty, then checks monthly for incremental updates.
 */
@Component
@ConditionalOnProperty(name = "monitor.datahub.enabled", havingValue = "true", matchIfMissing = false)
public class DataHubBackfillScheduler {

  private static final Logger log = LoggerFactory.getLogger(DataHubBackfillScheduler.class);

  private final DataHubBackfillClient client;

  @Value("${monitor.datahub.startup-full-load:true}")
  private boolean startupFullLoad;

  public DataHubBackfillScheduler(DataHubBackfillClient client) {
    this.client = client;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    if (startupFullLoad) {
      log.info("Starting DataHub full backfill on startup");
      try {
        client.backfillAll();
        log.info("DataHub full backfill completed");
      } catch (Exception e) {
        log.error("DataHub full backfill failed: {}", e.getMessage());
      }
    }
  }

  @Scheduled(cron = "${monitor.datahub.incremental-check-cron:0 0 2 1 * *}")
  @Bulkhead(name = "criticalIngestion")
  public void incrementalCheck() {
    log.info("Running DataHub incremental monthly check");
    try {
      client.backfillAll();
    } catch (Exception e) {
      log.error("DataHub incremental check failed: {}", e.getMessage());
    }
  }
}
