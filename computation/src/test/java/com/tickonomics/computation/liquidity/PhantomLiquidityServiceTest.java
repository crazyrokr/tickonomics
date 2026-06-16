package com.tickonomics.computation.liquidity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PhantomLiquidityServiceTest {

    private PhantomLiquidityService service;

    @BeforeEach
    void setUp() {
        service = new PhantomLiquidityService();
    }

    @Nested
    class ComputePli {

        @Test
        void givenCanceledAndTotalVolume_whenComputePli_thenCorrectRatio() {
            /*
             * Given: canceledVolume=500 and totalVolumeAtBest=1000
             * When: computePli() is called
             * Then: pli = 500/1000 = 0.5
             */
            double pli = service.computePli(500.0, 1000.0);

            assertEquals(0.5, pli, 1e-10);
        }

        @Test
        void givenZeroTotalVolume_whenComputePli_thenDividesByMaxOne() {
            /*
             * Given: totalVolumeAtBest=0
             * When: computePli() is called
             * Then: pli = canceledVolume / max(1, 0) = canceledVolume
             */
            double pli = service.computePli(500.0, 0.0);

            assertEquals(500.0, pli, 1e-10);
        }

        @Test
        void givenNoCanceledVolume_whenComputePli_thenZeroPli() {
            /*
             * Given: canceledVolume=0
             * When: computePli() is called
             * Then: pli = 0
             */
            double pli = service.computePli(0.0, 1000.0);

            assertEquals(0.0, pli, 1e-10);
        }
    }

    @Nested
    class ComputeReliabilityScore {

        @Test
        void givenZeroPli_whenComputeReliabilityScore_thenOne() {
            /*
             * Given: pli=0.0 (no phantom liquidity)
             * When: computeReliabilityScore() is called
             * Then: reliability = 1.0 - 0.0 = 1.0
             */
            double reliability = service.computeReliabilityScore(0.0);

            assertEquals(1.0, reliability, 1e-10);
        }

        @Test
        void givenFullPli_whenComputeReliabilityScore_thenZero() {
            /*
             * Given: pli=1.0 (all volume is phantom)
             * When: computeReliabilityScore() is called
             * Then: reliability = 1.0 - 1.0 = 0.0
             */
            double reliability = service.computeReliabilityScore(1.0);

            assertEquals(0.0, reliability, 1e-10);
        }
    }

    @Nested
    class DiscountIli {

        @Test
        void givenNoPhantomLiquidity_whenDiscountIli_thenNoDiscount() {
            /*
             * Given: ili=1.0 and pli=0.0
             * When: discountIli() is called
             * Then: discounted = 1.0 * (1.0 - 0.0 * 0.5) = 1.0
             */
            double discounted = service.discountIli(1.0, 0.0);

            assertEquals(1.0, discounted, 1e-10);
        }

        @Test
        void givenFullPhantomLiquidity_whenDiscountIli_thenFiftyPercentDiscount() {
            /*
             * Given: ili=1.0 and pli=1.0
             * When: discountIli() is called
             * Then: discounted = 1.0 * (1.0 - 1.0 * 0.5) = 0.5
             */
            double discounted = service.discountIli(1.0, 1.0);

            assertEquals(0.5, discounted, 1e-10);
        }

        @Test
        void givenModeratePhantomLiquidity_whenDiscountIli_thenProportionalDiscount() {
            /*
             * Given: ili=2.0 and pli=0.4
             * When: discountIli() is called
             * Then: discounted = 2.0 * (1.0 - 0.4 * 0.5) = 2.0 * 0.8 = 1.6
             */
            double discounted = service.discountIli(2.0, 0.4);

            assertEquals(1.6, discounted, 1e-10);
        }
    }

    @Nested
    class FullComputation {

        @Test
        void givenAllInputs_whenCompute_thenReturnsFullResult() {
            /*
             * Given: canceledVolume=300, totalVolumeAtBest=1000, ili=1.5
             * When: compute() is called
             * Then: pli=0.3, reliability=0.7, discountedIli=1.5*(1-0.15)=1.275
             */
            var result = service.compute(300.0, 1000.0, 1.5);

            assertEquals(0.3, result.pli(), 1e-10);
            assertEquals(0.7, result.reliabilityScore(), 1e-10);
            assertEquals(1.275, result.discountedIli(), 1e-10);
        }
    }
}
