package com.tickonomics.computation.kpi;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PriceImpactKpiTest {

    @InjectMocks
    private PriceImpactKpi kpi;

    @Nested
    class ComputeImpact {
        @Test
        void givenBuyAboveMidpoint_whenComputeImpact_thenPositiveImpact() {
            /* Given BUY execution above NBBO midpoint */
            /* When computing impact */
            /* Then impact is positive (exec - mid) / mid * 10000 */
            var result = kpi.computeImpact(99.0, 101.0, 101.5, "BUY");

            assertEquals(100.0, result.submissionMidpoint(), 0.01);
            assertEquals(1.0, result.halfSpread(), 0.01);
            assertEquals(101.5, result.executionPrice(), 0.01);
            assertEquals(150.0, result.impactBps(), 0.01);
        }

        @Test
        void givenSellBelowMidpoint_whenComputeImpact_thenPositiveImpact() {
            /* Given SELL execution below NBBO midpoint */
            /* When computing impact */
            /* Then impact is positive (mid - exec) / mid * 10000 */
            var result = kpi.computeImpact(99.0, 101.0, 98.5, "SELL");

            assertEquals(100.0, result.submissionMidpoint(), 0.01);
            assertEquals(150.0, result.impactBps(), 0.01);
        }

        @Test
        void givenBuyAtMidpoint_whenComputeImpact_thenZeroImpact() {
            /* Given BUY execution exactly at midpoint */
            /* When computing impact */
            /* Then impact is zero */
            var result = kpi.computeImpact(100.0, 102.0, 101.0, "BUY");

            assertEquals(0.0, result.impactBps(), 0.01);
        }

        @Test
        void givenSellAtMidpoint_whenComputeImpact_thenZeroImpact() {
            /* Given SELL execution exactly at midpoint */
            /* When computing impact */
            /* Then impact is zero */
            var result = kpi.computeImpact(100.0, 102.0, 101.0, "SELL");

            assertEquals(0.0, result.impactBps(), 0.01);
        }

        @Test
        void givenZeroMidpoint_whenComputeImpact_thenZeroImpact() {
            /* Given zero bid and ask */
            /* When computing impact */
            /* Then impact is zero */
            var result = kpi.computeImpact(0.0, 0.0, 100.0, "BUY");

            assertEquals(0.0, result.impactBps(), 0.01);
        }

        @Test
        void givenBuyBelowMidpoint_whenComputeImpact_thenNegativeImpact() {
            /* Given BUY execution below midpoint */
            /* When computing impact */
            /* Then impact is negative */
            var result = kpi.computeImpact(99.0, 101.0, 99.5, "BUY");

            assertEquals(-50.0, result.impactBps(), 0.01);
        }

        @Test
        void givenSellAboveMidpoint_whenComputeImpact_thenNegativeImpact() {
            /* Given SELL execution above midpoint */
            /* When computing impact */
            /* Then impact is negative */
            var result = kpi.computeImpact(99.0, 101.0, 101.5, "SELL");

            assertEquals(-150.0, result.impactBps(), 0.01);
        }
    }

    @Nested
    class HalfSpread {
        @Test
        void givenBidAsk_whenComputeImpact_thenHalfSpreadCorrect() {
            /* Given bid=100 ask=102 */
            /* When computing impact */
            /* Then half spread is 1.0 */
            var result = kpi.computeImpact(100.0, 102.0, 101.0, "BUY");

            assertEquals(1.0, result.halfSpread(), 0.01);
        }

        @Test
        void givenWideSpread_whenComputeImpact_thenLargeHalfSpread() {
            /* Given bid=50 ask=60 */
            /* When computing impact */
            /* Then half spread is 5.0 */
            var result = kpi.computeImpact(50.0, 60.0, 55.0, "BUY");

            assertEquals(5.0, result.halfSpread(), 0.01);
        }
    }
}
