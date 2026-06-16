package com.tickonomics.persistence.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VirtualPortfolioTradeTest {

  private VirtualPortfolioTrade defaultTrade() {
    return new VirtualPortfolioTrade(
        null, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("520.50"),
        new BigDecimal("1.0"), new BigDecimal("0.5"), null, null, null, "PAPER");
  }

  @Nested
  class Construction {

    @Test
    void givenAllRequiredFields_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> defaultTrade());
    }

    @Test
    void givenAllFieldsPopulated_whenConstruct_thenSuccess() {
      Instant now = Instant.now();
      VirtualPortfolioTrade trade = new VirtualPortfolioTrade(
          1L, now, "SPY", "SELL", new BigDecimal("10.0"), new BigDecimal("530.25"),
          new BigDecimal("1.0"), new BigDecimal("0.3"), new BigDecimal("97.50"), 42L, 7L, "PAPER");
      assertEquals(1L, trade.id());
      assertEquals(now, trade.executedAt());
      assertEquals("SPY", trade.symbol());
      assertEquals("SELL", trade.direction());
      assertEquals(0, new BigDecimal("10.0").compareTo(trade.quantity()));
      assertEquals(0, new BigDecimal("530.25").compareTo(trade.fillPrice()));
      assertEquals(0, new BigDecimal("1.0").compareTo(trade.commission()));
      assertEquals(0, new BigDecimal("0.3").compareTo(trade.slippage()));
      assertEquals(0, new BigDecimal("97.50").compareTo(trade.realizedPnl()));
      assertEquals(42L, trade.positionId());
      assertEquals(7L, trade.signalId());
      assertEquals("PAPER", trade.tradeType());
    }

    @Test
    void givenNullId_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> defaultTrade());
    }

    @Test
    void givenNullRealizedPnl_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("520.50"),
          new BigDecimal("1.0"), new BigDecimal("0.5"), null, null, null, "PAPER"));
    }
  }

  @Nested
  class Validation {

    @Test
    void givenNullSymbol_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), null, "BUY", new BigDecimal("10.0"), new BigDecimal("520.50"),
          new BigDecimal("1.0"), new BigDecimal("0.5"), null, null, null, "PAPER"));
    }

    @Test
    void givenBlankSymbol_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "  ", "BUY", new BigDecimal("10.0"), new BigDecimal("520.50"),
          new BigDecimal("1.0"), new BigDecimal("0.5"), null, null, null, "PAPER"));
    }

    @Test
    void givenNullDirection_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", null, new BigDecimal("10.0"), new BigDecimal("520.50"),
          new BigDecimal("1.0"), new BigDecimal("0.5"), null, null, null, "PAPER"));
    }

    @Test
    void givenInvalidDirection_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "HOLD", new BigDecimal("10.0"), new BigDecimal("520.50"),
          new BigDecimal("1.0"), new BigDecimal("0.5"), null, null, null, "PAPER"));
    }

    @Test
    void givenZeroQuantity_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", BigDecimal.ZERO, new BigDecimal("520.50"),
          new BigDecimal("1.0"), new BigDecimal("0.5"), null, null, null, "PAPER"));
    }

    @Test
    void givenNegativeQuantity_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", new BigDecimal("-5.0"), new BigDecimal("520.50"),
          new BigDecimal("1.0"), new BigDecimal("0.5"), null, null, null, "PAPER"));
    }

    @Test
    void givenZeroFillPrice_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), BigDecimal.ZERO,
          new BigDecimal("1.0"), new BigDecimal("0.5"), null, null, null, "PAPER"));
    }

    @Test
    void givenNegativeFillPrice_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("-100.0"),
          new BigDecimal("1.0"), new BigDecimal("0.5"), null, null, null, "PAPER"));
    }

    @Test
    void givenNegativeCommission_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("520.50"),
          new BigDecimal("-1.0"), new BigDecimal("0.5"), null, null, null, "PAPER"));
    }

    @Test
    void givenNegativeSlippage_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("520.50"),
          new BigDecimal("1.0"), new BigDecimal("-0.5"), null, null, null, "PAPER"));
    }

    @Test
    void givenSellDirection_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "SELL", new BigDecimal("10.0"), new BigDecimal("530.0"),
          new BigDecimal("1.0"), new BigDecimal("0.5"), null, null, null, "PAPER"));
    }
  }
}
