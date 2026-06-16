package com.tickonomics.computation.signal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class LiquidityPremiumFactorTest {

    private LiquidityPremiumFactor factor;

    @BeforeEach
    void setUp() {
        factor = new LiquidityPremiumFactor();
    }

    @Nested
    class Quintile5Premium {

        @Test
        void givenQuintile5_whenCompute_thenFullLiquidityPremium() {
            /*
             * Given: quintile=5 and spreadCompression=2.0
             * When: compute() is called
             * Then: factor = 1.0 + 2.0 * 0.1 = 1.2, rationale = "AT_LIQUIDITY_PREMIUM"
             */
            var result = factor.compute(5, 2.0);

            assertEquals(1.2, result.factor(), 1e-10);
            assertEquals(5, result.quintile());
            assertEquals(LiquidityPremiumFactor.RATIONALE_AT_LIQUIDITY_PREMIUM, result.rationale());
        }

        @Test
        void givenQuintile5ZeroCompression_whenCompute_thenFactorOne() {
            /*
             * Given: quintile=5 and spreadCompression=0.0
             * When: compute() is called
             * Then: factor = 1.0 + 0.0 * 0.1 = 1.0
             */
            var result = factor.compute(5, 0.0);

            assertEquals(1.0, result.factor(), 1e-10);
            assertEquals(LiquidityPremiumFactor.RATIONALE_AT_LIQUIDITY_PREMIUM, result.rationale());
        }

        @Test
        void givenQuintile5HighCompression_whenCompute_thenLargePremium() {
            /*
             * Given: quintile=5 and spreadCompression=5.0
             * When: compute() is called
             * Then: factor = 1.0 + 5.0 * 0.1 = 1.5
             */
            var result = factor.compute(5, 5.0);

            assertEquals(1.5, result.factor(), 1e-10);
        }
    }

    @Nested
    class Quintile4Premium {

        @Test
        void givenQuintile4_whenCompute_thenModeratePremium() {
            /*
             * Given: quintile=4 and spreadCompression=2.0
             * When: compute() is called
             * Then: factor = 1.0 + 2.0 * 0.05 = 1.1, rationale = "MODERATE_PREMIUM"
             */
            var result = factor.compute(4, 2.0);

            assertEquals(1.1, result.factor(), 1e-10);
            assertEquals(4, result.quintile());
            assertEquals(LiquidityPremiumFactor.RATIONALE_MODERATE_PREMIUM, result.rationale());
        }

        @Test
        void givenQuintile4ZeroCompression_whenCompute_thenFactorOne() {
            /*
             * Given: quintile=4 and spreadCompression=0.0
             * When: compute() is called
             * Then: factor = 1.0 + 0.0 * 0.05 = 1.0
             */
            var result = factor.compute(4, 0.0);

            assertEquals(1.0, result.factor(), 1e-10);
            assertEquals(LiquidityPremiumFactor.RATIONALE_MODERATE_PREMIUM, result.rationale());
        }
    }

    @Nested
    class NoPremiumQuintiles {

        @Test
        void givenQuintile3_whenCompute_thenNoPremium() {
            /*
             * Given: quintile=3
             * When: compute() is called
             * Then: factor = 1.0, rationale = "NO_PREMIUM"
             */
            var result = factor.compute(3, 2.0);

            assertEquals(1.0, result.factor(), 1e-10);
            assertEquals(LiquidityPremiumFactor.RATIONALE_NO_PREMIUM, result.rationale());
        }

        @Test
        void givenQuintile2_whenCompute_thenNoPremium() {
            /*
             * Given: quintile=2
             * When: compute() is called
             * Then: factor = 1.0, rationale = "NO_PREMIUM"
             */
            var result = factor.compute(2, 2.0);

            assertEquals(1.0, result.factor(), 1e-10);
            assertEquals(LiquidityPremiumFactor.RATIONALE_NO_PREMIUM, result.rationale());
        }

        @Test
        void givenQuintile1_whenCompute_thenNoPremium() {
            /*
             * Given: quintile=1
             * When: compute() is called
             * Then: factor = 1.0, rationale = "NO_PREMIUM"
             */
            var result = factor.compute(1, 2.0);

            assertEquals(1.0, result.factor(), 1e-10);
            assertEquals(LiquidityPremiumFactor.RATIONALE_NO_PREMIUM, result.rationale());
        }

        @Test
        void givenQuintile0_whenCompute_thenNoPremium() {
            /*
             * Given: quintile=0 (edge case)
             * When: compute() is called
             * Then: factor = 1.0, rationale = "NO_PREMIUM"
             */
            var result = factor.compute(0, 2.0);

            assertEquals(1.0, result.factor(), 1e-10);
            assertEquals(LiquidityPremiumFactor.RATIONALE_NO_PREMIUM, result.rationale());
        }
    }

    @Nested
    class ResultRecord {

        @Test
        void givenAnyQuintile_whenCompute_thenQuintileIsPreservedInResult() {
            /*
             * Given: any quintile value
             * When: compute() is called
             * Then: the quintile in the result matches the input
             */
            for (int q = 0; q <= 6; q++) {
                var result = factor.compute(q, 1.0);
                assertEquals(q, result.quintile());
            }
        }
    }
}
