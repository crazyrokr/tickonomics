package com.tickonomics.ingestion.writer;

import com.tickonomics.persistence.entity.IdempotentRow;
import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import com.tickonomics.persistence.repository.TickDataRepository;
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
  private final IdempotencyGuard idempotencyGuard;
  private final IngestionTracer tracer;

  private final ConcurrentLinkedQueue<IdempotentRow<TickData>> tickBuffer = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<IdempotentRow<RateSnapshot>> rateBuffer = new ConcurrentLinkedQueue<>();

  @Value("${writer.batch-size:500}")
  int batchSize;

  @Value("${writer.flush-interval-ms:500}")
  private int flushIntervalMs;

  public TimescaleDbWriter(
      TickDataRepository tickDataRepository,
      RateSnapshotRepository rateSnapshotRepository,
      IdempotencyGuard idempotencyGuard,
      IngestionTracer tracer) {
    this.tickDataRepository = tickDataRepository;
    this.rateSnapshotRepository = rateSnapshotRepository;
    this.idempotencyGuard = idempotencyGuard;
    this.tracer = tracer;
  }

  public void writeTick(TickData tick) {
    String key = idempotencyGuard.buildKey("TICK", tick.symbol(), tick.time());
    if (idempotencyGuard.isDuplicate(key)) {
      log.debug("Skipping duplicate tick: {}", key);
      return;
    }
    tickBuffer.add(new IdempotentRow<>(deterministicUuid(key), tick));
    if (tickBuffer.size() >= batchSize) {
      flushTicks();
    }
  }

  public void writeRate(RateSnapshot snapshot) {
    String key = idempotencyGuard.buildKey("RATE", snapshot.rateType(), snapshot.time());
    if (idempotencyGuard.isDuplicate(key)) {
      log.debug("Skipping duplicate rate: {}", key);
      return;
    }
    rateBuffer.add(new IdempotentRow<>(deterministicUuid(key), snapshot));
    if (rateBuffer.size() >= batchSize) {
      flushRates();
    }
  }

  @Scheduled(fixedDelayString = "${writer.flush-interval-ms:500}")
  public void flushAll() {
    try (var scope = tracer.span(IngestionTracingConfig.SPAN_TIMESCALEDB_WRITE)) {
      flushTicks();
      flushRates();
    }
  }

  void flushTicks() {
    List<IdempotentRow<TickData>> batch = drainBuffer(tickBuffer);
    if (batch.isEmpty()) {
      return;
    }

    try {
      int[] affected = tickDataRepository.saveAllIdempotent(batch);
      log.debug("Flushed {} tick records ({} new)", batch.size(), countNew(affected));
    } catch (Exception e) {
      log.error("Failed to flush {} tick records: {}", batch.size(), e.getMessage());
      tickBuffer.addAll(batch);
    }
  }

  void flushRates() {
    List<IdempotentRow<RateSnapshot>> batch = drainBuffer(rateBuffer);
    if (batch.isEmpty()) {
      return;
    }

    try {
      int[] affected = rateSnapshotRepository.saveAllIdempotent(batch);
      log.debug("Flushed {} rate records ({} new)", batch.size(), countNew(affected));
    } catch (Exception e) {
      log.error("Failed to flush {} rate records: {}", batch.size(), e.getMessage());
      rateBuffer.addAll(batch);
    }
  }

  public int pendingTickCount() {
    return tickBuffer.size();
  }

  public int pendingRateCount() {
    return rateBuffer.size();
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
