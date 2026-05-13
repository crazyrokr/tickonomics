package com.tickonomics.persistence.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VirtualPortfolioTradeTest {

  private VirtualPortfolioTrade defaultTrade() {
    return new VirtualPortfolioTrade(
        null, Instant.now(), "SPY", "BUY", 10.0, 520.50, 1.0, 0.5, null, null, null, "PAPER");
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
          1L, now, "SPY", "SELL", 10.0, 530.25, 1.0, 0.3, 97.50, 42L, 7L, "PAPER");
      assertEquals(1L, trade.id());
      assertEquals(now, trade.executedAt());
      assertEquals("SPY", trade.symbol());
      assertEquals("SELL", trade.direction());
      assertEquals(10.0, trade.quantity());
      assertEquals(530.25, trade.fillPrice());
      assertEquals(1.0, trade.commission());
      assertEquals(0.3, trade.slippage());
      assertEquals(97.50, trade.realizedPnl());
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
          null, Instant.now(), "SPY", "BUY", 10.0, 520.50, 1.0, 0.5, null, null, null, "PAPER"));
    }
  }

  @Nested
  class Validation {

    @Test
    void givenNullSymbol_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), null, "BUY", 10.0, 520.50, 1.0, 0.5, null, null, null, "PAPER"));
    }

    @Test
    void givenBlankSymbol_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "  ", "BUY", 10.0, 520.50, 1.0, 0.5, null, null, null, "PAPER"));
    }

    @Test
    void givenNullDirection_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", null, 10.0, 520.50, 1.0, 0.5, null, null, null, "PAPER"));
    }

    @Test
    void givenInvalidDirection_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "HOLD", 10.0, 520.50, 1.0, 0.5, null, null, null, "PAPER"));
    }

    @Test
    void givenZeroQuantity_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", 0, 520.50, 1.0, 0.5, null, null, null, "PAPER"));
    }

    @Test
    void givenNegativeQuantity_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", -5.0, 520.50, 1.0, 0.5, null, null, null, "PAPER"));
    }

    @Test
    void givenZeroFillPrice_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", 10.0, 0, 1.0, 0.5, null, null, null, "PAPER"));
    }

    @Test
    void givenNegativeFillPrice_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", 10.0, -100.0, 1.0, 0.5, null, null, null, "PAPER"));
    }

    @Test
    void givenNegativeCommission_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", 10.0, 520.50, -1.0, 0.5, null, null, null, "PAPER"));
    }

    @Test
    void givenNegativeSlippage_whenConstruct_thenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "BUY", 10.0, 520.50, 1.0, -0.5, null, null, null, "PAPER"));
    }

    @Test
    void givenSellDirection_whenConstruct_thenSuccess() {
      assertDoesNotThrow(() -> new VirtualPortfolioTrade(
          null, Instant.now(), "SPY", "SELL", 10.0, 530.0, 1.0, 0.5, null, null, null, "PAPER"));
    }
  }
}
