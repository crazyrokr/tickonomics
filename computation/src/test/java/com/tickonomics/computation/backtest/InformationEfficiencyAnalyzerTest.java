package com.tickonomics.computation.backtest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class InformationEfficiencyAnalyzerTest {

    @InjectMocks
    private InformationEfficiencyAnalyzer analyzer;

    private List<Double> generateReturns(int count, double value) {
        List<Double> returns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            returns.add(value);
        }
        return returns;
    }

    @Nested
    class Analyze {
        @Test
        void givenHighEfficiency_whenAnalyze_thenHighClass() {
            /* Given returns concentrated in the short window (indices 55-80) */
            /* When analyzing efficiency */
            /* Then PJR > 0.8 and class is HIGH */
            List<Double> returns = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                if (i >= 55 && i <= 80) {
                    returns.add(1.0);
                } else {
                    returns.add(0.0);
                }
            }
            // carShort (55..80) = 26*1.0 = 26, carLong (0..80) = 26*1.0 = 26, PJR = 1.0

            var result = analyzer.analyze(returns, 60);

            assertFalse(Double.isNaN(result.pjr()));
            assertTrue(result.pjr() > 0.8);
            assertEquals(InformationEfficiencyAnalyzer.EfficiencyClass.HIGH, result.efficiencyClass());
        }

        @Test
        void givenMediumEfficiency_whenAnalyze_thenMediumClass() {
            /* Given returns with partial concentration in short window */
            /* When analyzing efficiency */
            /* Then PJR is between 0.4 and 0.8, class is MEDIUM */
            List<Double> returns = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                if (i >= 55 && i <= 80) {
                    returns.add(1.0);
                } else if (i < 55) {
                    returns.add(0.5);
                } else {
                    returns.add(0.0);
                }
            }
            // carShort (55..80) = 26*1.0 = 26
            // carLong (0..80) = 55*0.5 + 26*1.0 = 27.5 + 26 = 53.5
            // PJR = 26/53.5 ~ 0.486 => MEDIUM

            var result = analyzer.analyze(returns, 60);

            assertEquals(InformationEfficiencyAnalyzer.EfficiencyClass.MEDIUM, result.efficiencyClass());
        }

        @Test
        void givenLowEfficiency_whenAnalyze_thenLowClass() {
            /* Given returns where short window is small vs long window */
            /* When analyzing efficiency */
            /* Then PJR < 0.4 and class is LOW */
            List<Double> returns = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                if (i >= 55 && i <= 75) {
                    returns.add(0.001);
                } else {
                    returns.add(0.01);
                }
            }

            var result = analyzer.analyze(returns, 60);

            assertEquals(InformationEfficiencyAnalyzer.EfficiencyClass.LOW, result.efficiencyClass());
        }

        @Test
        void givenNullReturns_whenAnalyze_thenLowClassWithNaN() {
            /* Given null returns */
            /* When analyzing */
            /* Then result is LOW with NaN values */
            var result = analyzer.analyze(null, 10);

            assertTrue(Double.isNaN(result.pjr()));
            assertEquals(InformationEfficiencyAnalyzer.EfficiencyClass.LOW, result.efficiencyClass());
        }

        @Test
        void givenEmptyReturns_whenAnalyze_thenLowClassWithNaN() {
            /* Given empty returns */
            /* When analyzing */
            /* Then result is LOW with NaN values */
            var result = analyzer.analyze(List.of(), 0);

            assertTrue(Double.isNaN(result.pjr()));
        }

        @Test
        void givenInvalidEventIndex_whenAnalyze_thenLowClassWithNaN() {
            /* Given negative event index */
            /* When analyzing */
            /* Then result is LOW with NaN */
            var result = analyzer.analyze(generateReturns(50, 0.01), -1);

            assertTrue(Double.isNaN(result.pjr()));
        }

        @Test
        void givenEventIndexOutOfRange_whenAnalyze_thenLowClassWithNaN() {
            /* Given event index beyond returns size */
            /* When analyzing */
            /* Then result is LOW with NaN */
            var result = analyzer.analyze(generateReturns(10, 0.01), 20);

            assertTrue(Double.isNaN(result.pjr()));
        }
    }

    @Nested
    class ComputeCar {
        @Test
        void givenUniformReturns_whenComputeCar_thenCorrectSum() {
            /* Given returns all equal to 0.01 */
            /* When computing CAR from 0 to 4 */
            /* Then result is 0.05 */
            var returns = generateReturns(10, 0.01);

            double car = analyzer.computeCar(returns, 0, 4);

            assertEquals(0.05, car, 0.0001);
        }

        @Test
        void givenStartBeyondSize_whenComputeCar_thenZero() {
            /* Given start index beyond list size */
            /* When computing CAR */
            /* Then result is zero */
            var returns = generateReturns(5, 0.01);

            double car = analyzer.computeCar(returns, 10, 15);

            assertEquals(0.0, car, 0.0001);
        }
    }

    @Nested
    class ClassifyEfficiency {
        @Test
        void givenPjrAbove08_whenClassify_thenHigh() {
            /* Given PJR > 0.8 */
            /* When classifying */
            /* Then class is HIGH */
            assertEquals(InformationEfficiencyAnalyzer.EfficiencyClass.HIGH,
                    analyzer.classifyEfficiency(0.9));
        }

        @Test
        void givenPjrBetween04And08_whenClassify_thenMedium() {
            /* Given PJR = 0.6 */
            /* When classifying */
            /* Then class is MEDIUM */
            assertEquals(InformationEfficiencyAnalyzer.EfficiencyClass.MEDIUM,
                    analyzer.classifyEfficiency(0.6));
        }

        @Test
        void givenPjrBelow04_whenClassify_thenLow() {
            /* Given PJR = 0.2 */
            /* When classifying */
            /* Then class is LOW */
            assertEquals(InformationEfficiencyAnalyzer.EfficiencyClass.LOW,
                    analyzer.classifyEfficiency(0.2));
        }

        @Test
        void givenPjrNaN_whenClassify_thenLow() {
            /* Given NaN PJR */
            /* When classifying */
            /* Then class is LOW */
            assertEquals(InformationEfficiencyAnalyzer.EfficiencyClass.LOW,
                    analyzer.classifyEfficiency(Double.NaN));
        }

        @Test
        void givenPjrExactly04_whenClassify_thenMedium() {
            /* Given PJR exactly 0.4 */
            /* When classifying */
            /* Then class is MEDIUM */
            assertEquals(InformationEfficiencyAnalyzer.EfficiencyClass.MEDIUM,
                    analyzer.classifyEfficiency(0.4));
        }

        @Test
        void givenPjrExactly08_whenClassify_thenMedium() {
            /* Given PJR exactly 0.8 */
            /* When classifying */
            /* Then class is MEDIUM (spec: HIGH is pjr > 0.8, so 0.8 is not strictly >) */
            assertEquals(InformationEfficiencyAnalyzer.EfficiencyClass.MEDIUM,
                    analyzer.classifyEfficiency(0.8));
        }
    }
}
