package com.tickonomics.persistence.entity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BacktestResultRecordTest {

  private static final Instant NOW = Instant.now();

  @Nested
  class ValidConstruction {

    @Test
    void givenAllFields_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new BacktestResultRecord(
          1L, NOW, "{\"strategy\":\"RSI\"}", "[2025-01-01,2025-06-01]",
          1.5, 0.12, 0.55, 2.1, "[100,102,98]",
          "abc123", "hash456", "{\"lr\":0.01}", 2,
          "{\"slice\":\"default\"}", "{\"p\":[0.01]}"));
    }

    @Test
    void givenNullOptionalFields_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new BacktestResultRecord(
          null, NOW, "{\"strategy\":\"RSI\"}", "[2025-01-01,2025-06-01]",
          null, null, null, null, null,
          null, null, null, null, null, null));
    }
  }

  @Nested
  class ValidationFailures {

    @Test
    void givenNullRunAt_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new BacktestResultRecord(
              null, null, "{}", "[]", null, null, null, null, null,
              null, null, null, null, null, null));
    }

    @Test
    void givenBlankStrategyConfig_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new BacktestResultRecord(
              null, NOW, "", "[]", null, null, null, null, null,
              null, null, null, null, null, null));
    }

    @Test
    void givenBlankDateRange_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new BacktestResultRecord(
              null, NOW, "{}", "", null, null, null, null, null,
              null, null, null, null, null, null));
    }
  }

  @Nested
  class FieldAccess {

    @Test
    void givenRecord_whenAccessFields_thenCorrectValues() {
      BacktestResultRecord record = new BacktestResultRecord(
          42L, NOW, "{\"strategy\":\"MACD\"}", "[2025-01-01,2025-12-31]",
          2.0, 0.15, 0.6, 1.8, "[100,110,105]",
          "deadbeef", "sha256hash", "{\"lr\":0.001}", 1,
          null, null);

      assertEquals(42L, record.id());
      assertEquals(NOW, record.runAt());
      assertEquals(2.0, record.sharpeRatio());
      assertEquals(0.15, record.maxDrawdown());
      assertEquals("deadbeef", record.gitSha());
      assertEquals(1, record.rdsScore());
    }
  }
}
