package com.tickonomics.computation.signal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class VotingClassifierTest {

    private VotingClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new VotingClassifier(true);
    }

    @Nested
    class ResolveAction {

        @Test
        void givenBothBuy_whenResolve_thenReturnActionableBuy() {
            // Given both signals are BUY
            // When resolving action
            String result = classifier.resolveAction("BUY", "BUY");

            // Then result is ACTIONABLE_BUY
            assertEquals("ACTIONABLE_BUY", result);
        }

        @Test
        void givenBothSell_whenResolve_thenReturnActionableSell() {
            // Given both signals are SELL
            // When resolving action
            String result = classifier.resolveAction("SELL", "SELL");

            // Then result is ACTIONABLE_SELL
            assertEquals("ACTIONABLE_SELL", result);
        }

        @Test
        void givenDisagreeingSignals_whenResolve_thenReturnConflictHold() {
            // Given ILI=BUY, ML=SELL
            // When resolving action
            String result = classifier.resolveAction("BUY", "SELL");

            // Then result is CONFLICT_HOLD
            assertEquals("CONFLICT_HOLD", result);
        }

        @Test
        void givenIliBuyMlNeutral_whenResolve_thenReturnInsufficientConviction() {
            // Given ILI=BUY, ML=NEUTRAL
            // When resolving action
            String result = classifier.resolveAction("BUY", "NEUTRAL");

            // Then result is INSUFFICIENT_CONVICTION
            assertEquals("INSUFFICIENT_CONVICTION", result);
        }

        @Test
        void givenIliNeutralMlBuy_whenResolve_thenReturnInsufficientConviction() {
            // Given ILI=NEUTRAL, ML=BUY
            // When resolving action
            String result = classifier.resolveAction("NEUTRAL", "BUY");

            // Then result is INSUFFICIENT_CONVICTION
            assertEquals("INSUFFICIENT_CONVICTION", result);
        }

        @Test
        void givenNullIli_whenResolve_thenReturnInsufficientConviction() {
            // Given null ILI signal
            // When resolving action
            String result = classifier.resolveAction(null, "BUY");

            // Then result is INSUFFICIENT_CONVICTION
            assertEquals("INSUFFICIENT_CONVICTION", result);
        }

        @Test
        void givenNullMl_whenResolve_thenReturnInsufficientConviction() {
            // Given null ML signal
            // When resolving action
            String result = classifier.resolveAction("BUY", null);

            // Then result is INSUFFICIENT_CONVICTION
            assertEquals("INSUFFICIENT_CONVICTION", result);
        }

        @Test
        void givenCaseInsensitiveSignals_whenResolve_thenMatchRegardlessOfCase() {
            // Given mixed-case signals
            // When resolving action
            String result = classifier.resolveAction("buy", "Buy");

            // Then result matches as BUY
            assertEquals("ACTIONABLE_BUY", result);
        }
    }

    @Nested
    class Vote {

        @Test
        void givenEnabledAndBothBuy_whenVote_thenReturnActionableBuy() {
            // Given enabled classifier and BUY/BUY signals
            // When voting
            VotingClassifier.VoteResult result = classifier.vote("BUY", "BUY");

            // Then actionable is ACTIONABLE_BUY
            assertEquals("ACTIONABLE_BUY", result.actionable());
            assertEquals("BUY", result.iliSignal());
            assertEquals("BUY", result.mlSignal());
            assertEquals(2, result.agreementCount());
            assertEquals("STRONG_BUY_CONSENSUS", result.recommendation());
        }

        @Test
        void givenEnabledAndBothSell_whenVote_thenReturnActionableSell() {
            // Given enabled classifier and SELL/SELL signals
            // When voting
            VotingClassifier.VoteResult result = classifier.vote("SELL", "SELL");

            // Then actionable is ACTIONABLE_SELL
            assertEquals("ACTIONABLE_SELL", result.actionable());
            assertEquals("STRONG_SELL_CONSENSUS", result.recommendation());
        }

        @Test
        void givenEnabledAndConflict_whenVote_thenReturnConflictHold() {
            // Given enabled classifier and BUY/SELL signals
            // When voting
            VotingClassifier.VoteResult result = classifier.vote("BUY", "SELL");

            // Then actionable is CONFLICT_HOLD
            assertEquals("CONFLICT_HOLD", result.actionable());
            assertEquals(1, result.agreementCount());
            assertEquals("SIGNALS_DIVERGE_HOLD", result.recommendation());
        }

        @Test
        void givenEnabledAndNeutral_whenVote_thenReturnInsufficientConviction() {
            // Given enabled classifier and NEUTRAL signal
            // When voting
            VotingClassifier.VoteResult result = classifier.vote("NEUTRAL", "BUY");

            // Then actionable is INSUFFICIENT_CONVICTION
            assertEquals("INSUFFICIENT_CONVICTION", result.actionable());
            assertEquals("NEUTRAL_SIGNAL_NO_ACTION", result.recommendation());
        }

        @Test
        void givenDisabled_whenVote_thenPassthroughIli() {
            // Given disabled classifier
            VotingClassifier disabled = new VotingClassifier(false);

            // When voting
            VotingClassifier.VoteResult result = disabled.vote("BUY", "SELL");

            // Then ILI signal passes through
            assertEquals("BUY", result.actionable());
            assertEquals("DISABLED_PASSTHROUGH", result.recommendation());
        }
    }

    @Nested
    class ToggleEnabled {

        @Test
        void givenDefaultConstructor_whenCheckEnabled_thenReturnFalse() {
            // Given default constructor
            VotingClassifier defaultClassifier = new VotingClassifier();

            // Then enabled is false
            assertFalse(defaultClassifier.isEnabled());
        }

        @Test
        void givenDisabled_whenSetEnabled_thenReturnEnabled() {
            // Given disabled classifier
            VotingClassifier toggleable = new VotingClassifier(false);
            assertFalse(toggleable.isEnabled());

            // When enabling
            toggleable.setEnabled(true);

            // Then enabled
            assertTrue(toggleable.isEnabled());
        }

        @Test
        void givenEnabled_whenSetEnabledFalse_thenBecomesDisabled() {
            // Given enabled classifier
            assertTrue(classifier.isEnabled());

            // When disabling
            classifier.setEnabled(false);

            // Then disabled
            assertFalse(classifier.isEnabled());
        }
    }

    @Nested
    class CountAgreements {

        @Test
        void givenSameSignals_whenCountAgreements_thenReturnTwo() {
            // Given matching signals
            // When counting agreements
            int count = classifier.countAgreements("BUY", "BUY");

            // Then count is 2
            assertEquals(2, count);
        }

        @Test
        void givenDifferentSignals_whenCountAgreements_thenReturnOne() {
            // Given different signals
            // When counting agreements
            int count = classifier.countAgreements("BUY", "SELL");

            // Then count is 1
            assertEquals(1, count);
        }

        @Test
        void givenNullSignals_whenCountAgreements_thenReturnZero() {
            // Given null signals
            // When counting agreements
            assertEquals(0, classifier.countAgreements(null, "BUY"));
            assertEquals(0, classifier.countAgreements("BUY", null));
            assertEquals(0, classifier.countAgreements(null, null));
        }
    }

    @Nested
    class BuildRecommendation {

        @Test
        void givenEachActionable_whenBuildRecommendation_thenReturnsCorrectString() {
            // Given each actionable type
            // When building recommendations
            assertEquals("STRONG_BUY_CONSENSUS",
                    classifier.buildRecommendation("BUY", "BUY", "ACTIONABLE_BUY"));
            assertEquals("STRONG_SELL_CONSENSUS",
                    classifier.buildRecommendation("SELL", "SELL", "ACTIONABLE_SELL"));
            assertEquals("SIGNALS_DIVERGE_HOLD",
                    classifier.buildRecommendation("BUY", "SELL", "CONFLICT_HOLD"));
            assertEquals("NEUTRAL_SIGNAL_NO_ACTION",
                    classifier.buildRecommendation("NEUTRAL", "BUY", "INSUFFICIENT_CONVICTION"));
            assertEquals("NO_RECOMMENDATION",
                    classifier.buildRecommendation("X", "Y", "UNKNOWN"));
        }
    }
}
