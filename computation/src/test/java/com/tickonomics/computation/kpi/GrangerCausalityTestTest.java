package com.tickonomics.computation.kpi;

import com.tickonomics.contracts.client.AnalyticsWorkerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrangerCausalityTestTest {

    @Mock
    private AnalyticsWorkerClient analyticsClient;

    private GrangerCausalityTest tester;

    @BeforeEach
    void setUp() {
        tester = new GrangerCausalityTest(analyticsClient);
    }

    private List<Double> series(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToDouble(i -> Math.sin(i * 0.1) + 4.0)
                .boxed()
                .toList();
    }

    @Nested
    class CausalityTest {
        @Test
        void givenCausalResponse_whenTest_thenCausalResult() {
            when(analyticsClient.sendAnalysisRequest(eq("/api/v1/econometrics/granger"), anyMap()))
                    .thenReturn(Map.of(
                            "f_statistic", 5.23,
                            "p_value", 0.02,
                            "lags", 3,
                            "causal", true,
                            "n_observations", 100
                    ));

            var result = tester.test(series(100), series(100), 10);
            assertTrue(result.causal());
            assertEquals(5.23, result.fStatistic(), 1e-6);
            assertEquals(0.02, result.pValue(), 1e-6);
            assertEquals(3, result.lags());
            assertEquals("OK", result.status());
            assertEquals(100, result.nObservations());
        }

        @Test
        void givenNonCausalResponse_whenTest_thenNotCausal() {
            when(analyticsClient.sendAnalysisRequest(eq("/api/v1/econometrics/granger"), anyMap()))
                    .thenReturn(Map.of(
                            "f_statistic", 0.89,
                            "p_value", 0.45,
                            "lags", 2,
                            "causal", false,
                            "n_observations", 50
                    ));

            var result = tester.test(series(50), series(50), 5);
            assertFalse(result.causal());
            assertEquals(0.45, result.pValue(), 1e-6);
            assertEquals("OK", result.status());
        }

        @Test
        void givenWorkerError_whenTest_thenWorkErrorStatus() {
            when(analyticsClient.sendAnalysisRequest(anyString(), anyMap()))
                    .thenReturn(Map.of("error", "connection refused"));

            var result = tester.test(series(50), series(50), 5);
            assertEquals("WORKER_ERROR", result.status());
            assertFalse(result.causal());
        }
    }

    @Nested
    class Validation {
        @Test
        void givenUnequalLengths_whenTest_thenMismatchStatus() {
            var result = tester.test(series(50), series(30), 5);
            assertEquals("SERIES_LENGTH_MISMATCH", result.status());
        }

        @Test
        void givenInsufficientData_whenTest_thenInsufficientStatus() {
            var result = tester.test(List.of(1.0, 2.0, 3.0), List.of(1.0, 2.0, 3.0), 5);
            assertEquals("INSUFFICIENT_DATA", result.status());
        }
    }
}
