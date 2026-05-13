package com.tickonomics.persistence.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VirtualPortfolioPositionTest {

  @Nested
  class Construction {

    @Test
    void givenAllRequiredFields_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new VirtualPortfolioPosition(
          1L, Instant.now(), "SPY", "BUY", 10.0, 520.50, null, null, null, null, null, null));
    }

    @Test
    void givenNullId_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", 10.0, 520.50, null, null, null, null, null, null));
    }

    @Test
    void givenAllNullableFieldsNull_whenConstruct_thenSuccess() {
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "SELL", 10.0, 520.50, null, null, null, null, null, null);
      assertNotNull(pos);
      assertEquals("SPY", pos.symbol());
      assertEquals("SELL", pos.direction());
    }

    @Test
    void givenAllFieldsPopulated_whenConstruct_thenSuccess() {
      Instant now = Instant.now();
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(
          1L, now, "SPY", "BUY", 10.0, 520.50, 525.30, 48.0, 494.48, 572.55, 42L, null);
      assertEquals(1L, pos.id());
      assertEquals(now, pos.openedAt());
      assertEquals("SPY", pos.symbol());
      assertEquals("BUY", pos.direction());
      assertEquals(10.0, pos.quantity());
      assertEquals(520.50, pos.entryPrice());
      assertEquals(525.30, pos.currentPrice());
      assertEquals(48.0, pos.unrealizedPnl());
      assertEquals(494.48, pos.stopLossPrice());
      assertEquals(572.55, pos.takeProfitPrice());
      assertEquals(42L, pos.signalId());
    }
  }

  @Nested
  class Validation {

    @Test
    void givenNullSymbol_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), null, "BUY", 10.0, 520.50, null, null, null, null, null, null));
    }

    @Test
    void givenBlankSymbol_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "  ", "BUY", 10.0, 520.50, null, null, null, null, null, null));
    }

    @Test
    void givenNullDirection_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", null, 10.0, 520.50, null, null, null, null, null, null));
    }

    @Test
    void givenBlankDirection_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "  ", 10.0, 520.50, null, null, null, null, null, null));
    }

    @Test
    void givenZeroQuantity_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", 0, 520.50, null, null, null, null, null, null));
    }

    @Test
    void givenNegativeQuantity_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", -5.0, 520.50, null, null, null, null, null, null));
    }

    @Test
    void givenZeroEntryPrice_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", 10.0, 0, null, null, null, null, null, null));
    }

    @Test
    void givenNegativeEntryPrice_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", 10.0, -100.0, null, null, null, null, null, null));
    }
  }
}
