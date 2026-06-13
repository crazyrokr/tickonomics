package com.tickonomics.computation.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FillProbabilityEngineTest {

  private final FillProbabilityEngine engine = new FillProbabilityEngine();

  @Nested
  class FillProbability {

    @Test
    void givenZeroPli_whenFillProbability_thenOne() {
      assertEquals(1.0, engine.fillProbability(0.0));
    }

    @Test
    void givenOnePli_whenFillProbability_thenZero() {
      assertEquals(0.0, engine.fillProbability(1.0));
    }

    @Test
    void givenMidPli_whenFillProbability_thenOneMinusPli() {
      assertEquals(0.7, engine.fillProbability(0.3));
    }

    @Test
    void givenPliOutOfRange_whenFillProbability_thenClamped() {
      assertEquals(1.0, engine.fillProbability(-0.5));
      assertEquals(0.0, engine.fillProbability(1.5));
    }
  }

  @Nested
  class SlippageMultiplier {

    @Test
    void givenZeroPli_whenSlippageMultiplier_thenOne() {
      assertEquals(1.0, engine.slippageMultiplier(0.0));
    }

    @Test
    void givenOnePli_whenSlippageMultiplier_thenTwo() {
      assertEquals(2.0, engine.slippageMultiplier(1.0));
    }

    @Test
    void givenPliOutOfRange_whenSlippageMultiplier_thenClampedToOneToTwo() {
      assertEquals(1.0, engine.slippageMultiplier(-0.5));
      assertEquals(2.0, engine.slippageMultiplier(2.0));
    }

    @Test
    void givenAnyPli_whenSlippageMultiplier_thenAtLeastOne() {
      assertTrue(engine.slippageMultiplier(0.25) >= 1.0);
    }
  }
}
