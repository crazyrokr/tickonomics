package com.tickonomics.computation.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tickonomics.computation.kpi.SignalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExecutionHandlersTest {

  private PassiveExecutionHandler passive;
  private SniperExecutionHandler sniper;

  private final SignalResult buy = new SignalResult(
      "SPY", SignalResult.DIR_BUY, SignalResult.STATUS_ACTIONABLE,
      85.0, 1.5, 0.02, 5.0, 0.7);
  private final SignalResult sell = new SignalResult(
      "SPY", SignalResult.DIR_SELL, SignalResult.STATUS_ACTIONABLE,
      15.0, 1.5, 0.02, 5.0, 0.7);

  @BeforeEach
  void setUp() {
    passive = new PassiveExecutionHandler();
    sniper = new SniperExecutionHandler();
  }

  @Nested
  class PassiveHandler {

    @Test
    void givenSignalAndFairPrice_whenFill_thenFillsAtFairPriceWithNoSlippage() {
      FillEstimate estimate = passive.fill(buy, 500.0);

      assertEquals("PASSIVE", estimate.type());
      assertEquals(500.0, estimate.fillPrice());
      assertEquals(0.0, estimate.slippageBps());
      assertTrue(estimate.filled());
    }
  }

  @Nested
  class SniperHandler {

    @Test
    void givenBuySignal_whenFill_thenFillPriceAboveReference() {
      FillEstimate estimate = sniper.fill(buy, 500.0, 10.0);

      assertEquals("SNIPER", estimate.type());
      assertEquals(500.5, estimate.fillPrice(), 0.0001);
      assertEquals(10.0, estimate.slippageBps());
    }

    @Test
    void givenSellSignal_whenFill_thenFillPriceBelowReference() {
      FillEstimate estimate = sniper.fill(sell, 500.0, 10.0);

      assertEquals(499.5, estimate.fillPrice(), 0.0001);
    }

    @Test
    void givenZeroSlippage_whenFill_thenFillPriceEqualsReference() {
      FillEstimate estimate = sniper.fill(buy, 500.0, 0.0);

      assertEquals(500.0, estimate.fillPrice());
    }
  }
}
