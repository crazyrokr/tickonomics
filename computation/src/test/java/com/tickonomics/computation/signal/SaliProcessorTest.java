package com.tickonomics.computation.signal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SaliProcessorTest {

    @InjectMocks
    private SaliProcessor processor;

    @Nested
    class Process {
        @Test
        void givenBothBearish_whenProcess_thenSellNotSuppressed() {
            /* Given negative ILI and negative sentiment */
            /* When processing SALI */
            /* Then action is SELL, not suppressed */
            var result = processor.process(-0.5, -0.3);

            assertEquals(SaliProcessor.Action.SELL, result.action());
            assertFalse(result.suppressed());
            assertTrue(result.saliValue() < 0);
        }

        @Test
        void givenBothBullish_whenProcess_thenBuyNotSuppressed() {
            /* Given positive ILI and positive sentiment */
            /* When processing SALI */
            /* Then action is BUY, not suppressed */
            var result = processor.process(0.5, 0.3);

            assertEquals(SaliProcessor.Action.BUY, result.action());
            assertFalse(result.suppressed());
            assertTrue(result.saliValue() > 0);
        }

        @Test
        void givenDisagreeingSignals_whenProcess_thenHoldSuppressed() {
            /* Given ILI bearish but sentiment bullish */
            /* When processing SALI */
            /* Then action is HOLD with suppressed=true */
            var result = processor.process(-0.5, 0.3);

            assertEquals(SaliProcessor.Action.HOLD, result.action());
            assertTrue(result.suppressed());
        }

        @Test
        void givenBullishIliBearishSentiment_whenProcess_thenHoldSuppressed() {
            /* Given ILI bullish but sentiment bearish */
            /* When processing SALI */
            /* Then action is HOLD with suppressed=true */
            var result = processor.process(0.5, -0.3);

            assertEquals(SaliProcessor.Action.HOLD, result.action());
            assertTrue(result.suppressed());
        }

        @Test
        void givenStrongBearishAgreement_whenProcess_thenSellNotSuppressed() {
            /* Given strong bearish agreement from both signals */
            /* When processing SALI */
            /* Then action is SELL, not suppressed */
            var result = processor.process(-1.0, -0.9);

            assertEquals(SaliProcessor.Action.SELL, result.action());
            assertFalse(result.suppressed());
        }

        @Test
        void givenSellWithExtremeBullishSentiment_whenProcess_thenSellSuppressedToHold() {
            /* Given ILI bearish but sentiment extremely bullish (>0.8) */
            /* When processing SALI */
            /* Then action is HOLD because signals disagree, and suppressed=true */
            var result = processor.process(-0.5, 0.9);

            assertEquals(SaliProcessor.Action.HOLD, result.action());
            assertTrue(result.suppressed());
        }

        @Test
        void givenSaliComponents_whenProcess_thenWeightedCorrectly() {
            /* Given ILI = 1.0 and sentiment = 1.0 */
            /* When processing SALI */
            /* Then saliValue = 0.7*1.0 + 0.3*1.0 = 1.0, action BUY, not suppressed */
            var result = processor.process(1.0, 1.0);

            assertEquals(0.7, result.iliComponent(), 0.01);
            assertEquals(0.3, result.sentimentComponent(), 0.01);
            assertEquals(1.0, result.saliValue(), 0.01);
            assertEquals(SaliProcessor.Action.BUY, result.action());
            assertFalse(result.suppressed());
        }

        @Test
        void givenZeroScores_whenProcess_thenHoldSuppressed() {
            /* Given both ILI and sentiment at zero */
            /* When processing SALI */
            /* Then action is HOLD (neither bullish nor bearish), suppressed */
            var result = processor.process(0.0, 0.0);

            assertEquals(SaliProcessor.Action.HOLD, result.action());
            assertEquals(0.0, result.saliValue(), 0.01);
            assertTrue(result.suppressed());
        }
    }

    @Nested
    class DetermineAction {
        @Test
        void givenBothNegative_whenDetermine_thenSell() {
            /* Given negative ILI and negative sentiment */
            /* When determining action */
            /* Then result is SELL */
            assertEquals(SaliProcessor.Action.SELL,
                    processor.determineAction(-0.5, -0.3));
        }

        @Test
        void givenBothPositive_whenDetermine_thenBuy() {
            /* Given positive ILI and positive sentiment */
            /* When determining action */
            /* Then result is BUY */
            assertEquals(SaliProcessor.Action.BUY,
                    processor.determineAction(0.5, 0.3));
        }

        @Test
        void givenMixedSignals_whenDetermine_thenHold() {
            /* Given positive ILI but negative sentiment */
            /* When determining action */
            /* Then result is HOLD */
            assertEquals(SaliProcessor.Action.HOLD,
                    processor.determineAction(0.5, -0.3));
        }

        @Test
        void givenNegativeIliPositiveSentiment_whenDetermine_thenHold() {
            /* Given negative ILI but positive sentiment */
            /* When determining action */
            /* Then result is HOLD */
            assertEquals(SaliProcessor.Action.HOLD,
                    processor.determineAction(-0.5, 0.3));
        }
    }
}
