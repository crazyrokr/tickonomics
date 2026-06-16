package com.tickonomics.computation.demo;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DemoConfigTest {

  @Nested
  class CoreFactory {

    @Test
    void givenCoreArgs_whenCore_thenNestedBlocksUseDefaults() {
      DemoConfig config = DemoConfig.core(
          true, new BigDecimal("100000.00"), 5.0, 5.0, 10.0, true, true, true, 10_000_000.0);

      assertEquals(DemoConfig.AdvancedCostModel.defaults(), config.advancedCostModel());
      assertEquals(DemoConfig.RandomizedExecution.defaults(), config.randomizedExecution());
      assertEquals(DemoConfig.MarketStabilityGuard.defaults(), config.marketStabilityGuard());
      assertEquals(DemoConfig.OrderImpactPredictor.defaults(), config.orderImpactPredictor());
      assertEquals(DemoConfig.DynamicStops.defaults(), config.dynamicStops());
      assertEquals(DemoConfig.PortfolioAlgebra.defaults(), config.portfolioAlgebra());
      assertEquals(DemoConfig.LeverageRotation.defaults(), config.leverageRotation());
      assertEquals(DemoConfig.KillSwitchConfig.defaults(), config.killSwitch());
      assertEquals(DemoConfig.GlobalSafeMode.defaults(), config.globalSafeMode());
    }

    @Test
    void givenDefaultsFactory_whenInvoke_thenDisabledWithConservativeCore() {
      DemoConfig config = DemoConfig.defaults();

      assertFalse(config.enabled());
      assertFalse(config.autoExecuteSignals());
      assertTrue(config.leverageRotation().enabled());
      assertEquals(0.84, config.advancedCostModel().passiveBps());
      assertEquals(2.03, config.advancedCostModel().aggressiveBps());
      assertEquals(9.04, config.advancedCostModel().largeOrderBps());
    }
  }

  @Nested
  class CanonicalConstructor {

    @Test
    void givenCustomNestedBlocks_whenConstruct_thenUsedVerbatim() {
      DemoConfig.AdvancedCostModel cost = new DemoConfig.AdvancedCostModel(false, 1.0, 3.0, 10.0, false);
      DemoConfig.RandomizedExecution exec = new DemoConfig.RandomizedExecution(false, 30, false);

      DemoConfig config = new DemoConfig(
          true, 100_000.0, 5.0, 5.0, 10.0, true, true, true, 10_000_000.0,
          cost, exec, DemoConfig.MarketStabilityGuard.defaults(),
          DemoConfig.OrderImpactPredictor.defaults(), DemoConfig.MarketMakerMode.defaults(),
          DemoConfig.DynamicStops.defaults(), DemoConfig.PortfolioAlgebra.defaults(),
          DemoConfig.LeverageRotation.defaults(), DemoConfig.KillSwitchConfig.defaults(),
          DemoConfig.GlobalSafeMode.defaults());

      assertEquals(cost, config.advancedCostModel());
      assertEquals(30, config.randomizedExecution().windowSeconds());
      assertFalse(config.randomizedExecution().avoidRoundMarks());
    }
  }

  @Nested
  class NestedValidation {

    @Test
    void givenNegativeSubmissionBps_whenConstructAdvancedCostModel_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new DemoConfig.AdvancedCostModel(true, -1.0, 2.0, 9.0, true));
    }

    @Test
    void givenZeroWindow_whenConstructRandomizedExecution_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new DemoConfig.RandomizedExecution(true, 0, true));
    }

    @Test
    void givenBlankBenchmark_whenConstructLeverageRotation_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new DemoConfig.LeverageRotation(true, 200, "  "));
    }

    @Test
    void givenOutOfRangeSpreadTarget_whenConstructMarketMakerMode_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new DemoConfig.MarketMakerMode(false, true, 150.0));
    }

    @Test
    void givenValidNestedBlocks_whenConstruct_thenNoThrow() {
      assertDoesNotThrow(() -> new DemoConfig.RandomizedExecution(true, 120, true));
      assertDoesNotThrow(() -> new DemoConfig.MarketStabilityGuard(true, 7.5, 50));
      assertDoesNotThrow(() -> new DemoConfig.DynamicStops(true, "1d"));
      assertDoesNotThrow(() -> new DemoConfig.GlobalSafeMode(true, 80.0, 5000L, 2, 5000L));
    }

    @Test
    void givenNegativeOverflowThreshold_whenConstructGlobalSafeMode_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new DemoConfig.GlobalSafeMode(true, -1.0, 5000L, 2, 5000L));
    }

    @Test
    void givenOverflowThresholdOverHundred_whenConstructGlobalSafeMode_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new DemoConfig.GlobalSafeMode(true, 150.0, 5000L, 2, 5000L));
    }

    @Test
    void givenMinDegradedIndicatorsOutOfRange_whenConstructGlobalSafeMode_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new DemoConfig.GlobalSafeMode(true, 80.0, 5000L, 0, 5000L));
      assertThrows(IllegalArgumentException.class,
          () -> new DemoConfig.GlobalSafeMode(true, 80.0, 5000L, 4, 5000L));
    }

    @Test
    void givenNegativeWorkerLatencyThreshold_whenConstructGlobalSafeMode_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new DemoConfig.GlobalSafeMode(true, 80.0, -1L, 2, 5000L));
    }

    @Test
    void givenNonPositiveEvaluationInterval_whenConstructGlobalSafeMode_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new DemoConfig.GlobalSafeMode(true, 80.0, 5000L, 2, 0L));
    }
  }
}
