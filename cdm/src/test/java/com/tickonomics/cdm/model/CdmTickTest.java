package com.tickonomics.cdm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

class CdmTickTest {

  private static CdmTick tick(int... conditions) {
    return new CdmTick(Instant.parse("2026-06-14T10:00:00Z"), "AAPL", BigDecimal.valueOf(150.0), 100L, conditions);
  }

  @Nested
  class EqualsAndHashCode {

    @Test
    void givenIdenticalConditionContents_whenEquals_thenEqualAndSameHash() {
      // Given - two separately constructed ticks with equivalent condition arrays
      var a = tick(1, 2, 3);
      var b = tick(1, 2, 3);

      // When / Then
      assertTrue(a.equals(b));
      assertTrue(b.equals(a));
      assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void givenDifferentConditions_whenEquals_thenNotEqual() {
      var a = tick(1, 2, 3);
      var b = tick(1, 2, 4);

      assertFalse(a.equals(b));
    }

    @Test
    void givenNullConditionsOnBoth_whenEquals_thenEqual() {
      var a = new CdmTick(Instant.parse("2026-06-14T10:00:00Z"), "AAPL", BigDecimal.valueOf(150.0), 100L, null);
      var b = new CdmTick(Instant.parse("2026-06-14T10:00:00Z"), "AAPL", BigDecimal.valueOf(150.0), 100L, null);

      assertTrue(a.equals(b));
      assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void givenSameReference_whenEquals_thenTrue() {
      var a = tick(1, 2);

      assertTrue(a.equals(a));
    }

    @Test
    void givenOtherTypeOrNull_whenEquals_thenFalse() {
      var a = tick(1);

      assertFalse(a.equals(null));
      assertFalse(a.equals("not a tick"));
    }
  }

  @Nested
  class DefensiveCopy {

    @Test
    void givenCallerMutatesSourceArray_whenConditionsAccessed_thenUnchanged() {
      var source = new int[] {1, 2};
      var tick = new CdmTick(Instant.parse("2026-06-14T10:00:00Z"), "AAPL", BigDecimal.valueOf(150.0), 100L, source);

      source[0] = 99;

      var before = tick(1, 2);
      assertEquals(before, tick);
    }

    @Test
    void givenMutatedAccessorResult_whenConditionsReaccessed_thenUnchanged() {
      var tick = tick(1, 2, 3);
      var leaked = tick.conditions();
      leaked[0] = 99;

      assertEquals(tick(1, 2, 3), tick);
    }
  }
}
