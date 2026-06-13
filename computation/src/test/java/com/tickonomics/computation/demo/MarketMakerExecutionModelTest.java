package com.tickonomics.computation.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MarketMakerExecutionModelTest {

  private final MarketMakerExecutionModel model = new MarketMakerExecutionModel();

  @Nested
  class Decide {

    @Test
    void givenPreferLimitAndValidQuote_whenDecide_thenLimitAtFairPriceWithSpreadCapture() {
      MarketMakerExecutionModel.ExecutionDecision decision = model.decide(99.0, 101.0, 100.0, true);

      assertEquals(MarketMakerExecutionModel.Strategy.LIMIT, decision.strategy());
      assertEquals(100.0, decision.limitPrice());
      assertEquals(100.0, decision.expectedFillPrice());
      assertEquals(100.0, decision.spreadCaptureBps(), 0.0001);
    }

    @Test
    void givenPreferLimitFalse_whenDecide_thenMarketAtMidNoCapture() {
      MarketMakerExecutionModel.ExecutionDecision decision = model.decide(99.0, 101.0, 100.0, false);

      assertEquals(MarketMakerExecutionModel.Strategy.MARKET, decision.strategy());
      assertEquals(100.0, decision.expectedFillPrice());
      assertEquals(0.0, decision.spreadCaptureBps());
    }

    @Test
    void givenCrossedQuote_whenDecide_thenMarketWithoutSpreadCapture() {
      MarketMakerExecutionModel.ExecutionDecision decision = model.decide(101.0, 99.0, 100.0, true);

      assertEquals(MarketMakerExecutionModel.Strategy.MARKET, decision.strategy());
      assertEquals(0.0, decision.spreadCaptureBps());
    }

    @Test
    void givenValidQuote_whenDecideLimit_thenSpreadCaptureProportionalToHalfSpread() {
      MarketMakerExecutionModel.ExecutionDecision decision = model.decide(100.0, 104.0, 102.0, true);

      assertTrue(decision.spreadCaptureBps() > 0.0);
    }
  }
}
