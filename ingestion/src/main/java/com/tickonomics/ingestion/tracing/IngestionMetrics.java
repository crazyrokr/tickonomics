package com.tickonomics.ingestion.tracing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom Micrometer metrics for the ingestion pipeline.
 *
 * <p>Covers three layers:
 * <ul>
 *   <li>Write path — success/failure/duplicate counters, buffer-size gauges</li>
 *   <li>Quality guard — sanity-breach counter by type</li>
 *   <li>Realtime feed — WebSocket connection state, message rate, IO errors</li>
 * </ul>
 */
@Component
public class IngestionMetrics {

    private final MeterRegistry registry;

    private final Counter writeSuccessTicks;
    private final Counter writeSuccessRates;
    private final Counter writeFailureTicks;
    private final Counter writeFailureRates;
    private final Counter duplicateTicks;
    private final Counter duplicateRates;
    private final Counter breachCounter;
    private final Counter wsMessagesReceived;
    private final Counter wsIoErrors;
    private final Timer writeFlushTimer;

    private final AtomicInteger tickBufferSize = new AtomicInteger(0);
    private final AtomicInteger rateBufferSize = new AtomicInteger(0);
    private final AtomicInteger wsConnected = new AtomicInteger(0);

    public IngestionMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.writeSuccessTicks = Counter.builder("ingestion.write.success")
                .tag("type", "tick")
                .description("Successful tick writes")
                .register(registry);
        this.writeSuccessRates = Counter.builder("ingestion.write.success")
                .tag("type", "rate")
                .description("Successful rate writes")
                .register(registry);
        this.writeFailureTicks = Counter.builder("ingestion.write.failure")
                .tag("type", "tick")
                .description("Failed tick write attempts")
                .register(registry);
        this.writeFailureRates = Counter.builder("ingestion.write.failure")
                .tag("type", "rate")
                .description("Failed rate write attempts")
                .register(registry);
        this.duplicateTicks = Counter.builder("ingestion.write.duplicate")
                .tag("type", "tick")
                .description("Duplicate ticks skipped")
                .register(registry);
        this.duplicateRates = Counter.builder("ingestion.write.duplicate")
                .tag("type", "rate")
                .description("Duplicate rates skipped")
                .register(registry);
        this.breachCounter = Counter.builder("ingestion.sanity.breach")
                .description("Algorithmic sanity breaches detected")
                .register(registry);
        this.wsMessagesReceived = Counter.builder("ingestion.ws.messages")
                .description("WebSocket messages received")
                .register(registry);
        this.wsIoErrors = Counter.builder("ingestion.ws.ioerrors")
                .description("WebSocket IO errors")
                .register(registry);
        this.writeFlushTimer = Timer.builder("ingestion.write.flush")
                .description("Write buffer flush duration")
                .register(registry);

        Gauge.builder("ingestion.buffer.tick.size", tickBufferSize, AtomicInteger::get)
                .description("Pending ticks in write buffer")
                .register(registry);
        Gauge.builder("ingestion.buffer.rate.size", rateBufferSize, AtomicInteger::get)
                .description("Pending rates in write buffer")
                .register(registry);
        Gauge.builder("ingestion.ws.connected", wsConnected, AtomicInteger::get)
                .description("WebSocket connection state (1 = connected)")
                .register(registry);
    }

    // -- Write path ---------------------------------------------------------

    public void recordWriteSuccessTick() {
        writeSuccessTicks.increment();
    }

    public void recordWriteSuccessRate() {
        writeSuccessRates.increment();
    }

    public void recordWriteFailureTick() {
        writeFailureTicks.increment();
    }

    public void recordWriteFailureRate() {
        writeFailureRates.increment();
    }

    public void recordDuplicateTick() {
        duplicateTicks.increment();
    }

    public void recordDuplicateRate() {
        duplicateRates.increment();
    }

    public void recordFlushDuration(long durationNanos) {
        writeFlushTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void setTickBufferSize(int size) {
        tickBufferSize.set(size);
    }

    public void setRateBufferSize(int size) {
        rateBufferSize.set(size);
    }

    // -- Quality guard ------------------------------------------------------

    public void recordBreach(String breachType) {
        breachCounter.increment();
    }

    // -- WebSocket ----------------------------------------------------------

    public void recordWsMessage() {
        wsMessagesReceived.increment();
    }

    public void recordWsIoError() {
        wsIoErrors.increment();
    }

    public void setWsConnected(boolean connected) {
        wsConnected.set(connected ? 1 : 0);
    }
}
