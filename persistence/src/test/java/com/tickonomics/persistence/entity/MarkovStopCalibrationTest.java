package com.tickonomics.persistence.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MarkovStopCalibrationTest {

  private MarkovStopCalibration defaultCalibration() {
    return new MarkovStopCalibration(
        1L, "SPY", 5.0, 10.0, 0.02, 0.4, true, 250, Instant.now());
  }

  @Nested
  class Construction {

    @Test
    void givenAllRequiredFields_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> defaultCalibration());
    }

    @Test
    void givenNullOptionalFields_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new MarkovStopCalibration(
          null, "SPY", 5.0, 10.0, null, null, false, null, null));
    }
  }

  @Nested
  class Validation {

    @Test
    void givenBlankSymbol_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new MarkovStopCalibration(1L, "  ", 5.0, 10.0, null, null, false, null, null));
    }

    @Test
    void givenZeroStopLoss_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new MarkovStopCalibration(1L, "SPY", 0.0, 10.0, null, null, false, null, null));
    }

    @Test
    void givenStopLossAtHundredPercent_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new MarkovStopCalibration(1L, "SPY", 100.0, 10.0, null, null, false, null, null));
    }

    @Test
    void givenZeroTakeProfit_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new MarkovStopCalibration(1L, "SPY", 5.0, 0.0, null, null, false, null, null));
    }
  }

  @Nested
  class Accessors {

    @Test
    void givenPopulatedCalibration_whenAccessors_thenValuesMatch() {
      MarkovStopCalibration calibration = defaultCalibration();

      assertEquals("SPY", calibration.symbol());
      assertEquals(5.0, calibration.optimalStopLoss());
      assertEquals(10.0, calibration.optimalTakeProfit());
      assertTrue(calibration.converged());
      assertEquals(250, calibration.iterations());
    }

    @Test
    void givenUnconvergedCalibration_whenAccessors_thenFalse() {
      MarkovStopCalibration calibration = new MarkovStopCalibration(
          2L, "QQQ", 4.0, 8.0, null, null, false, 1000, Instant.now());

      assertFalse(calibration.converged());
    }
  }
}
