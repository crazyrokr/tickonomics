package com.tickonomics.computation.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tickonomics.computation.backtest.Eq553SlippageModel;
import com.tickonomics.computation.kpi.IliResult;
import com.tickonomics.computation.kpi.SignalResult;
import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
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
class PaperTradingEngineTest {

  @Mock
  private VirtualPortfolio portfolio;
  @Mock
  private Eq553SlippageModel slippageModel;

  private DemoConfig activeConfig;
  private DemoConfig disabledConfig;
  private PaperTradingEngine activeEngine;
  private PaperTradingEngine disabledEngine;

  private final IliResult validIli = new IliResult(
      1.5, 0.5, -0.3, 0.2, IliResult.STATUS_VALID,
      new double[]{0.4, 0.35, 0.25}, "NORMAL", null);

  private final IliResult degradedIli = new IliResult(
      1.5, 0.5, -0.3, 0.2, IliResult.STATUS_DEGRADED,
      new double[]{0.4, 0.35, 0.25}, "NORMAL", null);

  private final IliResult dislocatedIli = new IliResult(
      1.5, 0.5, -0.3, 0.2, IliResult.STATUS_DISLOCATED,
      new double[]{0.4, 0.35, 0.25}, "NORMAL", null);

  private final SignalResult actionableBuy = new SignalResult(
      "SPY", SignalResult.DIR_BUY, SignalResult.STATUS_ACTIONABLE,
      85.0, 1.5, 0.02, 5.0, 0.7);

  private final SignalResult actionableSell = new SignalResult(
      "SPY", SignalResult.DIR_SELL, SignalResult.STATUS_ACTIONABLE,
      15.0, 1.5, 0.02, 5.0, 0.7);

  @BeforeEach
  void setUp() {
    activeConfig = new DemoConfig(true, 100_000.0, 5.0, 5.0, 10.0, true, true, true, 10_000_000.0);
    disabledConfig = new DemoConfig(false, 100_000.0, 5.0, 5.0, 10.0, false, true, true, 10_000_000.0);
    activeEngine = new PaperTradingEngine(portfolio, slippageModel, activeConfig);
    disabledEngine = new PaperTradingEngine(portfolio, slippageModel, disabledConfig);
  }

  @Nested
  class ProcessSignal {

    @Test
    void givenActionableBuySignal_whenProcessSignal_thenPositionOpened() {
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(null);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(2.0);
      when(portfolio.openPosition(any(), anyDouble())).thenReturn(
          new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY",
              9.6, 500.01, null, null, 475.0, 550.0, null, null));

      PaperTradingEngine.TradeResult result = activeEngine.processSignal(
          actionableBuy, validIli, 500.0);

      assertEquals("OPENED", result.action());
      assertEquals("SPY", result.symbol());
      assertEquals("BUY", result.direction());
      verify(portfolio).openPosition(any(), anyDouble());
    }

    @Test
    void givenActionableSellSignal_whenProcessSignal_thenPositionOpened() {
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(null);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(2.0);
      when(portfolio.openPosition(any(), anyDouble())).thenReturn(
          new VirtualPortfolioPosition(2L, Instant.now(), "SPY", "SELL",
              9.6, 499.99, null, null, 550.0, 450.0, null, null));

      PaperTradingEngine.TradeResult result = activeEngine.processSignal(
          actionableSell, validIli, 500.0);

      assertEquals("OPENED", result.action());
      assertEquals("SELL", result.direction());
    }

    @Test
    void givenDislocatedIliStatus_whenProcessSignal_thenSkipped() {
      PaperTradingEngine.TradeResult result = activeEngine.processSignal(
          actionableBuy, dislocatedIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertEquals("dislocated_data", result.reason());
      verify(portfolio, never()).openPosition(any(), anyDouble());
    }

    @Test
    void givenDegradedIliStatusAndConfigAllows_whenProcessSignal_thenTradeExecuted() {
      DemoConfig degradedAllowed = new DemoConfig(
          true, 100_000.0, 5.0, 5.0, 10.0, true, true, true, 10_000_000.0);
      PaperTradingEngine engine = new PaperTradingEngine(portfolio, slippageModel, degradedAllowed);

      when(portfolio.findOpenBySymbol("SPY")).thenReturn(null);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(1.0);
      when(portfolio.openPosition(any(), anyDouble())).thenReturn(
          new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY",
              9.6, 500.005, null, null, 475.0, 550.0, null, null));

      PaperTradingEngine.TradeResult result = engine.processSignal(
          actionableBuy, degradedIli, 500.0);

      assertEquals("OPENED", result.action());
    }

    @Test
    void givenDegradedIliStatusAndConfigDenies_whenProcessSignal_thenSkipped() {
      DemoConfig degradedDenied = new DemoConfig(
          true, 100_000.0, 5.0, 5.0, 10.0, true, false, true, 10_000_000.0);
      PaperTradingEngine engine = new PaperTradingEngine(portfolio, slippageModel, degradedDenied);

      PaperTradingEngine.TradeResult result = engine.processSignal(
          actionableBuy, degradedIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertEquals("degraded_data", result.reason());
    }

    @Test
    void givenSpeculativeStatus_whenProcessSignal_thenSkipped() {
      SignalResult speculative = new SignalResult(
          "SPY", SignalResult.DIR_BUY, SignalResult.STATUS_SPECULATIVE,
          85.0, 1.5, 0.02, 5.0, 0.7);

      PaperTradingEngine.TradeResult result = activeEngine.processSignal(
          speculative, validIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertTrue(result.reason().contains("non_actionable"));
    }

    @Test
    void givenCostExceedsStatus_whenProcessSignal_thenSkipped() {
      SignalResult costExceeds = new SignalResult(
          "SPY", SignalResult.DIR_BUY, SignalResult.STATUS_COST_EXCEEDS,
          85.0, 1.5, 0.02, 5.0, 0.7);

      PaperTradingEngine.TradeResult result = activeEngine.processSignal(
          costExceeds, validIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertTrue(result.reason().contains("non_actionable"));
    }

    @Test
    void givenDemoDisabled_whenProcessSignal_thenSkipped() {
      PaperTradingEngine.TradeResult result = disabledEngine.processSignal(
          actionableBuy, validIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertEquals("demo_disabled", result.reason());
    }

    @Test
    void givenExistingBuyPositionAndSellSignal_whenProcessSignal_thenOldClosedAndNewOpened() {
      VirtualPortfolioPosition existing = new VirtualPortfolioPosition(
          1L, Instant.now(), "SPY", "BUY", 10.0, 500.0, null, null, 475.0, 550.0, null, null);
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(existing);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(1.0);
      when(portfolio.openPosition(any(), anyDouble())).thenReturn(
          new VirtualPortfolioPosition(2L, Instant.now(), "SPY", "SELL",
              9.6, 499.95, null, null, 550.0, 450.0, null, null));

      PaperTradingEngine.TradeResult result = activeEngine.processSignal(
          actionableSell, validIli, 500.0);

      assertEquals("OPENED", result.action());
      verify(portfolio).closePosition(1L, 500.0);
      verify(portfolio).openPosition(any(), anyDouble());
    }

    @Test
    void givenExistingBuyPositionAndBuySignal_whenProcessSignal_thenSkipped() {
      VirtualPortfolioPosition existing = new VirtualPortfolioPosition(
          1L, Instant.now(), "SPY", "BUY", 10.0, 500.0, null, null, 475.0, 550.0, null, null);
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(existing);

      PaperTradingEngine.TradeResult result = activeEngine.processSignal(
          actionableBuy, validIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertEquals("existing_same_direction", result.reason());
    }

    @Test
    void givenBuySlippage_whenProcessSignal_thenFillPriceAboveMarket() {
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(null);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(10.0);
      when(portfolio.openPosition(any(), anyDouble())).thenReturn(
          new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY",
              9.6, 500.05, null, null, 475.0, 550.0, null, null));

      activeEngine.processSignal(actionableBuy, validIli, 500.0);

      verify(portfolio).openPosition(any(), anyDouble());
    }
  }

  @Nested
  class EvaluateExits {

    @Test
    void givenStopLossBreach_whenEvaluateExits_thenPositionClosed() {
      VirtualPortfolioPosition pos = new VirtualPortfolioPosition(
          1L, Instant.now(), "SPY", "BUY", 10.0, 100.0, null, null, 95.0, 110.0, null, null);
      when(portfolio.checkStopLossTakeProfit(any())).thenReturn(List.of(pos));
      when(portfolio.closePosition(1L, 94.0)).thenReturn(
          new VirtualPortfolioTrade(1L, Instant.now(), "SPY", "SELL", 10.0,
              94.0, 0, 0, -60.0, 1L, null, "PAPER"));

      List<VirtualPortfolioTrade> closed = activeEngine.evaluateExits(Map.of("SPY", 94.0));

      assertEquals(1, closed.size());
      verify(portfolio).markToMarket(Map.of("SPY", 94.0));
    }

    @Test
    void givenNoBreach_whenEvaluateExits_thenNoPositionsClosed() {
      when(portfolio.checkStopLossTakeProfit(any())).thenReturn(List.of());

      List<VirtualPortfolioTrade> closed = activeEngine.evaluateExits(
          Map.of("SPY", 102.0));

      assertTrue(closed.isEmpty());
      verify(portfolio).markToMarket(Map.of("SPY", 102.0));
    }
  }
}
