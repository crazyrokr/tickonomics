package com.tickonomics.computation.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RandomizedExecutionWindowTest {

  private final RandomizedExecutionWindow window = new RandomizedExecutionWindow();

  @Nested
  class DelaySeconds {

    @Test
    void givenZeroWindow_whenDelaySeconds_thenZero() {
      assertEquals(0L, window.delaySeconds(new Random(42), 0, true));
    }

    @Test
    void givenNegativeWindow_whenDelaySeconds_thenZero() {
      assertEquals(0L, window.delaySeconds(new Random(42), -5, true));
    }

    @Test
    void givenAvoidFalse_whenDelaySeconds_thenRawRandomValue() {
      long delay = window.delaySeconds(new Random(7), 60, false);

      assertEquals(53L, delay);
      assertTrue(delay >= 0 && delay <= 60);
    }

    @Test
    void givenAvoidTrueAndResultOutsideZone_whenDelaySeconds_thenUnchanged() {
      long delay = window.delaySeconds(new Random(123), 60, true);

      if (!isInDangerZone(48, 10, 1)) {
        assertEquals(48L, delay);
      }
    }
  }

  @Nested
  class ShiftOutOfDangerZone {

    @Test
    void givenDelayAtRoundMark_whenShift_thenMovedRight() {
      assertEquals(2L, window.shiftOutOfDangerZone(0, 60, 10, 1));
      assertEquals(12L, window.shiftOutOfDangerZone(10, 60, 10, 1));
    }

    @Test
    void givenDelayWithinMargin_whenShift_thenMovedOut() {
      assertEquals(12L, window.shiftOutOfDangerZone(9, 60, 10, 1));
      assertEquals(12L, window.shiftOutOfDangerZone(11, 60, 10, 1));
    }

    @Test
    void givenDelayOutsideDangerZone_whenShift_thenUnchanged() {
      assertEquals(5L, window.shiftOutOfDangerZone(5, 60, 10, 1));
      assertEquals(23L, window.shiftOutOfDangerZone(23, 60, 10, 1));
    }

    @Test
    void givenRoundMarkAtWindowEdge_whenShift_thenFallsBackLeft() {
      assertEquals(58L, window.shiftOutOfDangerZone(60, 60, 10, 1));
    }
  }

  private static boolean isInDangerZone(long delay, int interval, int margin) {
    long nearest = Math.round((double) delay / interval) * interval;
    return Math.abs(delay - nearest) <= margin;
  }
}
