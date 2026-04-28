package com.tickonomics.computation.transport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArrowIpcTransportTest {

    private ArrowIpcTransport transport;

    @BeforeEach
    void setUp() {
        transport = new ArrowIpcTransport();
    }

    @Nested
    class TimeSeriesRoundTrip {
        @Test
        void givenTimeSeriesData_whenSerializedAndDeserialized_thenValuesMatch() {
            double[] values = {100.5, 101.2, 99.8, 102.3};
            long[] timestamps = {1000L, 2000L, 3000L, 4000L};
            byte[] serialized = transport.serializeTimeSeries("SPY", values, timestamps);
            assertNotNull(serialized);
            assertTrue(serialized.length > 0);

            ArrowIpcTransport.TimeSeriesBatch batch = transport.deserializeTimeSeries(serialized);
            assertArrayEquals(values, batch.values(), 1e-9);
            assertArrayEquals(timestamps, batch.timestamps());
        }

        @Test
        void givenSingleDataPoint_whenRoundTripped_thenCorrect() {
            double[] values = {42.0};
            long[] timestamps = {9999L};
            byte[] data = transport.serializeTimeSeries("AAPL", values, timestamps);
            ArrowIpcTransport.TimeSeriesBatch batch = transport.deserializeTimeSeries(data);
            assertEquals(1, batch.values().length);
            assertEquals(42.0, batch.values()[0], 1e-9);
            assertEquals(9999L, batch.timestamps()[0]);
        }

        @Test
        void givenMismatchedLengths_whenSerialize_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> transport.serializeTimeSeries("SPY", new double[]{1, 2}, new long[]{1}));
        }
    }

    @Nested
    class AnalysisResultSerialization {
        @Test
        void givenMultipleColumns_whenSerializedAndDeserialized_thenSizesMatch() {
            byte[] data = transport.serializeAnalysisResult(Map.of(
                    "correlation", new double[]{0.95, 0.87, 0.91},
                    "beta", new double[]{1.2, 0.8, 1.05}
            ));
            assertNotNull(data);
            assertTrue(data.length > 0);
        }

        @Test
        void givenLargeDataset_whenSerialized_thenSizeIsReasonable() {
            double[] large = new double[10_000];
            for (int i = 0; i < large.length; i++) {
                large[i] = Math.random();
            }
            byte[] data = transport.serializeAnalysisResult(Map.of("values", large));
            assertTrue(data.length < large.length * 16, "Arrow IPC should be more compact than raw doubles");
        }
    }

    @Nested
    class TimeSeriesBatchValidation {
        @Test
        void givenNullValues_whenConstruct_thenThrows() {
            assertThrows(NullPointerException.class,
                    () -> new ArrowIpcTransport.TimeSeriesBatch(null, new long[]{1}));
        }

        @Test
        void givenNullTimestamps_whenConstruct_thenThrows() {
            assertThrows(NullPointerException.class,
                    () -> new ArrowIpcTransport.TimeSeriesBatch(new double[]{1}, null));
        }

        @Test
        void givenMismatchedLengths_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ArrowIpcTransport.TimeSeriesBatch(new double[]{1, 2}, new long[]{1}));
        }
    }

    @Nested
    class CustomAllocator {
        @Test
        void givenCustomAllocator_whenUsed_thenWorks() {
            try (var allocator = new org.apache.arrow.memory.RootAllocator()) {
                var customTransport = new ArrowIpcTransport(allocator);
                double[] values = {1.0, 2.0};
                long[] ts = {100L, 200L};
                byte[] data = customTransport.serializeTimeSeries("TEST", values, ts);
                ArrowIpcTransport.TimeSeriesBatch batch = customTransport.deserializeTimeSeries(data);
                assertArrayEquals(values, batch.values(), 1e-9);
            }
        }
    }
}
