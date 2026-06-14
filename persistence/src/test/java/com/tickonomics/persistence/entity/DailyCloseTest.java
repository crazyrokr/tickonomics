package com.tickonomics.persistence.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DailyCloseTest {

  @Nested
  class Construction {

    @Test
    void givenValidDayAndClose_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new DailyClose(LocalDate.of(2026, 6, 13), 500.25));
    }

    @Test
    void givenPopulatedClose_whenAccessors_thenValuesMatch() {
      DailyClose close = new DailyClose(LocalDate.of(2026, 6, 13), 500.25);

      assertEquals(LocalDate.of(2026, 6, 13), close.day());
      assertEquals(500.25, close.close());
    }
  }

  @Nested
  class Validation {

    @Test
    void givenNullDay_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new DailyClose(null, 500.0));
    }

    @Test
    void givenZeroClose_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new DailyClose(LocalDate.of(2026, 6, 13), 0.0));
    }

    @Test
    void givenNegativeClose_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new DailyClose(LocalDate.of(2026, 6, 13), -1.0));
    }
  }
}
