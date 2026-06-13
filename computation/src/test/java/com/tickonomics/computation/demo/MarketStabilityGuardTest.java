package com.tickonomics.computation.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MarketStabilityGuardTest {

  private final MarketStabilityGuard guard = new MarketStabilityGuard();

  @Nested
  class IsStable {

    @Test
    void givenEmptyReturns_whenIsStable_thenTrue() {
      assertTrue(guard.isStable(List.of(), 5.0));
    }

    @Test
    void givenSingleReturn_whenIsStable_thenTrue() {
      assertTrue(guard.isStable(List.of(0.10), 5.0));
    }

    @Test
    void givenLowVarianceReturnsUnderThreshold_whenIsStable_thenTrue() {
      assertTrue(guard.isStable(List.of(0.001, -0.001, 0.002, -0.002), 5.0));
    }

    @Test
    void givenHighVarianceReturnsOverThreshold_whenIsStable_thenFalse() {
      assertFalse(guard.isStable(List.of(0.10, -0.10, 0.12, -0.08), 5.0));
    }

    @Test
    void givenConstantReturns_whenIsStable_thenTrueEvenWithTinyThreshold() {
      assertTrue(guard.isStable(List.of(0.05, 0.05, 0.05), 0.001));
    }
  }

  @Nested
  class VolatilityPct {

    @Test
    void givenNullReturns_whenVolatilityPct_thenZero() {
      assertEquals(0.0, guard.volatilityPct(null));
    }

    @Test
    void givenSingleReturn_whenVolatilityPct_thenZero() {
      assertEquals(0.0, guard.volatilityPct(List.of(0.03)));
    }

    @Test
    void givenKnownReturns_whenVolatilityPct_thenMatchesManualStdDev() {
      double vol = guard.volatilityPct(List.of(0.01, -0.01, 0.01, -0.01));

      assertEquals(1.0, vol, 0.0001);
    }
  }
}
