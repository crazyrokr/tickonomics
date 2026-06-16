package com.tickonomics.persistence.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VirtualPortfolioPositionTest {

  private static final BigDecimal QTY = new BigDecimal("10.0");
  private static final BigDecimal PRICE = new BigDecimal("520.50");

  @Nested
  class Construction {

    @Test
    void givenAllRequiredFields_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new VirtualPortfolioPosition(
          1L, Instant.now(), "SPY", "BUY", QTY, PRICE, null, null, null, null, null, null));
    }

    @Test
    void givenNullId_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", QTY, PRICE, null, null, null, null, null, null));
    }

    @Test
    void givenAllNullableFieldsNull_whenConstruct_thenSuccess() {
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "SELL", QTY, PRICE, null, null, null, null, null, null);
      assertNotNull(pos);
      assertEquals("SPY", pos.symbol());
      assertEquals("SELL", pos.direction());
    }

    @Test
    void givenAllFieldsPopulated_whenConstruct_thenSuccess() {
      Instant now = Instant.now();
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(
          1L, now, "SPY", "BUY", QTY, PRICE,
          new BigDecimal("525.30"), new BigDecimal("48.0"),
          new BigDecimal("494.48"), new BigDecimal("572.55"), 42L, null);
      assertEquals(1L, pos.id());
      assertEquals(now, pos.openedAt());
      assertEquals("SPY", pos.symbol());
      assertEquals("BUY", pos.direction());
      assertEquals(0, QTY.compareTo(pos.quantity()));
      assertEquals(0, PRICE.compareTo(pos.entryPrice()));
      assertEquals(0, new BigDecimal("525.30").compareTo(pos.currentPrice()));
      assertEquals(0, new BigDecimal("48.0").compareTo(pos.unrealizedPnl()));
      assertEquals(0, new BigDecimal("494.48").compareTo(pos.stopLossPrice()));
      assertEquals(0, new BigDecimal("572.55").compareTo(pos.takeProfitPrice()));
      assertEquals(42L, pos.signalId());
    }
  }

  @Nested
  class Validation {

    @Test
    void givenNullSymbol_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), null, "BUY", QTY, PRICE, null, null, null, null, null, null));
    }

    @Test
    void givenBlankSymbol_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "  ", "BUY", QTY, PRICE, null, null, null, null, null, null));
    }

    @Test
    void givenNullDirection_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", null, QTY, PRICE, null, null, null, null, null, null));
    }

    @Test
    void givenBlankDirection_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "  ", QTY, PRICE, null, null, null, null, null, null));
    }

    @Test
    void givenZeroQuantity_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", BigDecimal.ZERO, PRICE,
          null, null, null, null, null, null));
    }

    @Test
    void givenNegativeQuantity_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", new BigDecimal("-5.0"), PRICE,
          null, null, null, null, null, null));
    }

    @Test
    void givenZeroEntryPrice_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", QTY, BigDecimal.ZERO,
          null, null, null, null, null, null));
    }

    @Test
    void givenNegativeEntryPrice_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", QTY, new BigDecimal("-100.0"),
          null, null, null, null, null, null));
    }

    @Test
    void givenNullQuantity_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", null, PRICE,
          null, null, null, null, null, null));
    }
  }

  @Nested
  class DecimalExactness {

    @Test
    void givenFractionalPrices_whenRoundTrip_thenExactValuesPreserved() {
      /*
       * 0.1 + 0.2 == 0.3 — this fails with double, must pass with BigDecimal.
       */
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(
          null, Instant.now(), "SPY", "BUY", BigDecimal.ONE,
          new BigDecimal("100.10"), new BigDecimal("100.30"), null, null, null, null, null);
      assertEquals(0,
          pos.currentPrice().subtract(pos.entryPrice()).compareTo(new BigDecimal("0.20")),
          "100.30 - 100.10 should equal 0.20 exactly");
    }
  }
}
