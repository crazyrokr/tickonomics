package com.tickonomics.ingestion.writer;

import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import com.tickonomics.persistence.repository.TickDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class TimescaleDbWriter {

    private static final Logger log = LoggerFactory.getLogger(TimescaleDbWriter.class);

    private final TickDataRepository tickDataRepository;
    private final RateSnapshotRepository rateSnapshotRepository;

    private final ConcurrentLinkedQueue<TickData> tickBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<RateSnapshot> rateBuffer = new ConcurrentLinkedQueue<>();

    @Value("${writer.batch-size:500}")
    int batchSize;

    @Value("${writer.flush-interval-ms:500}")
    private int flushIntervalMs;

    public TimescaleDbWriter(TickDataRepository tickDataRepository,
                             RateSnapshotRepository rateSnapshotRepository) {
        this.tickDataRepository = tickDataRepository;
        this.rateSnapshotRepository = rateSnapshotRepository;
    }

    public void writeTick(TickData tick) {
        tickBuffer.add(tick);
        if (tickBuffer.size() >= batchSize) {
            flushTicks();
        }
    }

    public void writeRate(RateSnapshot snapshot) {
        rateBuffer.add(snapshot);
        if (rateBuffer.size() >= batchSize) {
            flushRates();
        }
    }

    @Scheduled(fixedDelayString = "${writer.flush-interval-ms:500}")
    public void flushAll() {
        flushTicks();
        flushRates();
    }

    void flushTicks() {
        List<TickData> batch = drainBuffer(tickBuffer);
        if (batch.isEmpty()) return;

        try {
            tickDataRepository.saveAll(batch);
            log.debug("Flushed {} tick records", batch.size());
        } catch (Exception e) {
            log.error("Failed to flush {} tick records: {}", batch.size(), e.getMessage());
            tickBuffer.addAll(batch);
        }
    }

    void flushRates() {
        List<RateSnapshot> batch = drainBuffer(rateBuffer);
        if (batch.isEmpty()) return;

        try {
            rateSnapshotRepository.saveAll(batch);
            log.debug("Flushed {} rate records", batch.size());
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

    private <T> List<T> drainBuffer(ConcurrentLinkedQueue<T> buffer) {
        List<T> batch = new ArrayList<>();
        T item;
        while ((item = buffer.poll()) != null) {
            batch.add(item);
        }
        return batch;
    }
}
