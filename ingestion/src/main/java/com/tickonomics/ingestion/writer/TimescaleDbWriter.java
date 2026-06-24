package com.tickonomics.ingestion.writer;

import com.tickonomics.persistence.entity.IdempotentRow;
import com.tickonomics.persistence.entity.NewsEvent;
import com.tickonomics.persistence.entity.PredictionMarketQuote;
import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.repository.NewsEventRepository;
import com.tickonomics.persistence.repository.PredictionMarketQuoteRepository;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import com.tickonomics.persistence.repository.TickDataRepository;
import com.tickonomics.ingestion.tracing.IngestionMetrics;
import com.tickonomics.ingestion.tracing.IngestionTracer;
import com.tickonomics.ingestion.tracing.IngestionTracingConfig;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TimescaleDbWriter {

  private static final Logger log = LoggerFactory.getLogger(TimescaleDbWriter.class);

  private final TickDataRepository tickDataRepository;
  private final RateSnapshotRepository rateSnapshotRepository;
  private final PredictionMarketQuoteRepository predictionMarketQuoteRepository;
  private final NewsEventRepository newsEventRepository;
  private final IdempotencyGuard idempotencyGuard;
  private final IngestionTracer tracer;
  private final IngestionMetrics metrics;

  private final ConcurrentLinkedQueue<IdempotentRow<TickData>> tickBuffer = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<IdempotentRow<RateSnapshot>> rateBuffer = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<IdempotentRow<PredictionMarketQuote>> predictionMarketBuffer =
      new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<IdempotentRow<NewsEvent>> newsBuffer = new ConcurrentLinkedQueue<>();

  private final int batchSize;

  private final int flushIntervalMs;

  public TimescaleDbWriter(
      TickDataRepository tickDataRepository,
      RateSnapshotRepository rateSnapshotRepository,
      PredictionMarketQuoteRepository predictionMarketQuoteRepository,
      NewsEventRepository newsEventRepository,
      IdempotencyGuard idempotencyGuard,
      IngestionTracer tracer,
      IngestionMetrics metrics,
      @Value("${writer.batch-size:500}") int batchSize,
      @Value("${writer.flush-interval-ms:500}") int flushIntervalMs) {
    this.tickDataRepository = tickDataRepository;
    this.rateSnapshotRepository = rateSnapshotRepository;
    this.predictionMarketQuoteRepository = predictionMarketQuoteRepository;
    this.newsEventRepository = newsEventRepository;
    this.idempotencyGuard = idempotencyGuard;
    this.tracer = tracer;
    this.metrics = metrics;
    this.batchSize = batchSize;
    this.flushIntervalMs = flushIntervalMs;
  }

  public void writeTick(TickData tick) {
    String key = idempotencyGuard.buildKey("TICK", tick.symbol(), tick.time());
    if (idempotencyGuard.isDuplicate(key)) {
      metrics.recordDuplicateTick();
      log.debug("Skipping duplicate tick: {}", key);
      return;
    }
    tickBuffer.add(new IdempotentRow<>(deterministicUuid(key), tick));
    metrics.setTickBufferSize(tickBuffer.size());
    if (tickBuffer.size() >= batchSize) {
      flushTicks();
    }
  }

  public void writeRate(RateSnapshot snapshot) {
    String key = idempotencyGuard.buildKey("RATE", snapshot.rateType(), snapshot.time());
    if (idempotencyGuard.isDuplicate(key)) {
      metrics.recordDuplicateRate();
      log.debug("Skipping duplicate rate: {}", key);
      return;
    }
    rateBuffer.add(new IdempotentRow<>(deterministicUuid(key), snapshot));
    metrics.setRateBufferSize(rateBuffer.size());
    if (rateBuffer.size() >= batchSize) {
      flushRates();
    }
  }

  public void writePredictionMarketQuote(PredictionMarketQuote quote) {
    String key = idempotencyGuard.buildKey("PREDICTION_MARKET", quote.marketId(), quote.time());
    if (idempotencyGuard.isDuplicate(key)) {
      metrics.recordDuplicatePredictionMarket();
      log.debug("Skipping duplicate prediction-market quote: {}", key);
      return;
    }
    predictionMarketBuffer.add(new IdempotentRow<>(deterministicUuid(key), quote));
    metrics.setPredictionMarketBufferSize(predictionMarketBuffer.size());
    if (predictionMarketBuffer.size() >= batchSize) {
      flushPredictionMarketQuotes();
    }
  }

  public void writeNewsEvent(NewsEvent event) {
    String key = idempotencyGuard.buildKey("NEWS", event.eventId(), event.time());
    if (idempotencyGuard.isDuplicate(key)) {
      metrics.recordDuplicateNews();
      log.debug("Skipping duplicate news event: {}", key);
      return;
    }
    newsBuffer.add(new IdempotentRow<>(deterministicUuid(key), event));
    metrics.setNewsBufferSize(newsBuffer.size());
    if (newsBuffer.size() >= batchSize) {
      flushNewsEvents();
    }
  }

  @Scheduled(fixedDelayString = "${writer.flush-interval-ms:500}")
  public void flushAll() {
    try (var scope = tracer.span(IngestionTracingConfig.SPAN_TIMESCALEDB_WRITE)) {
      flushTicks();
      flushRates();
      flushPredictionMarketQuotes();
      flushNewsEvents();
    }
  }

  void flushTicks() {
    List<IdempotentRow<TickData>> batch = drainBuffer(tickBuffer);
    metrics.setTickBufferSize(tickBuffer.size());
    if (batch.isEmpty()) {
      return;
    }

    long start = System.nanoTime();
    try {
      int[] affected = tickDataRepository.saveAllIdempotent(batch);
      metrics.recordWriteSuccessTick();
      metrics.recordFlushDuration(System.nanoTime() - start);
      log.debug("Flushed {} tick records ({} new)", batch.size(), countNew(affected));
    } catch (Exception e) {
      metrics.recordWriteFailureTick();
      log.error("Failed to flush {} tick records: {}", batch.size(), e.getMessage());
      tickBuffer.addAll(batch);
    }
  }

  void flushRates() {
    List<IdempotentRow<RateSnapshot>> batch = drainBuffer(rateBuffer);
    metrics.setRateBufferSize(rateBuffer.size());
    if (batch.isEmpty()) {
      return;
    }

    long start = System.nanoTime();
    try {
      int[] affected = rateSnapshotRepository.saveAllIdempotent(batch);
      metrics.recordWriteSuccessRate();
      metrics.recordFlushDuration(System.nanoTime() - start);
      log.debug("Flushed {} rate records ({} new)", batch.size(), countNew(affected));
    } catch (Exception e) {
      metrics.recordWriteFailureRate();
      log.error("Failed to flush {} rate records: {}", batch.size(), e.getMessage());
      rateBuffer.addAll(batch);
    }
  }

  void flushPredictionMarketQuotes() {
    List<IdempotentRow<PredictionMarketQuote>> batch = drainBuffer(predictionMarketBuffer);
    metrics.setPredictionMarketBufferSize(predictionMarketBuffer.size());
    if (batch.isEmpty()) {
      return;
    }

    long start = System.nanoTime();
    try {
      int[] affected = predictionMarketQuoteRepository.saveAllIdempotent(batch);
      metrics.recordWriteSuccessPredictionMarket();
      metrics.recordFlushDuration(System.nanoTime() - start);
      log.debug("Flushed {} prediction-market quotes ({} new)", batch.size(), countNew(affected));
    } catch (Exception e) {
      metrics.recordWriteFailurePredictionMarket();
      log.error("Failed to flush {} prediction-market quotes: {}", batch.size(), e.getMessage());
      predictionMarketBuffer.addAll(batch);
    }
  }

  void flushNewsEvents() {
    List<IdempotentRow<NewsEvent>> batch = drainBuffer(newsBuffer);
    metrics.setNewsBufferSize(newsBuffer.size());
    if (batch.isEmpty()) {
      return;
    }

    long start = System.nanoTime();
    try {
      int[] affected = newsEventRepository.saveAllIdempotent(batch);
      metrics.recordWriteSuccessNews();
      metrics.recordFlushDuration(System.nanoTime() - start);
      log.debug("Flushed {} news events ({} new)", batch.size(), countNew(affected));
    } catch (Exception e) {
      metrics.recordWriteFailureNews();
      log.error("Failed to flush {} news events: {}", batch.size(), e.getMessage());
      newsBuffer.addAll(batch);
    }
  }

  public int pendingTickCount() {
    return tickBuffer.size();
  }

  public int pendingRateCount() {
    return rateBuffer.size();
  }

  public int pendingPredictionMarketCount() {
    return predictionMarketBuffer.size();
  }

  public int pendingNewsCount() {
    return newsBuffer.size();
  }

  /**
   * Derives a deterministic type-3 (name-based) UUID from the natural idempotency key so that a
   * retried write of the same logical event produces the identical UUID and is suppressed by the
   * database {@code ON CONFLICT} constraint, even across process restarts.
   */
  static UUID deterministicUuid(String key) {
    return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
  }

  private static int countNew(int[] affected) {
    if (affected == null) {
      return 0;
    }
    int sum = 0;
    for (int rows : affected) {
      if (rows > 0) {
        sum += rows;
      }
    }
    return sum;
  }

  private <T> List<T> drainBuffer(ConcurrentLinkedQueue<T> buffer) {
    List<T> batch = new ArrayList<>();
    T item;
    while ((item = buffer.poll()) != null) {
      batch.add(item);
    }
    return batch;
  }
}
