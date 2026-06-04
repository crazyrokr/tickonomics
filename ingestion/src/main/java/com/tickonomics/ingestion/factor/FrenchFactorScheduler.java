package com.tickonomics.ingestion.factor;

import com.tickonomics.cdm.enums.FactorSet;
import com.tickonomics.cdm.model.FactorReturn;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled polling coordinator for Ken French factor data. Polls monthly (1st of month) for
 * updated factor return datasets from the Ken French Data Library.
 */
@Component
@ConditionalOnProperty(name = "monitor.ken-french.enabled", havingValue = "true", matchIfMissing = false)
public class FrenchFactorScheduler {

  private static final Logger log = LoggerFactory.getLogger(FrenchFactorScheduler.class);

  private final FrenchFactorClient client;

  public FrenchFactorScheduler(FrenchFactorClient client) {
    this.client = client;
  }

  @Scheduled(fixedDelayString = "${monitor.ken-french.poll-interval-ms:86400000}")
  @Bulkhead(name = "criticalIngestion")
  public void pollFactorReturns() {
    fetch3Factor();
    fetch5Factor();
    fetchMomentum();
  }

  private void fetch3Factor() {
    List<FactorReturn> returns = client.fetchDataset(
        "F-F_Research_Data_Factors_CSV.zip", FactorSet.FACTOR_3, "MONTHLY");
    log.info("Ingested {} 3-factor monthly rows", returns.size());
  }

  private void fetch5Factor() {
    List<FactorReturn> returns = client.fetchDataset(
        "F-F_Research_Data_5_Factors_2x3_CSV.zip", FactorSet.FACTOR_5, "MONTHLY");
    log.info("Ingested {} 5-factor monthly rows", returns.size());
  }

  private void fetchMomentum() {
    List<FactorReturn> returns = client.fetchDataset(
        "Momentum_Factor_CSV.zip", FactorSet.MOMENTUM, "MONTHLY");
    log.info("Ingested {} momentum monthly rows", returns.size());
  }
}
