package com.tickonomics.ingestion.tracing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("IngestionMetrics")
class IngestionMetricsTest {

    private MeterRegistry registry;
    private IngestionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new IngestionMetrics(registry);
    }

    // -- Write path ---------------------------------------------------------

    @Test
    @DisplayName("Should increment tick write success counter")
    void shouldIncrementTickWriteSuccessCounter() {
        // When
        metrics.recordWriteSuccessTick();
        metrics.recordWriteSuccessTick();

        // Then
        double count = registry.get("ingestion.write.success")
                .tag("type", "tick").counter().count();
        assertThat(count).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should increment rate write success counter")
    void shouldIncrementRateWriteSuccessCounter() {
        // When
        metrics.recordWriteSuccessRate();

        // Then
        double count = registry.get("ingestion.write.success")
                .tag("type", "rate").counter().count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should increment tick write failure counter")
    void shouldIncrementTickWriteFailureCounter() {
        // When
        metrics.recordWriteFailureTick();

        // Then
        double count = registry.get("ingestion.write.failure")
                .tag("type", "tick").counter().count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should increment rate write failure counter")
    void shouldIncrementRateWriteFailureCounter() {
        // When
        metrics.recordWriteFailureRate();

        // Then
        double count = registry.get("ingestion.write.failure")
                .tag("type", "rate").counter().count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should increment duplicate tick counter")
    void shouldIncrementDuplicateTickCounter() {
        // When
        metrics.recordDuplicateTick();
        metrics.recordDuplicateTick();
        metrics.recordDuplicateTick();

        // Then
        double count = registry.get("ingestion.write.duplicate")
                .tag("type", "tick").counter().count();
        assertThat(count).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Should increment duplicate rate counter")
    void shouldIncrementDuplicateRateCounter() {
        // When
        metrics.recordDuplicateRate();

        // Then
        double count = registry.get("ingestion.write.duplicate")
                .tag("type", "rate").counter().count();
        assertThat(count).isEqualTo(1.0);
    }

    // -- Quality guard ------------------------------------------------------

    @Test
    @DisplayName("Should increment breach counter")
    void shouldIncrementBreachCounter() {
        // When
        metrics.recordBreach("FLASH_MOVE");
        metrics.recordBreach("MESSAGE_RATE_SPIKE");

        // Then
        double count = registry.get("ingestion.sanity.breach").counter().count();
        assertThat(count).isEqualTo(2.0);
    }

    // -- Buffer gauges ------------------------------------------------------

    @Test
    @DisplayName("Should update tick buffer size gauge")
    void shouldUpdateTickBufferSizeGauge() {
        // When
        metrics.setTickBufferSize(42);

        // Then
        double value = registry.get("ingestion.buffer.tick.size").gauge().value();
        assertThat(value).isEqualTo(42.0);
    }

    @Test
    @DisplayName("Should update rate buffer size gauge")
    void shouldUpdateRateBufferSizeGauge() {
        // When
        metrics.setRateBufferSize(7);

        // Then
        double value = registry.get("ingestion.buffer.rate.size").gauge().value();
        assertThat(value).isEqualTo(7.0);
    }

    // -- WebSocket ----------------------------------------------------------

    @Test
    @DisplayName("Should increment WS message counter")
    void shouldIncrementWsMessageCounter() {
        // When
        metrics.recordWsMessage();
        metrics.recordWsMessage();

        // Then
        double count = registry.get("ingestion.ws.messages").counter().count();
        assertThat(count).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should increment WS IO error counter")
    void shouldIncrementWsIoErrorCounter() {
        // When
        metrics.recordWsIoError();

        // Then
        double count = registry.get("ingestion.ws.ioerrors").counter().count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should set WS connected gauge to 1 when connected")
    void shouldSetWsConnectedGaugeToOneWhenConnected() {
        // When
        metrics.setWsConnected(true);

        // Then
        double value = registry.get("ingestion.ws.connected").gauge().value();
        assertThat(value).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should set WS connected gauge to 0 when disconnected")
    void shouldSetWsConnectedGaugeToZeroWhenDisconnected() {
        // When
        metrics.setWsConnected(false);

        // Then
        double value = registry.get("ingestion.ws.connected").gauge().value();
        assertThat(value).isEqualTo(0.0);
    }

    // -- Flush timer --------------------------------------------------------

    @Test
    @DisplayName("Should record flush duration")
    void shouldRecordFlushDuration() {
        // When
        metrics.recordFlushDuration(1_500_000_000L); // 1.5s in nanos

        // Then
        double totalTime = registry.get("ingestion.write.flush").timer().totalTime(
                java.util.concurrent.TimeUnit.NANOSECONDS);
        assertThat(totalTime).isGreaterThan(0.0);
    }

    // -- Edge cases ---------------------------------------------------------

    @Test
    @DisplayName("Should not throw when setting buffer size to zero")
    void shouldNotThrowWhenSettingBufferSizeToZero() {
        assertThatCode(() -> {
            metrics.setTickBufferSize(0);
            metrics.setRateBufferSize(0);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should not throw on zero flush duration")
    void shouldNotThrowOnZeroFlushDuration() {
        assertThatCode(() -> metrics.recordFlushDuration(0L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should tolerate multiple gauge updates")
    void shouldTolerateMultipleGaugeUpdates() {
        // When - updating the same gauge multiple times
        metrics.setTickBufferSize(10);
        metrics.setTickBufferSize(20);
        metrics.setTickBufferSize(0);

        // Then - last value wins
        double value = registry.get("ingestion.buffer.tick.size").gauge().value();
        assertThat(value).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should distinguish tick and rate metrics by tag")
    void shouldDistinguishTickAndRateMetricsByTag() {
        // When
        metrics.recordWriteSuccessTick();
        metrics.recordWriteSuccessTick();
        metrics.recordWriteSuccessRate();

        // Then
        double tickCount = registry.get("ingestion.write.success")
                .tag("type", "tick").counter().count();
        double rateCount = registry.get("ingestion.write.success")
                .tag("type", "rate").counter().count();
        assertThat(tickCount).isEqualTo(2.0);
        assertThat(rateCount).isEqualTo(1.0);
    }
}
