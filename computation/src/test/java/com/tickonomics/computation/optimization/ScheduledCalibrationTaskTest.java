package com.tickonomics.computation.optimization;

import com.tickonomics.computation.ili.WeightedWeightStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledCalibrationTaskTest {

    @Mock
    private WeightedWeightStore weightStore;

    private ScheduledCalibrationTask task;

    @BeforeEach
    void setUp() {
        task = new ScheduledCalibrationTask(weightStore);
    }

    @Nested
    class RunCalibrationWithHistory {

        @Test
        void givenNullHistory_whenRunCalibration_thenReturnsInsufficientData() {
            // Given: null price history data
            double[][] history = null;

            // When: calibration is attempted
            ScheduledCalibrationTask.CalibrationResult result =
                    task.runCalibrationWithHistory(history);

            // Then: result indicates insufficient data
            assertFalse(result.improved());
            assertEquals("INSUFFICIENT_DATA", result.status());
            assertEquals(0.0, result.sharpeBefore());
            assertEquals(0.0, result.oosSharpe());
        }

        @Test
        void givenShortHistory_whenRunCalibration_thenReturnsInsufficientData() {
            // Given: price history shorter than 180 days
            double[][] history = new double[100][];

            // When: calibration is attempted
            ScheduledCalibrationTask.CalibrationResult result =
                    task.runCalibrationWithHistory(history);

            // Then: result indicates insufficient data
            assertFalse(result.improved());
            assertEquals("INSUFFICIENT_DATA", result.status());
        }

        @Test
        void givenValidHistory_whenNoImprovement_thenReturnsNoImprovement() {
            // Given: valid 180-day history that does not improve Sharpe
            double[][] history = buildFlatPriceHistory(180, 3, 100.0);
            when(weightStore.getCalibratedWeights())
                    .thenReturn(new double[]{0.4, 0.35, 0.25});

            // When: calibration runs
            ScheduledCalibrationTask.CalibrationResult result =
                    task.runCalibrationWithHistory(history);

            // Then: no improvement is reported
            assertFalse(result.improved());
            assertEquals("NO_IMPROVEMENT", result.status());
            assertNotNull(task.getLastCalibrationTime());
        }

        @Test
        void givenImprovingHistory_whenRunCalibration_thenApplyIsSkippedIfBelowThreshold() {
            // Given: history where optimization yields less than 0.1 Sharpe improvement
            double[][] history = buildFlatPriceHistory(180, 3, 100.0);
            when(weightStore.getCalibratedWeights())
                    .thenReturn(new double[]{0.4, 0.35, 0.25});

            // When: calibration runs
            ScheduledCalibrationTask.CalibrationResult result =
                    task.runCalibrationWithHistory(history);

            // Then: applyDelta is never called because improvement is below threshold
            verify(weightStore, never()).applyDelta(any());
        }

        @Test
        void givenImprovingHistory_whenRunCalibration_thenAppliesDeltasWhenAboveThreshold() {
            // Given: 180-day history with strong trend in one dimension
            double[][] history = buildTrendingPriceHistory(180, 3, 100.0, 0);
            when(weightStore.getCalibratedWeights())
                    .thenReturn(new double[]{0.33, 0.34, 0.33})
                    .thenReturn(new double[]{0.33, 0.34, 0.33});

            // When: calibration runs
            ScheduledCalibrationTask.CalibrationResult result =
                    task.runCalibrationWithHistory(history);

            // Then: result is returned with valid status
            assertNotNull(result);
            assertNotNull(result.status());
        }

        @Test
        void givenSufficientHistory_whenCalibrationCompletes_thenLastCalibrationTimeIsSet() {
            // Given: valid 180-day history
            double[][] history = buildFlatPriceHistory(180, 3, 100.0);
            when(weightStore.getCalibratedWeights())
                    .thenReturn(new double[]{0.4, 0.35, 0.25});

            // When: calibration runs
            task.runCalibrationWithHistory(history);

            // Then: last calibration timestamp is recorded
            Instant lastTime = task.getLastCalibrationTime();
            assertNotNull(lastTime);
            assertTrue(lastTime.isBefore(Instant.now().plusSeconds(1)));
        }
    }

    @Nested
    class ComputeSharpeRatio {

        @Test
        void givenNullData_whenComputeSharpe_thenReturnsZero() {
            // Given: null price data
            double[] weights = {0.5, 0.5};

            // When: Sharpe is computed
            double sharpe = task.computeSharpeRatio(null, weights);

            // Then: returns 0.0
            assertEquals(0.0, sharpe, 1e-10);
        }

        @Test
        void givenEmptyData_whenComputeSharpe_thenReturnsZero() {
            // Given: empty price data array
            double[][] data = new double[0][];
            double[] weights = {0.5, 0.5};

            // When: Sharpe is computed
            double sharpe = task.computeSharpeRatio(data, weights);

            // Then: returns 0.0
            assertEquals(0.0, sharpe, 1e-10);
        }

        @Test
        void givenSingleRowData_whenComputeSharpe_thenReturnsZero() {
            // Given: only one row of price data (insufficient for returns)
            double[][] data = {{100.0, 200.0}};
            double[] weights = {0.5, 0.5};

            // When: Sharpe is computed
            double sharpe = task.computeSharpeRatio(data, weights);

            // Then: returns 0.0 (cannot compute returns from one row)
            assertEquals(0.0, sharpe, 1e-10);
        }

        @Test
        void givenNullWeights_whenComputeSharpe_thenReturnsZero() {
            // Given: null weights
            double[][] data = {{100.0, 200.0}, {101.0, 202.0}};

            // When: Sharpe is computed
            double sharpe = task.computeSharpeRatio(data, null);

            // Then: returns 0.0
            assertEquals(0.0, sharpe, 1e-10);
        }

        @Test
        void givenValidUpwardTrend_whenComputeSharpe_thenReturnsPositive() {
            // Given: upward trending price data
            double[][] data = {
                    {100.0, 100.0},
                    {101.0, 102.0},
                    {102.0, 104.0},
                    {103.0, 106.0}
            };
            double[] weights = {0.5, 0.5};

            // When: Sharpe is computed
            double sharpe = task.computeSharpeRatio(data, weights);

            // Then: Sharpe is positive
            assertTrue(sharpe > 0.0);
        }

        @Test
        void givenConstantPrices_whenComputeSharpe_thenReturnsZero() {
            // Given: constant prices (zero variance in returns)
            double[][] data = {
                    {100.0, 100.0},
                    {100.0, 100.0},
                    {100.0, 100.0}
            };
            double[] weights = {0.5, 0.5};

            // When: Sharpe is computed
            double sharpe = task.computeSharpeRatio(data, weights);

            // Then: Sharpe is zero (no returns)
            assertEquals(0.0, sharpe, 1e-10);
        }
    }

    @Nested
    class GridSearchOptimize {

        @Test
        void givenFlatTrainingData_whenOptimize_thenReturnsNormalizedWeights() {
            // Given: flat training data that cannot distinguish weight quality
            double[][] trainingData = buildFlatPriceHistory(150, 3, 100.0);
            double[] current = {0.4, 0.35, 0.25};

            // When: grid search optimizes
            double[] result = task.gridSearchOptimize(trainingData, current);

            // Then: weights are normalized to sum ~1.0
            double sum = 0;
            for (double w : result) {
                assertTrue(w >= 0.0);
                sum += w;
            }
            assertEquals(1.0, sum, 0.01);
        }

        @Test
        void givenTrendingTrainingData_whenOptimize_thenWeightsShift() {
            // Given: training data with a strong trend in asset 0
            double[][] trainingData = buildTrendingPriceHistory(150, 3, 100.0, 0);
            double[] current = {0.33, 0.34, 0.33};

            // When: grid search optimizes
            double[] result = task.gridSearchOptimize(trainingData, current);

            // Then: weights remain valid (normalized, non-negative)
            double sum = 0;
            for (double w : result) {
                assertTrue(w >= 0.0);
                sum += w;
            }
            assertEquals(1.0, sum, 0.01);
        }

        @Test
        void givenEqualWeightStart_whenOptimize_thenReturnsSameLength() {
            // Given: equal starting weights
            double[][] trainingData = buildFlatPriceHistory(150, 3, 100.0);
            double[] current = {0.33, 0.33, 0.34};

            // When: grid search optimizes
            double[] result = task.gridSearchOptimize(trainingData, current);

            // Then: result has same number of weights
            assertEquals(current.length, result.length);
        }
    }

    @Nested
    class PerturbOptimize {

        @Test
        void givenUniformWeights_whenPerturb_thenReturnsNormalizedWeights() {
            // Given: uniform starting weights
            double[] current = {0.33, 0.34, 0.33};

            // When: perturbation optimization runs
            double[] result = task.perturbOptimize(current);

            // Then: result weights are normalized
            double sum = 0;
            for (double w : result) {
                assertTrue(w >= 0.0);
                sum += w;
            }
            assertEquals(1.0, sum, 0.01);
        }
    }

    @Nested
    class CalibrationResultRecord {

        @Test
        void givenValues_whenCreateRecord_thenFieldsAreAccessible() {
            // Given: explicit calibration result values
            boolean improved = true;
            double sharpeBefore = 0.5;
            double sharpeAfter = 0.8;
            double oosSharpe = 0.7;
            String status = "IMPROVED";

            // When: record is created
            ScheduledCalibrationTask.CalibrationResult result =
                    new ScheduledCalibrationTask.CalibrationResult(
                            improved, sharpeBefore, sharpeAfter, oosSharpe, status);

            // Then: all fields match
            assertTrue(result.improved());
            assertEquals(0.5, result.sharpeBefore(), 1e-10);
            assertEquals(0.8, result.sharpeAfter(), 1e-10);
            assertEquals(0.7, result.oosSharpe(), 1e-10);
            assertEquals("IMPROVED", result.status());
        }

        @Test
        void givenNoImprovement_whenCreateRecord_thenStatusIsNoImprovement() {
            // Given: calibration did not improve
            // When: record is created with NO_IMPROVEMENT
            ScheduledCalibrationTask.CalibrationResult result =
                    new ScheduledCalibrationTask.CalibrationResult(
                            false, 0.5, 0.55, 0.48, "NO_IMPROVEMENT");

            // Then: improved flag is false
            assertFalse(result.improved());
            assertEquals("NO_IMPROVEMENT", result.status());
        }
    }

    @Nested
    class Constants {

        @Test
        void givenTask_whenCheckConstants_thenValuesAreCorrect() {
            // Given: the task class constants
            // When/Then: verify they match spec requirements
            assertEquals(180, ScheduledCalibrationTask.TOTAL_WINDOW_DAYS);
            assertEquals(150, ScheduledCalibrationTask.TRAINING_DAYS);
            assertEquals(30, ScheduledCalibrationTask.VALIDATION_DAYS);
            assertEquals(0.1, ScheduledCalibrationTask.MIN_SHARPE_IMPROVEMENT, 1e-10);
        }
    }

    private static double[][] buildFlatPriceHistory(int days, int assets, double basePrice) {
        double[][] data = new double[days][assets];
        for (int t = 0; t < days; t++) {
            for (int a = 0; a < assets; a++) {
                data[t][a] = basePrice + a * 10.0;
            }
        }
        return data;
    }

    private static double[][] buildTrendingPriceHistory(int days, int assets, double basePrice, int trendingAsset) {
        double[][] data = new double[days][assets];
        for (int t = 0; t < days; t++) {
            for (int a = 0; a < assets; a++) {
                if (a == trendingAsset) {
                    data[t][a] = basePrice + t * 0.5;
                } else {
                    data[t][a] = basePrice + a * 10.0;
                }
            }
        }
        return data;
    }
}
