package com.tickonomics.computation.execution;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ComparativeExecutionAnalysisTest {

    @InjectMocks
    private ComparativeExecutionAnalysis analysis;

    private ComparativeExecutionAnalysis.ExecutionSlippage slippage(String type, double bps, int fills) {
        return new ComparativeExecutionAnalysis.ExecutionSlippage(type, bps, fills);
    }

    @Nested
    class Compare {
        @Test
        void givenPassiveMuchBetter_whenCompare_thenPreferPassive() {
            /* Given passive slippage much lower than aggressive */
            /* When comparing */
            /* Then recommendation is PREFER_PASSIVE */
            var passive = List.of(
                    slippage("PASSIVE", 2.0, 50),
                    slippage("PASSIVE", 3.0, 40));
            var aggressive = List.of(
                    slippage("AGGRESSIVE", 8.0, 30),
                    slippage("AGGRESSIVE", 10.0, 20));

            var result = analysis.compare(passive, aggressive);

            assertEquals(2.5, result.passiveSlippageBps(), 0.01);
            assertEquals(9.0, result.aggressiveSlippageBps(), 0.01);
            assertEquals(6.5, result.slippageSavingsBps(), 0.01);
            assertEquals(ComparativeExecutionAnalysis.Recommendation.PREFER_PASSIVE, result.recommendation());
        }

        @Test
        void givenAggressiveBetter_whenCompare_thenPreferAggressive() {
            /* Given aggressive slippage lower than passive by > 1 bps */
            /* When comparing */
            /* Then recommendation is PREFER_AGGRESSIVE */
            var passive = List.of(slippage("PASSIVE", 10.0, 10));
            var aggressive = List.of(slippage("AGGRESSIVE", 5.0, 20));

            var result = analysis.compare(passive, aggressive);

            assertEquals(-5.0, result.slippageSavingsBps(), 0.01);
            assertEquals(ComparativeExecutionAnalysis.Recommendation.PREFER_AGGRESSIVE, result.recommendation());
        }

        @Test
        void givenSimilarSlippage_whenCompare_thenNeutral() {
            /* Given similar slippage for both */
            /* When comparing */
            /* Then recommendation is NEUTRAL */
            var passive = List.of(slippage("PASSIVE", 5.0, 30));
            var aggressive = List.of(slippage("AGGRESSIVE", 6.0, 30));

            var result = analysis.compare(passive, aggressive);

            assertEquals(1.0, result.slippageSavingsBps(), 0.01);
            assertEquals(ComparativeExecutionAnalysis.Recommendation.NEUTRAL, result.recommendation());
        }

        @Test
        void givenEmptyPassive_whenCompare_thenZeroPassiveSlippage() {
            /* Given empty passive executions */
            /* When comparing */
            /* Then passive slippage is zero */
            var aggressive = List.of(slippage("AGGRESSIVE", 5.0, 10));

            var result = analysis.compare(List.of(), aggressive);

            assertEquals(0.0, result.passiveSlippageBps(), 0.01);
        }

        @Test
        void givenBothEmpty_whenCompare_thenNeutral() {
            /* Given both empty lists */
            /* When comparing */
            /* Then recommendation is NEUTRAL */
            var result = analysis.compare(List.of(), List.of());

            assertEquals(0.0, result.passiveSlippageBps(), 0.01);
            assertEquals(0.0, result.aggressiveSlippageBps(), 0.01);
            assertEquals(0.0, result.slippageSavingsBps(), 0.01);
            assertEquals(ComparativeExecutionAnalysis.Recommendation.NEUTRAL, result.recommendation());
        }
    }

    @Nested
    class DetermineRecommendation {
        @Test
        void givenSavingsAbove2AndSufficientFills_whenDetermine_thenPreferPassive() {
            /* Given savings = 3 bps and passive fills > aggressive * 0.5 */
            /* When determining recommendation */
            /* Then result is PREFER_PASSIVE */
            assertEquals(ComparativeExecutionAnalysis.Recommendation.PREFER_PASSIVE,
                    analysis.determineRecommendation(3.0, 60, 100));
        }

        @Test
        void givenSavingsAbove2ButInsufficientFills_whenDetermine_thenNeutral() {
            /* Given savings = 3 bps but passive fills < aggressive * 0.5 */
            /* When determining recommendation */
            /* Then result is NEUTRAL */
            assertEquals(ComparativeExecutionAnalysis.Recommendation.NEUTRAL,
                    analysis.determineRecommendation(3.0, 10, 100));
        }

        @Test
        void givenNegativeSavingsBelow1_whenDetermine_thenPreferAggressive() {
            /* Given savings = -2 bps */
            /* When determining recommendation */
            /* Then result is PREFER_AGGRESSIVE */
            assertEquals(ComparativeExecutionAnalysis.Recommendation.PREFER_AGGRESSIVE,
                    analysis.determineRecommendation(-2.0, 10, 10));
        }

        @Test
        void givenSavingsExactlyNegative1_whenDetermine_thenNeutral() {
            /* Given savings = -1.0 exactly */
            /* When determining recommendation */
            /* Then result is NEUTRAL (not strictly < -1) */
            assertEquals(ComparativeExecutionAnalysis.Recommendation.NEUTRAL,
                    analysis.determineRecommendation(-1.0, 10, 10));
        }
    }

    @Nested
    class AverageSlippage {
        @Test
        void givenNullList_whenAverageSlippage_thenZero() {
            /* Given null list */
            /* When computing average slippage */
            /* Then result is zero */
            assertEquals(0.0, analysis.averageSlippage(null), 0.01);
        }

        @Test
        void givenEmptyList_whenAverageSlippage_thenZero() {
            /* Given empty list */
            /* When computing average slippage */
            /* Then result is zero */
            assertEquals(0.0, analysis.averageSlippage(List.of()), 0.01);
        }
    }

    @Nested
    class TotalFills {
        @Test
        void givenNullList_whenTotalFills_thenZero() {
            /* Given null list */
            /* When computing total fills */
            /* Then result is zero */
            assertEquals(0, analysis.totalFills(null));
        }
    }
}
