package com.tickonomics.computation.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderImpactPredictorTest {

  private final OrderImpactPredictor predictor = new OrderImpactPredictor();

  @Nested
  class IsAcceptable {

    @Test
    void givenZeroOrder_whenIsAcceptable_thenTrue() {
      assertTrue(predictor.isAcceptable(0.0, 1_000_000.0, 10.0));
    }

    @Test
    void givenTinyOrderInDeepLiquidity_whenIsAcceptable_thenTrue() {
      assertTrue(predictor.isAcceptable(5_000.0, 10_000_000.0, 10.0));
    }

    @Test
    void givenLargeParticipationOverThreshold_whenIsAcceptable_thenFalse() {
      assertFalse(predictor.isAcceptable(10_000.0, 100_000.0, 10.0));
    }

    @Test
    void givenImpactExactlyAtThreshold_whenIsAcceptable_thenTrue() {
      assertTrue(predictor.isAcceptable(4_000.0, 100_000.0, 10.0));
    }

    @Test
    void givenZeroOrNegativeLiquidity_whenIsAcceptable_thenFalse() {
      assertFalse(predictor.isAcceptable(5_000.0, 0.0, 10.0));
      assertFalse(predictor.isAcceptable(5_000.0, -1.0, 10.0));
    }
  }

  @Nested
  class EstimatedImpactBps {

    @Test
    void givenSmallParticipation_whenEstimatedImpactBps_thenSmall() {
      assertEquals(0.5, predictor.estimatedImpactBps(10_000.0, 100_000_000.0), 0.0001);
    }

    @Test
    void givenZeroOrder_whenEstimatedImpactBps_thenZero() {
      assertEquals(0.0, predictor.estimatedImpactBps(0.0, 1_000_000.0));
    }

    @Test
    void givenZeroLiquidity_whenEstimatedImpactBps_thenZero() {
      assertEquals(0.0, predictor.estimatedImpactBps(1_000.0, 0.0));
    }
  }
}
