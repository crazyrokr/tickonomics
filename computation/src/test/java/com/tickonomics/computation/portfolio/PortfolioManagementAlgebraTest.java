package com.tickonomics.computation.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioManagementAlgebraTest {

    @InjectMocks
    private PortfolioManagementAlgebra algebra;

    @Nested
    class ComputeCosts {
        @Test
        void givenStandardInputs_whenComputeCosts_thenCorrectModel() {
            var result = algebra.computeCosts(1000, new BigDecimal("50.0"),
                    new BigDecimal("0.001"), new BigDecimal("0.002"), new BigDecimal("5.0"));

            assertEquals(0, new BigDecimal("0.001").compareTo(result.t0Rate()));
            assertEquals(0, new BigDecimal("0.002").compareTo(result.t1Rate()));
            assertEquals(0, new BigDecimal("25.0").compareTo(result.spreadCost()));
            assertEquals(0, new BigDecimal("175.0").compareTo(result.totalCost()));
        }

        @Test
        void givenZeroSize_whenComputeCosts_thenZeroCosts() {
            var result = algebra.computeCosts(0, new BigDecimal("100.0"),
                    new BigDecimal("0.001"), new BigDecimal("0.002"), new BigDecimal("5.0"));

            assertEquals(0, BigDecimal.ZERO.compareTo(result.spreadCost()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.totalCost()));
        }

        @Test
        void givenZeroPrice_whenComputeCosts_thenZeroCosts() {
            var result = algebra.computeCosts(1000, BigDecimal.ZERO,
                    new BigDecimal("0.001"), new BigDecimal("0.002"), new BigDecimal("5.0"));

            assertEquals(0, BigDecimal.ZERO.compareTo(result.totalCost()));
        }

        @Test
        void givenZeroRates_whenComputeCosts_thenOnlySpreadCost() {
            var result = algebra.computeCosts(100, new BigDecimal("100.0"),
                    BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.0"));

            assertEquals(0, BigDecimal.ZERO.compareTo(result.t0Rate()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.t1Rate()));
            assertEquals(0, new BigDecimal("10.0").compareTo(result.spreadCost()));
            assertEquals(0, new BigDecimal("10.0").compareTo(result.totalCost()));
        }

        @Test
        void givenLargePosition_whenComputeCosts_thenCorrectTotal() {
            var result = algebra.computeCosts(10000, new BigDecimal("200.0"),
                    new BigDecimal("0.0005"), new BigDecimal("0.0003"), new BigDecimal("3.0"));

            assertEquals(0, new BigDecimal("2200.0").compareTo(result.totalCost()));
        }
    }

    @Nested
    class ComputeMargin {
        @Test
        void givenStandardInputs_whenComputeMargin_thenCorrectRequirement() {
            var result = algebra.computeMargin(1000, new BigDecimal("50.0"), 0.50, 0.25);

            assertEquals(0, new BigDecimal("25000.0").compareTo(result.grossMargin()));
            assertEquals(0, new BigDecimal("12500.0").compareTo(result.netMargin()));
            assertEquals(0, new BigDecimal("12500.0").compareTo(result.maintenanceMargin()));
            assertEquals(0, new BigDecimal("25000.0").compareTo(result.equityRequired()));
        }

        @Test
        void givenZeroInitialPct_whenComputeMargin_thenZeroMargin() {
            var result = algebra.computeMargin(1000, new BigDecimal("50.0"), 0.0, 0.25);

            assertEquals(0, BigDecimal.ZERO.compareTo(result.grossMargin()));
            assertEquals(0, new BigDecimal("-12500.0").compareTo(result.netMargin()));
        }

        @Test
        void givenZeroSize_whenComputeMargin_thenZeroMargin() {
            var result = algebra.computeMargin(0, new BigDecimal("100.0"), 0.5, 0.25);

            assertEquals(0, BigDecimal.ZERO.compareTo(result.grossMargin()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.maintenanceMargin()));
        }

        @Test
        void givenSameInitialAndMaintenance_whenComputeMargin_thenZeroNetMargin() {
            var result = algebra.computeMargin(100, new BigDecimal("100.0"), 0.30, 0.30);

            assertEquals(0, new BigDecimal("3000.0").compareTo(result.grossMargin()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.netMargin()));
            assertEquals(0, new BigDecimal("3000.0").compareTo(result.maintenanceMargin()));
        }
    }
}
