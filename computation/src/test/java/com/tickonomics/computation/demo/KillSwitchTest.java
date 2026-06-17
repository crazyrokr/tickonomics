package com.tickonomics.computation.demo;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KillSwitchTest {

  @Mock
  private VirtualPortfolio portfolio;

  private final KillSwitch killSwitch = new KillSwitch();

  @Nested
  class ActiveState {

    @Test
    void givenDefaultState_whenIsActive_thenFalse() {
      assertFalse(killSwitch.isActive());
    }

    @Test
    void givenActivated_whenIsActive_thenTrue() {
      killSwitch.activate();

      assertTrue(killSwitch.isActive());
    }

    @Test
    void givenActivatedThenDeactivated_whenIsActive_thenFalse() {
      killSwitch.activate();
      killSwitch.deactivate();

      assertFalse(killSwitch.isActive());
    }
  }

  @Nested
  class LiquidateAll {

    @Test
    void givenPositionsAndPrices_whenLiquidateAll_thenAllClosed() {
      VirtualPortfolioPosition spy = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY",
          new BigDecimal("10.0"), new BigDecimal("500.0"), null, null,
          new BigDecimal("475.0"), new BigDecimal("550.0"), null, null);
      VirtualPortfolioPosition aapl = new VirtualPortfolioPosition(2L, Instant.now(), "AAPL", "BUY",
          new BigDecimal("5.0"), new BigDecimal("180.0"), null, null,
          new BigDecimal("170.0"), new BigDecimal("200.0"), null, null);
      when(portfolio.findOpenPositions()).thenReturn(List.of(spy, aapl));
      when(portfolio.closePosition(1L, new BigDecimal("510.0"))).thenReturn(
          new VirtualPortfolioTrade(1L, Instant.now(), "SPY", "SELL",
              new BigDecimal("10.0"), new BigDecimal("510.0"), BigDecimal.ZERO, BigDecimal.ZERO,
              new BigDecimal("100.0"), 1L, null, "PAPER"));
      when(portfolio.closePosition(2L, new BigDecimal("190.0"))).thenReturn(
          new VirtualPortfolioTrade(2L, Instant.now(), "AAPL", "SELL",
              new BigDecimal("5.0"), new BigDecimal("190.0"), BigDecimal.ZERO, BigDecimal.ZERO,
              new BigDecimal("50.0"), 2L, null, "PAPER"));

      List<VirtualPortfolioTrade> trades = killSwitch.liquidateAll(
          portfolio, Map.of("SPY", new BigDecimal("510.0"), "AAPL", new BigDecimal("190.0")));

      assertEquals(2, trades.size());
      verify(portfolio).closePosition(1L, new BigDecimal("510.0"));
      verify(portfolio).closePosition(2L, new BigDecimal("190.0"));
    }

    @Test
    void givenPositionWithoutPrice_whenLiquidateAll_thenSkipped() {
      VirtualPortfolioPosition spy = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY",
          new BigDecimal("10.0"), new BigDecimal("500.0"), null, null,
          new BigDecimal("475.0"), new BigDecimal("550.0"), null, null);
      when(portfolio.findOpenPositions()).thenReturn(List.of(spy));

      List<VirtualPortfolioTrade> trades = killSwitch.liquidateAll(portfolio, Map.of());

      assertTrue(trades.isEmpty());
    }

    @Test
    void givenNullInputs_whenLiquidateAll_thenEmpty() {
      assertTrue(killSwitch.liquidateAll(null, Map.of("SPY", new BigDecimal("500.0"))).isEmpty());
      assertTrue(killSwitch.liquidateAll(portfolio, null).isEmpty());
    }
  }
}
