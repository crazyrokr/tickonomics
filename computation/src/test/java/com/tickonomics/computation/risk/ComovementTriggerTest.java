package com.tickonomics.computation.risk;

import com.tickonomics.computation.client.RestClientAnalyticsWorkerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComovementTriggerTest {

    @Mock
    private RestClientAnalyticsWorkerClient analyticsClient;

    private ComovementTrigger trigger;

    @BeforeEach
    void setUp() {
        trigger = new ComovementTrigger(analyticsClient);
    }

    private double[] generateHistory(double base, int count) {
        double[] arr = new double[count];
        for (int i = 0; i < count; i++) {
            arr[i] = base + i * 0.1;
        }
        return arr;
    }

    @Nested
    class PercentileComputation {

        @Test
        void givenNullHistory_whenEvaluate_thenFiftiethPercentile() {
            /*
             * Given: null historical factors
             * When: evaluate() is called
             * Then: percentile = 50.0 (middle default)
             */
            var verdict = trigger.evaluate(0.5, null);

            assertEquals(50.0, verdict.percentile(), 1e-10);
        }

        @Test
        void givenEmptyHistory_whenEvaluate_thenFiftiethPercentile() {
            /*
             * Given: empty historical factors
             * When: evaluate() is called
             * Then: percentile = 50.0
             */
            var verdict = trigger.evaluate(0.5, new double[]{});

            assertEquals(50.0, verdict.percentile(), 1e-10);
        }

        @Test
        void givenFactorAboveAllHistory_whenEvaluate_thenHighPercentile() {
            /*
             * Given: factor=5.0 and history with values 1.0-4.0
             * When: evaluate() is called
             * Then: percentile near 100.0 (all values below)
             */
            double[] history = {1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0};

            var verdict = trigger.evaluate(5.0, history);

            assertEquals(100.0, verdict.percentile(), 1e-10);
        }

        @Test
        void givenFactorBelowAllHistory_whenEvaluate_thenZeroPercentile() {
            /*
             * Given: factor=0.1 and history with values 1.0-4.0
             * When: evaluate() is called
             * Then: percentile = 0.0 (no values below)
             */
            double[] history = {1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0};

            var verdict = trigger.evaluate(0.1, history);

            assertEquals(0.0, verdict.percentile(), 1e-10);
        }
    }

    @Nested
    class RecommendationLevels {

        @Test
        void givenPercentileAbove90_whenEvaluate_thenSwitchToDefensive() {
            /*
             * Given: factor at 95th percentile of history
             * When: evaluate() is called
             * Then: recommendation = "SWITCH_TO_DEFENSIVE", elevatedRisk=true
             */
            double[] history = generateHistory(0.1, 100);

            var verdict = trigger.evaluate(15.0, history);

            assertTrue(verdict.percentile() > 90.0);
            assertEquals(ComovementTrigger.RECOMMENDATION_SWITCH_TO_DEFENSIVE, verdict.recommendation());
            assertTrue(verdict.elevatedRisk());
        }

        @Test
        void givenPercentileAbove75_whenEvaluate_thenMonitorClosely() {
            /*
             * Given: factor at 80th percentile of history
             * When: evaluate() is called
             * Then: recommendation = "MONITOR_CLOSELY", elevatedRisk=true
             */
            double[] history = new double[100];
            for (int i = 0; i < 100; i++) {
                history[i] = i * 0.1;
            }

            var verdict = trigger.evaluate(8.0, history);

            assertTrue(verdict.percentile() > 75.0);
            assertTrue(verdict.percentile() <= 90.0);
            assertEquals(ComovementTrigger.RECOMMENDATION_MONITOR_CLOSELY, verdict.recommendation());
            assertTrue(verdict.elevatedRisk());
        }

        @Test
        void givenPercentileBelow75_whenEvaluate_thenNormalOps() {
            /*
             * Given: factor at 50th percentile of history
             * When: evaluate() is called
             * Then: recommendation = "NORMAL_OPS", elevatedRisk=false
             */
            double[] history = generateHistory(0.1, 100);

            var verdict = trigger.evaluate(5.0, history);

            assertTrue(verdict.percentile() <= 75.0);
            assertEquals(ComovementTrigger.RECOMMENDATION_NORMAL_OPS, verdict.recommendation());
            assertFalse(verdict.elevatedRisk());
        }
    }

    @Nested
    class RemoteEvaluation {

        @Test
        void givenRemoteCallFails_whenEvaluateFromRemote_thenFallsBackToLocal() {
            /*
             * Given: remote analytics client throws exception
             * When: evaluateFromRemote() is called
             * Then: falls back to local computation
             */
            when(analyticsClient.sendAnalysisRequest(anyString(), any()))
                    .thenReturn(Map.of("error", "connection refused"));

            double[] history = generateHistory(0.1, 50);

            var verdict = trigger.evaluateFromRemote("AAPL", 1.0, history);

            assertNotNull(verdict);
            assertEquals(ComovementTrigger.RECOMMENDATION_NORMAL_OPS, verdict.recommendation());
        }

        @Test
        void givenRemoteReturnsSuccess_whenEvaluateFromRemote_thenReturnsVerdict() {
            /*
             * Given: remote analytics client returns successful response
             * When: evaluateFromRemote() is called
             * Then: still computes verdict locally with the data
             */
            when(analyticsClient.sendAnalysisRequest(anyString(), any()))
                    .thenReturn(Map.of("status", "ok"));

            double[] history = generateHistory(0.1, 50);

            var verdict = trigger.evaluateFromRemote("AAPL", 5.0, history);

            assertNotNull(verdict);
            assertEquals(5.0, verdict.factor(), 1e-10);
        }
    }
}
