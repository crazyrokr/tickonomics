package com.tickonomics.computation.optimization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class FireflyWeightOptimizerTest {

    private static final double EPSILON = 1e-9;

    private FireflyWeightOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new FireflyWeightOptimizer();
    }

    @Nested
    @DisplayName("optimize - happy path")
    class OptimizeHappyPath {

        @Test
        @DisplayName("given valid returns and weights when optimize called then returns normalised weights")
            /** Given a matrix of historical returns and a valid current weight vector.
             *  When optimize is called.
             *  Then the result is a non-null weight vector whose elements sum to 1.0.
             */
        void givenValidReturnsAndWeights_whenOptimize_thenReturnsNormalisedWeights() {
            double[][] returns = {
                    {0.01, 0.02, -0.01},
                    {0.03, -0.01, 0.02},
                    {-0.02, 0.03, 0.01},
                    {0.04, 0.01, -0.03},
                    {0.02, -0.02, 0.04}
            };
            double[] currentWeights = {0.4, 0.35, 0.25};

            double[] result = optimizer.optimize(returns, currentWeights);

            assertNotNull(result);
            assertEquals(currentWeights.length, result.length);
            double sum = 0.0;
            for (double w : result) {
                assertTrue(w > 0.0, "each weight must be positive");
                sum += w;
            }
            assertEquals(1.0, sum, EPSILON, "weights must sum to 1.0");
        }

        @Test
        @DisplayName("given single-period returns when optimize called then returns valid weights")
            /** Given a single-row returns matrix and a valid weight vector.
             *  When optimize is called.
             *  Then the result is a normalised weight vector.
             */
        void givenSinglePeriodReturns_whenOptimize_thenReturnsValidWeights() {
            double[][] returns = {{0.05, 0.03, 0.02}};
            double[] currentWeights = {0.5, 0.3, 0.2};

            double[] result = optimizer.optimize(returns, currentWeights);

            assertNotNull(result);
            assertEquals(3, result.length);
            double sum = 0.0;
            for (double w : result) {
                sum += w;
            }
            assertEquals(1.0, sum, EPSILON);
        }

        @Test
        @DisplayName("given uniform returns when optimize called then drift constraint holds")
            /** Given uniform historical returns and an initial weight vector.
             *  When optimize is called.
             *  Then each output weight is within 10% drift of the corresponding current weight.
             */
        void givenUniformReturns_whenOptimize_thenDriftConstraintHolds() {
            double[][] returns = new double[20][3];
            for (int i = 0; i < 20; i++) {
                returns[i] = new double[]{0.01, 0.01, 0.01};
            }
            double[] currentWeights = {0.4, 0.35, 0.25};

            double[] result = optimizer.optimize(returns, currentWeights);

            for (int i = 0; i < result.length; i++) {
                double drift = Math.abs(result[i] - currentWeights[i]) / currentWeights[i];
                assertTrue(drift <= 0.11, "weight drift must be within ~10% of current weight");
            }
        }
    }

    @Nested
    @DisplayName("optimize - invalid and edge-case inputs")
    class OptimizeInvalidInputs {

        @Test
        @DisplayName("given null returns when optimize called then returns copy of current weights")
            /** Given null historical returns and a valid current weight vector.
             *  When optimize is called.
             *  Then the result is a copy of the current weights unchanged.
             */
        void givenNullReturns_whenOptimize_thenReturnsCurrentWeights() {
            double[] currentWeights = {0.4, 0.35, 0.25};

            double[] result = optimizer.optimize(null, currentWeights);

            assertArrayEquals(currentWeights, result, EPSILON);
        }

        @Test
        @DisplayName("given empty returns when optimize called then returns copy of current weights")
            /** Given an empty historical returns matrix and a valid weight vector.
             *  When optimize is called.
             *  Then the result is a copy of the current weights unchanged.
             */
        void givenEmptyReturns_whenOptimize_thenReturnsCurrentWeights() {
            double[] currentWeights = {0.4, 0.35, 0.25};

            double[] result = optimizer.optimize(new double[0][], currentWeights);

            assertArrayEquals(currentWeights, result, EPSILON);
        }

        @Test
        @DisplayName("given null current weights when optimize called then returns null")
            /** Given valid historical returns but null current weights.
             *  When optimize is called.
             *  Then the result is null.
             */
        void givenNullCurrentWeights_whenOptimize_thenReturnsNull() {
            double[][] returns = {{0.01, 0.02}};

            double[] result = optimizer.optimize(returns, null);

            assertEquals(null, result);
        }

        @Test
        @DisplayName("given empty current weights when optimize called then returns empty array")
            /** Given valid historical returns but an empty current weight vector.
             *  When optimize is called.
             *  Then the result is an empty array.
             */
        void givenEmptyCurrentWeights_whenOptimize_thenReturnsEmptyArray() {
            double[][] returns = {{0.01, 0.02}};

            double[] result = optimizer.optimize(returns, new double[0]);

            assertEquals(0, result.length);
        }
    }

    @Nested
    @DisplayName("computeSharpeRatio")
    class ComputeSharpeRatio {

        @Test
        @DisplayName("given positive mean returns when computeSharpeRatio called then returns positive ratio")
            /** Given weights and historical returns with positive mean portfolio return.
             *  When computeSharpeRatio is called.
             *  Then the result is a positive Sharpe ratio.
             */
        void givenPositiveMeanReturns_whenComputeSharpeRatio_thenReturnsPositive() {
            double[][] returns = {
                    {0.05, 0.02},
                    {0.03, 0.04},
                    {0.06, 0.01}
            };
            double[] weights = {0.6, 0.4};

            double sharpe = optimizer.computeSharpeRatio(weights, returns);

            assertTrue(sharpe > 0.0);
        }

        @Test
        @DisplayName("given zero returns when computeSharpeRatio called then returns zero")
            /** Given weights and all-zero historical returns.
             *  When computeSharpeRatio is called.
             *  Then the result is 0.0 because standard deviation is zero.
             */
        void givenZeroReturns_whenComputeSharpeRatio_thenReturnsZero() {
            double[][] returns = {
                    {0.0, 0.0},
                    {0.0, 0.0},
                    {0.0, 0.0}
            };
            double[] weights = {0.5, 0.5};

            double sharpe = optimizer.computeSharpeRatio(weights, returns);

            assertEquals(0.0, sharpe, EPSILON);
        }

        @Test
        @DisplayName("given empty returns when computeSharpeRatio called then returns zero")
            /** Given weights and an empty returns matrix.
             *  When computeSharpeRatio is called.
             *  Then the result is 0.0.
             */
        void givenEmptyReturns_whenComputeSharpeRatio_thenReturnsZero() {
            double[] weights = {0.5, 0.5};

            double sharpe = optimizer.computeSharpeRatio(weights, new double[0][]);

            assertEquals(0.0, sharpe, EPSILON);
        }

        @Test
        @DisplayName("given mixed positive and negative returns when computeSharpeRatio called then returns finite value")
            /** Given weights and returns with both positive and negative values.
             *  When computeSharpeRatio is called.
             *  Then the result is a finite number.
             */
        void givenMixedReturns_whenComputeSharpeRatio_thenReturnsFinite() {
            double[][] returns = {
                    {0.05, -0.03},
                    {-0.02, 0.04},
                    {0.03, 0.01},
                    {-0.01, -0.02},
                    {0.04, 0.03}
            };
            double[] weights = {0.5, 0.5};

            double sharpe = optimizer.computeSharpeRatio(weights, returns);

            assertTrue(Double.isFinite(sharpe));
        }
    }

    @Nested
    @DisplayName("initialisePopulation")
    class InitialisePopulation {

        @Test
        @DisplayName("given current weights when initialisePopulation called then first firefly equals current weights")
            /** Given a current weight vector.
             *  When initialisePopulation is called.
             *  Then the first firefly in the population equals the current weights.
             */
        void givenCurrentWeights_whenInitialisePopulation_thenFirstFireflyMatchesCurrent() {
            double[] currentWeights = {0.4, 0.35, 0.25};

            double[][] population = optimizer.initialisePopulation(currentWeights, 3);

            assertEquals(15, population.length);
            assertArrayEquals(currentWeights, population[0], EPSILON);
        }

        @Test
        @DisplayName("given current weights when initialisePopulation called then all fireflies are normalised")
            /** Given a current weight vector.
             *  When initialisePopulation is called.
             *  Then every firefly in the population has weights that sum to 1.0.
             */
        void givenCurrentWeights_whenInitialisePopulation_thenAllNormalised() {
            double[] currentWeights = {0.4, 0.35, 0.25};

            double[][] population = optimizer.initialisePopulation(currentWeights, 3);

            for (int i = 0; i < population.length; i++) {
                double sum = 0.0;
                for (double w : population[i]) {
                    sum += w;
                }
                assertEquals(1.0, sum, EPSILON, "firefly " + i + " must be normalised");
            }
        }

        @Test
        @DisplayName("given current weights when initialisePopulation called then all weights are positive")
            /** Given a current weight vector.
             *  When initialisePopulation is called.
             *  Then every weight in every firefly is strictly positive.
             */
        void givenCurrentWeights_whenInitialisePopulation_thenAllWeightsPositive() {
            double[] currentWeights = {0.4, 0.35, 0.25};

            double[][] population = optimizer.initialisePopulation(currentWeights, 3);

            for (int i = 0; i < population.length; i++) {
                for (int j = 0; j < population[i].length; j++) {
                    assertTrue(population[i][j] > 0.0,
                            "firefly " + i + " weight " + j + " must be positive");
                }
            }
        }
    }

    @Nested
    @DisplayName("enforceDriftConstraint")
    class EnforceDriftConstraint {

        @Test
        @DisplayName("given candidate within drift when enforceDriftConstraint called then candidate unchanged")
            /** Given a candidate weight vector within the 10% drift limit of current weights.
             *  When enforceDriftConstraint is called.
             *  Then the candidate weights are returned essentially unchanged.
             */
        void givenCandidateWithinDrift_whenEnforceDriftConstraint_thenCandidateUnchanged() {
            double[] current = {0.4, 0.35, 0.25};
            double[] candidate = {0.42, 0.34, 0.24};

            double[] result = optimizer.enforceDriftConstraint(candidate, current);

            double drift0 = Math.abs(result[0] - current[0]) / current[0];
            assertTrue(drift0 <= 0.10 + EPSILON);
        }

        @Test
        @DisplayName("given candidate exceeding drift when enforceDriftConstraint called then candidate clamped")
            /** Given a candidate weight vector exceeding the 10% drift limit.
             *  When enforceDriftConstraint is called.
             *  Then the candidate weights are clamped to the 10% boundary.
             */
        void givenCandidateExceedingDrift_whenEnforceDriftConstraint_thenCandidateClamped() {
            double[] current = {0.4, 0.35, 0.25};
            double[] candidate = {0.6, 0.1, 0.3};

            double[] result = optimizer.enforceDriftConstraint(candidate, current);

            for (int i = 0; i < result.length; i++) {
                double maxDrift = current[i] * 0.10;
                assertTrue(result[i] >= current[i] - maxDrift - EPSILON,
                        "result[" + i + "] must be above drift lower bound");
                assertTrue(result[i] <= current[i] + maxDrift + EPSILON,
                        "result[" + i + "] must be below drift upper bound");
            }
        }

        @Test
        @DisplayName("given zero weights in candidate when enforceDriftConstraint called then floored to 0.001")
            /** Given a candidate with a zero weight.
             *  When enforceDriftConstraint is called.
             *  Then that weight is floored to 0.001.
             */
        void givenZeroWeight_whenEnforceDriftConstraint_thenFloored() {
            double[] current = {0.5, 0.3, 0.2};
            double[] candidate = {0.0, 0.6, 0.4};

            double[] result = optimizer.enforceDriftConstraint(candidate, current);

            for (double w : result) {
                assertTrue(w >= 0.001, "weight must be floored to at least 0.001");
            }
        }
    }

    @Nested
    @DisplayName("normalize")
    class Normalize {

        @Test
        @DisplayName("given positive weights when normalize called then weights sum to 1.0")
            /** Given a weight vector with positive values that do not sum to 1.0.
             *  When normalize is called.
             *  Then the weights are scaled so they sum to exactly 1.0.
             */
        void givenPositiveWeights_whenNormalize_thenSumToOne() {
            double[] weights = {2.0, 3.0, 5.0};

            optimizer.normalize(weights);

            assertEquals(1.0, weights[0] + weights[1] + weights[2], EPSILON);
            assertEquals(0.2, weights[0], EPSILON);
            assertEquals(0.3, weights[1], EPSILON);
            assertEquals(0.5, weights[2], EPSILON);
        }

        @Test
        @DisplayName("given already normalised weights when normalize called then unchanged")
            /** Given a weight vector that already sums to 1.0.
             *  When normalize is called.
             *  Then the weights remain unchanged.
             */
        void givenAlreadyNormalisedWeights_whenNormalize_thenUnchanged() {
            double[] weights = {0.4, 0.35, 0.25};

            optimizer.normalize(weights);

            assertEquals(0.4, weights[0], EPSILON);
            assertEquals(0.35, weights[1], EPSILON);
            assertEquals(0.25, weights[2], EPSILON);
        }

        @Test
        @DisplayName("given all-zero weights when normalize called then uniform weights assigned")
            /** Given an all-zero weight vector.
             *  When normalize is called.
             *  Then each weight is set to 1/n (uniform distribution).
             */
        void givenAllZeroWeights_whenNormalize_thenUniformAssigned() {
            double[] weights = {0.0, 0.0, 0.0};

            optimizer.normalize(weights);

            assertEquals(1.0 / 3.0, weights[0], EPSILON);
            assertEquals(1.0 / 3.0, weights[1], EPSILON);
            assertEquals(1.0 / 3.0, weights[2], EPSILON);
        }
    }

    @Nested
    @DisplayName("findBestIndex")
    class FindBestIndex {

        @Test
        @DisplayName("given fitness array when findBestIndex called then returns index of maximum")
            /** Given a fitness array with a clear maximum.
             *  When findBestIndex is called.
             *  Then the index of the maximum value is returned.
             */
        void givenFitnessArray_whenFindBestIndex_thenReturnsMaxIndex() {
            double[] fitness = {0.5, 1.2, 0.8, 2.1, 1.0};

            int best = optimizer.findBestIndex(fitness);

            assertEquals(3, best);
        }

        @Test
        @DisplayName("given equal fitness values when findBestIndex called then returns first index")
            /** Given a fitness array where all values are equal.
             *  When findBestIndex is called.
             *  Then index 0 is returned.
             */
        void givenEqualFitnessValues_whenFindBestIndex_thenReturnsFirstIndex() {
            double[] fitness = {1.0, 1.0, 1.0};

            int best = optimizer.findBestIndex(fitness);

            assertEquals(0, best);
        }

        @Test
        @DisplayName("given single-element fitness array when findBestIndex called then returns zero")
            /** Given a single-element fitness array.
             *  When findBestIndex is called.
             *  Then index 0 is returned.
             */
        void givenSingleElementFitness_whenFindBestIndex_thenReturnsZero() {
            double[] fitness = {3.14};

            int best = optimizer.findBestIndex(fitness);

            assertEquals(0, best);
        }
    }

    @Nested
    @DisplayName("euclideanDistance")
    class EuclideanDistance {

        @Test
        @DisplayName("given identical vectors when euclideanDistance called then returns zero")
            /** Given two identical weight vectors.
             *  When euclideanDistance is called.
             *  Then the distance is 0.0.
             */
        void givenIdenticalVectors_whenEuclideanDistance_thenReturnsZero() {
            double[] a = {0.4, 0.35, 0.25};
            double[] b = {0.4, 0.35, 0.25};

            double distance = optimizer.euclideanDistance(a, b);

            assertEquals(0.0, distance, EPSILON);
        }

        @Test
        @DisplayName("given different vectors when euclideanDistance called then returns correct distance")
            /** Given two different weight vectors.
             *  When euclideanDistance is called.
             *  Then the Euclidean distance is correctly computed.
             */
        void givenDifferentVectors_whenEuclideanDistance_thenReturnsCorrectDistance() {
            double[] a = {0.0, 0.0, 0.0};
            double[] b = {3.0, 4.0, 0.0};

            double distance = optimizer.euclideanDistance(a, b);

            assertEquals(5.0, distance, EPSILON);
        }
    }

    @Nested
    @DisplayName("custom configuration")
    class CustomConfiguration {

        @Test
        @DisplayName("given small population and generations when optimize called then terminates successfully")
            /** Given a FireflyWeightOptimizer configured with population=3 and generations=5.
             *  When optimize is called with valid inputs.
             *  Then it terminates and returns normalised weights.
             */
        void givenSmallPopulationAndGenerations_whenOptimize_thenTerminatesSuccessfully() {
            FireflyWeightOptimizer smallOptimizer = new FireflyWeightOptimizer(3, 5, 0.1, 0.5);
            double[][] returns = {
                    {0.01, 0.02},
                    {-0.01, 0.03},
                    {0.02, -0.01}
            };
            double[] currentWeights = {0.6, 0.4};

            double[] result = smallOptimizer.optimize(returns, currentWeights);

            assertNotNull(result);
            assertEquals(2, result.length);
            double sum = result[0] + result[1];
            assertEquals(1.0, sum, EPSILON);
        }
    }

    @Nested
    @DisplayName("reproducibility and determinism")
    class Reproducibility {

        @Test
        @DisplayName("given same inputs when optimize called multiple times then drift constraint approximately holds")
            /** Given the same returns and weights used across multiple calls.
             *  When optimize is called repeatedly.
             *  Then every result stays within approximately 15% drift from the current weights.
             */
        void givenSameInputs_whenOptimizeCalledMultipleTimes_thenDriftConstraintApproximatelyHolds() {
            double[][] returns = {
                    {0.02, 0.01, 0.03},
                    {-0.01, 0.03, 0.01},
                    {0.03, -0.02, 0.02},
                    {0.01, 0.02, -0.01},
                    {-0.02, 0.01, 0.04}
            };
            double[] currentWeights = {0.4, 0.35, 0.25};

            for (int trial = 0; trial < 5; trial++) {
                double[] result = optimizer.optimize(returns, currentWeights);
                for (int i = 0; i < result.length; i++) {
                    double driftFraction = Math.abs(result[i] - currentWeights[i]) / currentWeights[i];
                    assertTrue(driftFraction <= 0.15,
                            "trial " + trial + " weight " + i + " drift " + driftFraction + " exceeds 15%");
                }
            }
        }
    }
}
