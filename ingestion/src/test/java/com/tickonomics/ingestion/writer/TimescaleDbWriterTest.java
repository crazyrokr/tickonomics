package com.tickonomics.ingestion.writer;

import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import com.tickonomics.persistence.repository.TickDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimescaleDbWriterTest {

    private static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z");

    @Mock private TickDataRepository tickDataRepository;
    @Mock private RateSnapshotRepository rateSnapshotRepository;

    private TimescaleDbWriter writer;

    @BeforeEach
    void setUp() {
        writer = new TimescaleDbWriter(tickDataRepository, rateSnapshotRepository);
        writer.batchSize = 10;
    }

    @Nested
    class WriteTick {
        @Test
        void givenSingleTick_whenWrite_thenBufferedNotFlushed() {
            var tick = new TickData(NOW, "SPY", 450.50, 1000, new int[]{});
            writer.writeTick(tick);
            assertEquals(1, writer.pendingTickCount());
            verify(tickDataRepository, never()).saveAll(any());
        }

        @Test
        void givenBatchSizeTicks_whenWrite_thenAutoFlushed() {
            for (int i = 0; i < 10; i++) {
                writer.writeTick(new TickData(NOW.plusMillis(i), "SPY", 450.0 + i, 100, new int[]{}));
            }
            assertEquals(0, writer.pendingTickCount());
            verify(tickDataRepository).saveAll(any());
        }

        @Test
        void givenFlushAll_whenBuffered_thenDrained() {
            writer.writeTick(new TickData(NOW, "SPY", 450.50, 1000, new int[]{}));
            writer.writeRate(new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED"));
            writer.flushAll();
            assertEquals(0, writer.pendingTickCount());
            assertEquals(0, writer.pendingRateCount());
            verify(tickDataRepository).saveAll(any());
            verify(rateSnapshotRepository).saveAll(any());
        }
    }

    @Nested
    class WriteRate {
        @Test
        void givenSingleRate_whenWrite_thenBufferedNotFlushed() {
            var rate = new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED");
            writer.writeRate(rate);
            assertEquals(1, writer.pendingRateCount());
            verify(rateSnapshotRepository, never()).saveAll(any());
        }

        @Test
        void givenBatchSizeRates_whenWrite_thenAutoFlushed() {
            for (int i = 0; i < 10; i++) {
                writer.writeRate(new RateSnapshot(NOW.plusMillis(i), "SOFR", 4.29 + i * 0.01, "NY_FED"));
            }
            assertEquals(0, writer.pendingRateCount());
            verify(rateSnapshotRepository).saveAll(any());
        }
    }

    @Nested
    class ErrorRecovery {
        @Test
        void givenFlushFailure_whenFlush_thenRebuffered() {
            doThrow(new RuntimeException("DB error")).when(tickDataRepository).saveAll(any());
            writer.writeTick(new TickData(NOW, "SPY", 450.50, 1000, new int[]{}));
            writer.flushTicks();
            assertTrue(writer.pendingTickCount() > 0);
        }
    }
}
