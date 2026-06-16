package com.tickonomics.computation.demo;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tickonomics.computation.kpi.SignalResult;
import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import com.tickonomics.persistence.repository.VirtualPortfolioPositionRepository;
import com.tickonomics.persistence.repository.VirtualPortfolioTradeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VirtualPortfolioTest {

  @Mock
  private VirtualPortfolioPositionRepository positionRepository;
  @Mock
  private VirtualPortfolioTradeRepository tradeRepository;

  private DemoConfig config;
  private VirtualPortfolio portfolio;

  private final SignalResult buySignal = new SignalResult(
      "SPY", SignalResult.DIR_BUY, SignalResult.STATUS_ACTIONABLE,
      85.0, 1.5, 0.02, 5.0, 0.7);

  @BeforeEach
  void setUp() {
    config = DemoConfig.core(true, new BigDecimal("100000.00"), 5.0, 5.0, 10.0, true, true, true, 10_000_000.0);
    portfolio = new VirtualPortfolio(positionRepository, tradeRepository, config);
  }

  @Nested
  class OpenPosition {

    @Test
    void givenBuySignal_whenOpenPosition_thenPositionCreatedWithBuyDirection() {
      when(positionRepository.save(any())).thenReturn(1L);
      when(tradeRepository.save(any())).thenReturn(1L);

      VirtualPortfolioPosition result = portfolio.openPosition(buySignal, new BigDecimal("520.50"));

      assertNotNull(result);
      assertEquals("SPY", result.symbol());
      assertEquals("BUY", result.direction());
      assertEquals(0, new BigDecimal("520.50").compareTo(result.entryPrice()));
      verify(positionRepository).save(any());
      verify(tradeRepository).save(any());
    }

    @Test
    void givenBuySignal_whenOpenPosition_thenStopLossBelowEntry() {
      when(positionRepository.save(any())).thenReturn(1L);
      when(tradeRepository.save(any())).thenReturn(1L);

      VirtualPortfolioPosition result = portfolio.openPosition(buySignal, new BigDecimal("100.0"));

      assertTrue(result.stopLossPrice().compareTo(new BigDecimal("100.0")) < 0);
      assertTrue(result.takeProfitPrice().compareTo(new BigDecimal("100.0")) > 0);
    }

    @Test
    void givenSellSignal_whenOpenPosition_thenPositionCreatedWithSellDirection() {
      SignalResult sellSignal = new SignalResult(
          "SPY", SignalResult.DIR_SELL, SignalResult.STATUS_ACTIONABLE,
          15.0, 1.5, 0.02, 5.0, 0.7);
      when(positionRepository.save(any())).thenReturn(1L);
      when(tradeRepository.save(any())).thenReturn(1L);

      VirtualPortfolioPosition result = portfolio.openPosition(sellSignal, new BigDecimal("520.50"));

      assertEquals("SELL", result.direction());
    }

    @Test
    void givenPositionSizePct_whenOpenPosition_thenCorrectQuantity() {
      when(positionRepository.save(any())).thenReturn(1L);
      when(tradeRepository.save(any())).thenReturn(1L);

      BigDecimal fillPrice = new BigDecimal("500.0");
      BigDecimal expectedSize = config.virtualBalance()
          .multiply(BigDecimal.valueOf(config.positionSizePct()))
          .divide(new BigDecimal("100"), java.math.MathContext.DECIMAL64);
      BigDecimal expectedQuantity = expectedSize.divide(fillPrice, 4, java.math.RoundingMode.HALF_UP);

      VirtualPortfolioPosition result = portfolio.openPosition(buySignal, fillPrice);

      assertEquals(0, expectedQuantity.compareTo(result.quantity()));
    }
  }

  @Nested
  class ClosePosition {

    @Test
    void givenOpenBuyPosition_whenClosePosition_thenRealizedPnlPositive() {
      VirtualPortfolioPosition openPos = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("500.0"), null, null, new BigDecimal("475.0"), new BigDecimal("550.0"), null, null);
      when(positionRepository.findOpenPositions()).thenReturn(List.of(openPos));
      when(tradeRepository.save(any())).thenReturn(2L);

      VirtualPortfolioTrade trade = portfolio.closePosition(1L, new BigDecimal("550.0"));

      assertNotNull(trade);
      assertEquals(0, new BigDecimal("500.0").compareTo(trade.realizedPnl()));
      verify(positionRepository).close(eq(1L), any(Instant.class), eq(new BigDecimal("550.0")));
    }

    @Test
    void givenOpenSellPosition_whenClosePosition_thenRealizedPnlPositive() {
      VirtualPortfolioPosition openPos = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "SELL", new BigDecimal("10.0"), new BigDecimal("500.0"), null, null, new BigDecimal("550.0"), new BigDecimal("450.0"), null, null);
      when(positionRepository.findOpenPositions()).thenReturn(List.of(openPos));
      when(tradeRepository.save(any())).thenReturn(2L);

      VirtualPortfolioTrade trade = portfolio.closePosition(1L, new BigDecimal("450.0"));

      assertEquals(0, new BigDecimal("500.0").compareTo(trade.realizedPnl()));
    }

    @Test
    void givenNonexistentPosition_whenClosePosition_thenThrows() {
      when(positionRepository.findOpenPositions()).thenReturn(List.of());

      assertThrows(IllegalArgumentException.class, () -> portfolio.closePosition(999L, new BigDecimal("500.0")));
    }
  }

  @Nested
  class MarkToMarket {

    @Test
    void givenOpenPositionsWithPrices_whenMarkToMarket_thenUnrealizedPnlUpdated() {
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("500.0"), null, null, null, null, null, null);
      when(positionRepository.findOpenPositions()).thenReturn(List.of(pos));

      portfolio.markToMarket(Map.of("SPY", new BigDecimal("520.0")));

      verify(positionRepository).updateMarkToMarket(eq(1L), eq(new BigDecimal("520.0")),
          org.mockito.ArgumentMatchers.argThat(
              (BigDecimal bd) -> bd.compareTo(new BigDecimal("200.0")) == 0));
    }

    @Test
    void givenOpenPositionWithoutPrice_whenMarkToMarket_thenNoUpdate() {
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("500.0"), null, null, null, null, null, null);
      when(positionRepository.findOpenPositions()).thenReturn(List.of(pos));

      portfolio.markToMarket(Map.of("AAPL", new BigDecimal("180.0")));

      verify(positionRepository, never()).updateMarkToMarket(anyLong(), any(BigDecimal.class), any(BigDecimal.class));
    }
  }

  @Nested
  class CheckStopLossTakeProfit {

    @Test
    void givenBuyPositionAtStopLoss_whenCheck_thenPositionFlagged() {
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("100.0"), null, null, new BigDecimal("95.0"), new BigDecimal("110.0"), null, null);
      when(positionRepository.findOpenPositions()).thenReturn(List.of(pos));

      List<VirtualPortfolioPosition> breached = portfolio.checkStopLossTakeProfit(
          Map.of("SPY", new BigDecimal("94.0")));

      assertEquals(1, breached.size());
      assertEquals(1L, breached.getFirst().id());
    }

    @Test
    void givenBuyPositionAtTakeProfit_whenCheck_thenPositionFlagged() {
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("100.0"), null, null, new BigDecimal("95.0"), new BigDecimal("110.0"), null, null);
      when(positionRepository.findOpenPositions()).thenReturn(List.of(pos));

      List<VirtualPortfolioPosition> breached = portfolio.checkStopLossTakeProfit(
          Map.of("SPY", new BigDecimal("111.0")));

      assertEquals(1, breached.size());
    }

    @Test
    void givenPositionWithinBounds_whenCheck_thenNotFlagged() {
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("100.0"), null, null, new BigDecimal("95.0"), new BigDecimal("110.0"), null, null);
      when(positionRepository.findOpenPositions()).thenReturn(List.of(pos));

      List<VirtualPortfolioPosition> breached = portfolio.checkStopLossTakeProfit(
          Map.of("SPY", new BigDecimal("102.0")));

      assertTrue(breached.isEmpty());
    }

    @Test
    void givenSellPositionAtStopLoss_whenCheck_thenPositionFlagged() {
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "SELL", new BigDecimal("10.0"), new BigDecimal("100.0"), null, null, new BigDecimal("105.0"), new BigDecimal("90.0"), null, null);
      when(positionRepository.findOpenPositions()).thenReturn(List.of(pos));

      List<VirtualPortfolioPosition> breached = portfolio.checkStopLossTakeProfit(
          Map.of("SPY", new BigDecimal("106.0")));

      assertEquals(1, breached.size());
    }

    @Test
    void givenSellPositionAtTakeProfit_whenCheck_thenPositionFlagged() {
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "SELL", new BigDecimal("10.0"), new BigDecimal("100.0"), null, null, new BigDecimal("105.0"), new BigDecimal("90.0"), null, null);
      when(positionRepository.findOpenPositions()).thenReturn(List.of(pos));

      List<VirtualPortfolioPosition> breached = portfolio.checkStopLossTakeProfit(
          Map.of("SPY", new BigDecimal("89.0")));

      assertEquals(1, breached.size());
    }
  }

  @Nested
  class GetPortfolioSummary {

    @Test
    void givenNoTrades_whenGetSummary_thenZeroPnlAndWinRate() {
      when(positionRepository.findOpenPositions()).thenReturn(List.of());
      when(tradeRepository.countByTradeType("PAPER")).thenReturn(0);
      when(tradeRepository.findLatest(0, 0)).thenReturn(List.of());

      VirtualPortfolio.PortfolioSummary summary = portfolio.getPortfolioSummary();

      assertEquals(0, new BigDecimal("100000.00").compareTo(summary.initialBalance()));
      assertEquals(0, new BigDecimal("100000.00").compareTo(summary.currentBalance()));
      assertEquals(0, BigDecimal.ZERO.compareTo(summary.realizedPnl()));
      assertEquals(0, BigDecimal.ZERO.compareTo(summary.unrealizedPnl()));
      assertEquals(0.0, summary.winRate(), 0.001);
    }

    @Test
    void givenWinningAndLosingTrades_whenGetSummary_thenCorrectWinRate() {
      when(positionRepository.findOpenPositions()).thenReturn(List.of());

      VirtualPortfolioTrade win = new VirtualPortfolioTrade(1L, Instant.now(), "SPY", "SELL", new BigDecimal("10.0"), new BigDecimal("500.0"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.0"), null, null, "PAPER");
      VirtualPortfolioTrade loss = new VirtualPortfolioTrade(2L, Instant.now(), "AAPL", "BUY", new BigDecimal("10.0"), new BigDecimal("180.0"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-50.0"), null, null, "PAPER");

      when(tradeRepository.countByTradeType("PAPER")).thenReturn(2);
      when(tradeRepository.findLatest(2, 0)).thenReturn(List.of(win, loss));

      VirtualPortfolio.PortfolioSummary summary = portfolio.getPortfolioSummary();

      assertEquals(0, new BigDecimal("50.0").compareTo(summary.realizedPnl()));
      assertEquals(0.5, summary.winRate(), 0.001);
    }
  }
}
