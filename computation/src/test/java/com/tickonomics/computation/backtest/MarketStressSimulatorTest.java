package com.tickonomics.computation.backtest;

import com.tickonomics.computation.scenario.AumfStatus;
import com.tickonomics.computation.scenario.CrisisProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketStressSimulatorTest {

  private MarketStressSimulator simulator;

  private static final BacktestResult BASELINE = new BacktestResult(
      "RSI_OSCILLATOR", ExecutionDelay.DELAY_0,
      2.0, 0.10, 0.05, 0.60, 0.10, 0.09, 1.0, false, List.of());

  @BeforeEach
  void setUp() {
    simulator = new MarketStressSimulator();
  }

  @Nested
  class ApplyShock {

    @Test
    void givenCovidProfile_whenApplyShock_thenSharpeDegrades() {
      MarketStressSimulator.StressTestResult result =
          simulator.applyShock(BASELINE, CrisisProfile.COVID_2020);

      assertEquals("COVID-2020", result.scenarioName());
      assertTrue(result.stressed().sharpeRatio() < BASELINE.sharpeRatio());
      assertTrue(result.stressed().maxDrawdown() > BASELINE.maxDrawdown());
      assertTrue(result.sharpeDegradation() > 0);
    }

    @Test
    void givenSnbProfile_whenApplyShock_thenSharpeDegrades() {
      MarketStressSimulator.StressTestResult result =
          simulator.applyShock(BASELINE, CrisisProfile.SNB_2015);

      assertEquals("SNB-2015", result.scenarioName());
      assertTrue(result.stressed().sharpeRatio() < BASELINE.sharpeRatio());
    }

    @Test
    void givenBlackMondayProfile_whenApplyShock_thenSharpeDegrades() {
      MarketStressSimulator.StressTestResult result =
          simulator.applyShock(BASELINE, CrisisProfile.BLACK_MONDAY_1987);

      assertEquals("BLACK_MONDAY-1987", result.scenarioName());
      assertTrue(result.stressed().sharpeRatio() < BASELINE.sharpeRatio());
      assertTrue(result.stressed().liquidityFragile());
    }

    @Test
    void givenHighDegradation_whenApplyShock_thenSuspendedUncertainty() {
      MarketStressSimulator.StressTestResult result =
          simulator.applyShock(BASELINE, CrisisProfile.BLACK_MONDAY_1987);

      assertTrue(result.sharpeDegradation() > 0.5);
      assertEquals(AumfStatus.SUSPENDED_UNCERTAINTY, result.worstStatus());
    }
  }

  @Nested
  class RunAllShocks {

    @Test
    void givenBaseline_whenRunAllShocks_thenThreeResults() {
      Map<String, MarketStressSimulator.StressTestResult> results =
          simulator.runAllShocks(BASELINE);

      assertEquals(3, results.size());
      assertTrue(results.containsKey("COVID-2020"));
      assertTrue(results.containsKey("SNB-2015"));
      assertTrue(results.containsKey("BLACK_MONDAY-1987"));
    }
  }
}
