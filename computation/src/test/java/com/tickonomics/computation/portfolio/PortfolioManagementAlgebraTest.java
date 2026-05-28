package com.tickonomics.computation.portfolio;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PortfolioManagementAlgebraTest {

    @InjectMocks
    private PortfolioManagementAlgebra algebra;

    @Nested
    class ComputeCosts {
        @Test
        void givenStandardInputs_whenComputeCosts_thenCorrectModel() {
            /* Given 1000 shares at $50, t0=0.001, t1=0.002, spread=5bps */
            /* When computing costs */
            /* Then total = (0.001+0.002)*50000 + 50000*5/10000 */
            var result = algebra.computeCosts(1000, 50.0, 0.001, 0.002, 5.0);

            assertEquals(0.001, result.t0Rate(), 0.0001);
            assertEquals(0.002, result.t1Rate(), 0.0001);
            assertEquals(25.0, result.spreadCost(), 0.01);
            assertEquals(175.0, result.totalCost(), 0.01);
        }

        @Test
        void givenZeroSize_whenComputeCosts_thenZeroCosts() {
            /* Given zero size */
            /* When computing costs */
            /* Then all costs are zero */
            var result = algebra.computeCosts(0, 100.0, 0.001, 0.002, 5.0);

            assertEquals(0.0, result.spreadCost(), 0.01);
            assertEquals(0.0, result.totalCost(), 0.01);
        }

        @Test
        void givenZeroPrice_whenComputeCosts_thenZeroCosts() {
            /* Given zero price */
            /* When computing costs */
            /* Then all costs are zero */
            var result = algebra.computeCosts(1000, 0.0, 0.001, 0.002, 5.0);

            assertEquals(0.0, result.totalCost(), 0.01);
        }

        @Test
        void givenZeroRates_whenComputeCosts_thenOnlySpreadCost() {
            /* Given zero commission rates but positive spread */
            /* When computing costs */
            /* Then only spread cost remains */
            var result = algebra.computeCosts(100, 100.0, 0.0, 0.0, 10.0);

            assertEquals(0.0, result.t0Rate());
            assertEquals(0.0, result.t1Rate());
            assertEquals(10.0, result.spreadCost(), 0.01);
            assertEquals(10.0, result.totalCost(), 0.01);
        }

        @Test
        void givenLargePosition_whenComputeCosts_thenCorrectTotal() {
            /* Given large position 10000 shares at $200 */
            /* When computing costs with 0.05% + 0.03% + 3bps spread */
            /* Then total is correctly computed */
            var result = algebra.computeCosts(10000, 200.0, 0.0005, 0.0003, 3.0);

            double expectedTotal = (0.0005 + 0.0003) * 2000000 + 2000000 * 3.0 / 10000.0;
            assertEquals(expectedTotal, result.totalCost(), 0.01);
        }
    }

    @Nested
    class ComputeMargin {
        @Test
        void givenStandardInputs_whenComputeMargin_thenCorrectRequirement() {
            /* Given 1000 shares at $50, 50% initial, 25% maintenance */
            /* When computing margin */
            /* Then gross=25000, maintenance=12500, net=12500 */
            var result = algebra.computeMargin(1000, 50.0, 0.50, 0.25);

            assertEquals(25000.0, result.grossMargin(), 0.01);
            assertEquals(12500.0, result.netMargin(), 0.01);
            assertEquals(12500.0, result.maintenanceMargin(), 0.01);
            assertEquals(25000.0, result.equityRequired(), 0.01);
        }

        @Test
        void givenZeroInitialPct_whenComputeMargin_thenZeroMargin() {
            /* Given zero initial percentage */
            /* When computing margin */
            /* Then all margin values are zero */
            var result = algebra.computeMargin(1000, 50.0, 0.0, 0.25);

            assertEquals(0.0, result.grossMargin(), 0.01);
            assertEquals(-12500.0, result.netMargin(), 0.01);
        }

        @Test
        void givenZeroSize_whenComputeMargin_thenZeroMargin() {
            /* Given zero size */
            /* When computing margin */
            /* Then all margin values are zero */
            var result = algebra.computeMargin(0, 100.0, 0.5, 0.25);

            assertEquals(0.0, result.grossMargin(), 0.01);
            assertEquals(0.0, result.maintenanceMargin(), 0.01);
        }

        @Test
        void givenSameInitialAndMaintenance_whenComputeMargin_thenZeroNetMargin() {
            /* Given equal initial and maintenance percentages */
            /* When computing margin */
            /* Then net margin is zero */
            var result = algebra.computeMargin(100, 100.0, 0.30, 0.30);

            assertEquals(3000.0, result.grossMargin(), 0.01);
            assertEquals(0.0, result.netMargin(), 0.01);
            assertEquals(3000.0, result.maintenanceMargin(), 0.01);
        }
    }
}
