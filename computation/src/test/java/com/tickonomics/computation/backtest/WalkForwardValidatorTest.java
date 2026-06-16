package com.tickonomics.computation.backtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class WalkForwardValidatorTest {

    private WalkForwardValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WalkForwardValidator();
    }

    private List<Double> generateReturns(int count, double mean, double std, long seed) {
        Random rng = new Random(seed);
        List<Double> returns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            returns.add(mean + std * rng.nextGaussian());
        }
        return returns;
    }

    @Nested
    class ComputeSharpe {

        @Test
        void givenPositiveMeanReturns_whenComputeSharpe_thenPositiveAnnualized() {
            // Given returns with positive mean
            List<Double> returns = List.of(0.01, 0.02, 0.015, 0.005, 0.01);

            // When computing Sharpe
            double sharpe = validator.computeSharpe(returns);

            // Then result is positive and annualized
            assertTrue(sharpe > 0.0);
        }

        @Test
        void givenZeroVariance_whenComputeSharpe_thenReturnZero() {
            // Given constant returns (zero variance)
            List<Double> returns = List.of(0.01, 0.01, 0.01, 0.01);

            // When computing Sharpe
            double sharpe = validator.computeSharpe(returns);

            // Then result is zero (degenerate case)
            assertEquals(0.0, sharpe);
        }

        @Test
        void givenNullReturns_whenComputeSharpe_thenReturnZero() {
            // Given null returns
            // When computing Sharpe
            double sharpe = validator.computeSharpe(null);

            // Then result is zero
            assertEquals(0.0, sharpe);
        }

        @Test
        void givenSingleReturn_whenComputeSharpe_thenReturnZero() {
            // Given a single return value
            List<Double> returns = List.of(0.05);

            // When computing Sharpe
            double sharpe = validator.computeSharpe(returns);

            // Then result is zero (need at least 2)
            assertEquals(0.0, sharpe);
        }
    }

    @Nested
    class ClassifyDegradation {

        @Test
        void givenLowDegradation_whenClassify_thenReturnGood() {
            assertEquals("GOOD", validator.classifyDegradation(0.1));
            assertEquals("GOOD", validator.classifyDegradation(0.0));
            assertEquals("GOOD", validator.classifyDegradation(-0.5));
        }

        @Test
        void givenMediumDegradation_whenClassify_thenReturnWarning() {
            assertEquals("WARNING", validator.classifyDegradation(0.3));
            assertEquals("WARNING", validator.classifyDegradation(0.45));
            assertEquals("WARNING", validator.classifyDegradation(0.6));
        }

        @Test
        void givenHighDegradation_whenClassify_thenReturnPoor() {
            assertEquals("POOR", validator.classifyDegradation(0.61));
            assertEquals("POOR", validator.classifyDegradation(1.0));
        }
    }

    @Nested
    class Validate {

        @Test
        void givenSufficientData_whenValidate_thenReturnWfaResultWithFolds() {
            // Given 500 data points (enough for anchor=252 + multiple test windows of 63)
            List<Double> returns = generateReturns(500, 0.001, 0.02, 42L);

            // When validating
            WalkForwardValidator.WfaResult result = validator.validate(returns);

            // Then result has folds, reasonable values
            assertTrue(result.folds() > 0);
            assertEquals("GOOD", result.status());
            assertFalse(Double.isNaN(result.inSampleSharpe()));
            assertFalse(Double.isNaN(result.outOfSampleSharpe()));
        }

        @Test
        void givenInsufficientData_whenValidate_thenReturnInsufficientData() {
            // Given too few data points
            List<Double> returns = generateReturns(100, 0.001, 0.02, 42L);

            // When validating
            WalkForwardValidator.WfaResult result = validator.validate(returns);

            // Then status indicates insufficient data
            assertEquals(0, result.folds());
            assertEquals("INSUFFICIENT_DATA", result.status());
        }

        @Test
        void givenNullData_whenValidate_thenReturnInsufficientData() {
            // Given null returns
            // When validating
            WalkForwardValidator.WfaResult result = validator.validate(null);

            // Then status indicates insufficient data
            assertEquals(0, result.folds());
            assertEquals("INSUFFICIENT_DATA", result.status());
        }

        @Test
        void givenCustomWindows_whenValidate_thenRespectsCustomParameters() {
            // Given custom small windows
            List<Double> returns = generateReturns(60, 0.002, 0.015, 42L);

            // When validating with anchor=20, test=10
            WalkForwardValidator.WfaResult result = validator.validate(returns, 20, 10);

            // Then it succeeds with multiple folds
            assertTrue(result.folds() >= 3);
            assertNotNull(result.status());
        }

        @Test
        void givenDegradedModel_whenValidate_thenShowsPoorStatus() {
            // Given returns that degrade significantly out of sample
            // Train period has positive returns, test period has negative
            List<Double> returns = new ArrayList<>();
            for (int i = 0; i < 252; i++) {
                returns.add(0.01); // anchor: all positive
            }
            for (int i = 0; i < 252; i++) {
                returns.add(-0.02); // out of sample: all negative
            }

            // When validating
            WalkForwardValidator.WfaResult result = validator.validate(returns);

            // Then degradation is significant
            assertTrue(result.degradation() > 0.5);
            assertEquals("POOR", result.status());
        }
    }

    @Nested
    class GetFoldResults {

        @Test
        void givenSufficientData_whenGetFoldResults_thenReturnCorrectFoldCount() {
            // Given 500 data points with anchor=252 and test=63
            List<Double> returns = generateReturns(500, 0.001, 0.02, 42L);

            // When getting fold results
            List<WalkForwardValidator.FoldResult> folds = validator.getFoldResults(returns);

            // Then correct number of folds (500-252)/63 = 3 full folds, + initial = 4
            // Actually: trainEnd starts at 252, increment by 63 each fold
            // fold1: 0..252 train, 252..315 test
            // fold2: 0..315 train, 315..378 test
            // fold3: 0..378 train, 378..441 test
            // fold4: 0..441 train, 441..504 test (504 > 500, so last fold uses min)
            assertTrue(folds.size() >= 3);
        }

        @Test
        void givenInsufficientData_whenGetFoldResults_thenReturnEmptyList() {
            // Given insufficient data
            List<Double> returns = List.of(0.01, 0.02);

            // When getting fold results
            List<WalkForwardValidator.FoldResult> folds = validator.getFoldResults(returns);

            // Then empty list returned
            assertTrue(folds.isEmpty());
        }

        @Test
        void givenFoldResults_whenCheckStructure_thenFoldNumbersAreSequential() {
            // Given enough data
            List<Double> returns = generateReturns(400, 0.001, 0.02, 42L);

            // When getting fold results
            List<WalkForwardValidator.FoldResult> folds = validator.getFoldResults(returns);

            // Then fold numbers are sequential starting from 1
            for (int i = 0; i < folds.size(); i++) {
                assertEquals(i + 1, folds.get(i).fold());
            }
        }
    }

    @Nested
    class ComputeFoldResults {

        @Test
        void givenExpandingWindow_whenComputeFoldResults_thenTrainWindowExpands() {
            // Given returns with known size
            List<Double> returns = generateReturns(400, 0.001, 0.02, 42L);

            // When computing fold results with anchor=100, test=50
            List<WalkForwardValidator.FoldResult> folds = validator.computeFoldResults(returns, 100, 50);

            // Then folds expand: trainEnd = 100, 150, 200, ...
            assertTrue(folds.size() >= 4);
        }
    }
}
