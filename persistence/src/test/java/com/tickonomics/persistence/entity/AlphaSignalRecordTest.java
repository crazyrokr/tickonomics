package com.tickonomics.persistence.entity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlphaSignalRecordTest {

  private static final UUID STRATEGY_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.now();

  @Nested
  class ValidConstruction {

    @Test
    void givenValidFields_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new AlphaSignalRecord(
          NOW, STRATEGY_ID, "SPY", "LONG", 0.8, 0.9, 1.5, "{}"));
    }

    @Test
    void givenNullOptionalFields_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new AlphaSignalRecord(
          NOW, STRATEGY_ID, "SPY", "LONG", 0.5, 0.5, null, null));
    }
  }

  @Nested
  class ValidationFailures {

    @Test
    void givenNullTime_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new AlphaSignalRecord(null, STRATEGY_ID, "SPY", "LONG", 0.5, 0.5, null, null));
    }

    @Test
    void givenNullStrategyId_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new AlphaSignalRecord(NOW, null, "SPY", "LONG", 0.5, 0.5, null, null));
    }

    @Test
    void givenBlankSymbol_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new AlphaSignalRecord(NOW, STRATEGY_ID, "", "LONG", 0.5, 0.5, null, null));
    }

    @Test
    void givenStrengthAboveOne_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new AlphaSignalRecord(NOW, STRATEGY_ID, "SPY", "LONG", 1.5, 0.5, null, null));
    }

    @Test
    void givenNegativeConfidence_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class,
          () -> new AlphaSignalRecord(NOW, STRATEGY_ID, "SPY", "LONG", 0.5, -0.1, null, null));
    }
  }

  @Nested
  class FieldAccess {

    @Test
    void givenRecord_whenAccessFields_thenCorrectValues() {
      AlphaSignalRecord record = new AlphaSignalRecord(
          NOW, STRATEGY_ID, "SPY", "LONG", 0.8, 0.9, 2.5, "{\"key\":\"val\"}");

      assertEquals(NOW, record.time());
      assertEquals(STRATEGY_ID, record.strategyId());
      assertEquals("SPY", record.symbol());
      assertEquals("LONG", record.direction());
      assertEquals(0.8, record.strength());
      assertEquals(0.9, record.confidence());
      assertEquals(2.5, record.expectedMove());
      assertEquals("{\"key\":\"val\"}", record.metadata());
    }
  }
}
