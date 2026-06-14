package com.tickonomics.computation.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tickonomics.computation.backtest.Eq553SlippageModel;
import com.tickonomics.computation.execution.ComparativeExecutionAnalysis;
import com.tickonomics.computation.kpi.IliResult;
import com.tickonomics.computation.kpi.SignalResult;
import com.tickonomics.computation.portfolio.PortfolioManagementAlgebra;
import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaperTradingEngineTest {

  @Mock
  private VirtualPortfolio portfolio;
  @Mock
  private Eq553SlippageModel slippageModel;
  @Mock
  private MarkovStopHandler markovStopHandler;

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

  private VirtualPortfolioPosition openedPosition(long id, String direction, double fillPrice) {
    return new VirtualPortfolioPosition(
        id, Instant.now(), "SPY", direction, 9.6, fillPrice, null, null,
        fillPrice * 0.95, fillPrice * 1.10, null, null);
  }

  private PaperTradingEngine buildEngine(DemoConfig config) {
    return buildEngine(config, new KillSwitch(), markovStopHandler);
  }

  private PaperTradingEngine buildEngine(DemoConfig config, KillSwitch killSwitch,
      MarkovStopHandler stopsHandler) {
    return buildEngine(config, killSwitch, stopsHandler,
        new SystemicResilienceMonitor(config, () -> ResilienceHealthSnapshot.unknown()));
  }

  private PaperTradingEngine buildEngine(DemoConfig config, KillSwitch killSwitch,
      MarkovStopHandler stopsHandler, SystemicResilienceMonitor monitor) {
    return new PaperTradingEngine(portfolio, slippageModel, config, killSwitch,
        monitor, new MarketStabilityGuard(), new OrderImpactPredictor(), stopsHandler,
        new PortfolioManagementAlgebra(), new MarketMakerExecutionModel(),
        new FillProbabilityEngine(), new PassiveExecutionHandler(), new SniperExecutionHandler(),
        new ComparativeExecutionAnalysis(), new RandomizedExecutionWindow());
  }

  private DemoConfig activeConfig() {
    return DemoConfig.core(true, 100_000.0, 5.0, 5.0, 10.0, true, true, true, 10_000_000.0);
  }

  private DemoConfig fullConfig(boolean marketMaker, boolean orderImpact, boolean dynamicStops,
      double maxImpactBps) {
    return new DemoConfig(
        true, 100_000.0, 5.0, 5.0, 10.0, true, true, true, 10_000_000.0,
        DemoConfig.AdvancedCostModel.defaults(), DemoConfig.RandomizedExecution.defaults(),
        DemoConfig.MarketStabilityGuard.defaults(),
        new DemoConfig.OrderImpactPredictor(orderImpact, maxImpactBps),
        new DemoConfig.MarketMakerMode(marketMaker, true, 60.0),
        new DemoConfig.DynamicStops(dynamicStops, "7d"),
        DemoConfig.PortfolioAlgebra.defaults(), DemoConfig.LeverageRotation.defaults(),
        DemoConfig.KillSwitchConfig.defaults(), DemoConfig.GlobalSafeMode.defaults());
  }

  private void stubOpen() {
    when(portfolio.findOpenBySymbol("SPY")).thenReturn(null);
    when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(2.0);
    when(portfolio.openPosition(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(openedPosition(1L, "BUY", 500.1));
  }

  @Nested
  class ProcessSignalCore {

    @Test
    void givenActionableBuySignal_whenProcessSignal_thenPositionOpened() {
      PaperTradingEngine engine = buildEngine(activeConfig());
      stubOpen();

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, validIli, 500.0);

      assertEquals("OPENED", result.action());
      assertEquals("BUY", result.direction());
      verify(portfolio).openPosition(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void givenActionableSellSignal_whenProcessSignal_thenPositionOpened() {
      PaperTradingEngine engine = buildEngine(activeConfig());
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(null);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(2.0);
      when(portfolio.openPosition(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(openedPosition(2L, "SELL", 499.9));

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableSell, validIli, 500.0);

      assertEquals("OPENED", result.action());
      assertEquals("SELL", result.direction());
    }

    @Test
    void givenDislocatedIliStatus_whenProcessSignal_thenSkipped() {
      PaperTradingEngine engine = buildEngine(activeConfig());

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, dislocatedIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertEquals("dislocated_data", result.reason());
      verify(portfolio, never()).openPosition(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void givenDegradedIliStatusAndConfigDenies_whenProcessSignal_thenSkipped() {
      DemoConfig degradedDenied = DemoConfig.core(
          true, 100_000.0, 5.0, 5.0, 10.0, true, false, true, 10_000_000.0);
      PaperTradingEngine engine = buildEngine(degradedDenied);

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, degradedIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertEquals("degraded_data", result.reason());
    }

    @Test
    void givenSpeculativeStatus_whenProcessSignal_thenSkipped() {
      SignalResult speculative = new SignalResult(
          "SPY", SignalResult.DIR_BUY, SignalResult.STATUS_SPECULATIVE,
          85.0, 1.5, 0.02, 5.0, 0.7);
      PaperTradingEngine engine = buildEngine(activeConfig());

      PaperTradingEngine.TradeResult result = engine.processSignal(speculative, validIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertTrue(result.reason().contains("non_actionable"));
    }

    @Test
    void givenDemoDisabled_whenProcessSignal_thenSkipped() {
      DemoConfig disabledConfig = DemoConfig.core(
          false, 100_000.0, 5.0, 5.0, 10.0, false, true, true, 10_000_000.0);
      PaperTradingEngine engine = buildEngine(disabledConfig);

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, validIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertEquals("demo_disabled", result.reason());
    }

    @Test
    void givenExistingBuyPositionAndSellSignal_whenProcessSignal_thenOldClosedAndNewOpened() {
      VirtualPortfolioPosition existing = openedPosition(1L, "BUY", 500.0);
      PaperTradingEngine engine = buildEngine(activeConfig());
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(existing);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(1.0);
      when(portfolio.openPosition(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(openedPosition(2L, "SELL", 499.95));

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableSell, validIli, 500.0);

      assertEquals("OPENED", result.action());
      verify(portfolio).closePosition(1L, 500.0);
      verify(portfolio).openPosition(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void givenExistingSameDirectionPosition_whenProcessSignal_thenSkipped() {
      VirtualPortfolioPosition existing = openedPosition(1L, "BUY", 500.0);
      PaperTradingEngine engine = buildEngine(activeConfig());
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(existing);

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, validIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertEquals("existing_same_direction", result.reason());
    }
  }

  @Nested
  class V5Gates {

    @Test
    void givenKillSwitchActive_whenProcessSignal_thenSkipped() {
      KillSwitch killSwitch = new KillSwitch();
      killSwitch.activate();
      PaperTradingEngine engine = buildEngine(activeConfig(), killSwitch, markovStopHandler);

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, validIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertEquals("kill_switch_active", result.reason());
      verify(portfolio, never()).openPosition(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void givenGlobalSafeModeActive_whenProcessSignal_thenSkipped() {
      SystemicResilienceMonitor monitor =
          new SystemicResilienceMonitor(activeConfig(), () -> ResilienceHealthSnapshot.unknown());
      monitor.activateManual();
      PaperTradingEngine engine =
          buildEngine(activeConfig(), new KillSwitch(), markovStopHandler, monitor);

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, validIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertEquals("global_safe_mode", result.reason());
      verify(portfolio, never()).openPosition(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void givenUnstablePortfolio_whenProcessSignal_thenSkipped() {
      PaperTradingEngine engine = buildEngine(activeConfig());
      PaperTradingEngine.ExecutionContext ctx = new PaperTradingEngine.ExecutionContext(
          List.of(0.10, -0.10, 0.12, -0.08), 0.0, 0.0, 0.0, 0.0);

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, validIli, 500.0, ctx);

      assertEquals("SKIPPED", result.action());
      assertEquals("market_unstable", result.reason());
    }

    @Test
    void givenStablePortfolio_whenProcessSignal_thenOpened() {
      PaperTradingEngine engine = buildEngine(activeConfig());
      stubOpen();
      PaperTradingEngine.ExecutionContext ctx = new PaperTradingEngine.ExecutionContext(
          List.of(0.001, -0.001, 0.002, -0.002), 0.0, 0.0, 0.0, 0.0);

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, validIli, 500.0, ctx);

      assertEquals("OPENED", result.action());
    }

    @Test
    void givenExcessiveOrderImpact_whenProcessSignal_thenSkipped() {
      PaperTradingEngine engine = buildEngine(fullConfig(false, true, false, 0.01));

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, validIli, 500.0);

      assertEquals("SKIPPED", result.action());
      assertEquals("order_impact_excessive", result.reason());
    }

    @Test
    void givenAcceptableOrderImpact_whenProcessSignal_thenOpened() {
      PaperTradingEngine engine = buildEngine(fullConfig(false, true, false, 10.0));
      stubOpen();

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, validIli, 500.0);

      assertEquals("OPENED", result.action());
    }
  }

  @Nested
  class ExecutionModel {

    @Test
    void givenDynamicStopsPresent_whenProcessSignal_thenCalibratedLevelsApplied() {
      when(markovStopHandler.stopsFor("SPY")).thenReturn(
          Optional.of(new MarkovStopHandler.CalibratedStops("SPY", 4.0, 8.0, true)));
      PaperTradingEngine engine = buildEngine(fullConfig(false, false, true, 10.0));
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(null);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(2.0);
      when(portfolio.openPosition(any(), anyDouble(), eq(4.0), eq(8.0), anyDouble()))
          .thenReturn(openedPosition(1L, "BUY", 500.1));

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, validIli, 500.0);

      assertEquals("OPENED", result.action());
      verify(portfolio).openPosition(any(), anyDouble(), eq(4.0), eq(8.0), anyDouble());
    }

    @Test
    void givenFillProbabilityInflatesSlippage_whenProcessSignal_thenFillPriceReflectsHigherSlippage() {
      PaperTradingEngine engine = buildEngine(activeConfig());
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(null);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(10.0);
      ArgumentCaptor<Double> fillPriceCaptor = ArgumentCaptor.forClass(Double.class);
      when(portfolio.openPosition(any(), fillPriceCaptor.capture(), anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(openedPosition(1L, "BUY", 501.0));

      PaperTradingEngine.ExecutionContext ctx = new PaperTradingEngine.ExecutionContext(
          List.of(), 1.0, 0.0, 0.0, 0.0);
      engine.processSignal(actionableBuy, validIli, 500.0, ctx);

      assertEquals(501.0, fillPriceCaptor.getValue(), 0.0001);
    }

    @Test
    void givenMarketMakerLimitMode_whenProcessSignal_thenStrategyIsLimitAndSpreadCaptured() {
      PaperTradingEngine engine = buildEngine(fullConfig(true, false, false, 10.0));
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(null);
      when(portfolio.openPosition(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(openedPosition(1L, "BUY", 100.0));
      PaperTradingEngine.ExecutionContext ctx = new PaperTradingEngine.ExecutionContext(
          List.of(), 0.0, 99.0, 101.0, 0.0);

      PaperTradingEngine.TradeResult result = engine.processSignal(actionableBuy, validIli, 100.0, ctx);

      assertEquals("OPENED", result.action());
      assertEquals("LIMIT", result.strategy());
      assertTrue(result.spreadCaptureBps() > 0.0);
    }

    @Test
    void givenStandardizedCostModel_whenProcessSignal_thenCommissionApplied() {
      PaperTradingEngine engine = buildEngine(activeConfig());
      ArgumentCaptor<Double> commissionCaptor = ArgumentCaptor.forClass(Double.class);
      when(portfolio.findOpenBySymbol("SPY")).thenReturn(null);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(2.0);
      when(portfolio.openPosition(any(), anyDouble(), anyDouble(), anyDouble(), commissionCaptor.capture()))
          .thenReturn(openedPosition(1L, "BUY", 500.1));

      engine.processSignal(actionableBuy, validIli, 500.0);

      assertTrue(commissionCaptor.getValue() > 0.0);
    }

    @Test
    void givenOpenedTrade_whenProcessSignal_thenExecutionComparisonRecorded() {
      PaperTradingEngine engine = buildEngine(activeConfig());
      stubOpen();

      engine.processSignal(actionableBuy, validIli, 500.0);

      assertNotNull(engine.executionComparison());
    }
  }

  @Nested
  class EvaluateExits {

    @Test
    void givenStopLossBreach_whenEvaluateExits_thenPositionClosed() {
      VirtualPortfolioPosition pos = openedPosition(1L, "BUY", 100.0);
      PaperTradingEngine engine = buildEngine(activeConfig());
      when(portfolio.checkStopLossTakeProfit(any())).thenReturn(List.of(pos));
      when(portfolio.closePosition(1L, 94.0)).thenReturn(
          new VirtualPortfolioTrade(1L, Instant.now(), "SPY", "SELL", 10.0,
              94.0, 0, 0, -60.0, 1L, null, "PAPER"));

      List<VirtualPortfolioTrade> closed = engine.evaluateExits(Map.of("SPY", 94.0));

      assertEquals(1, closed.size());
      verify(portfolio).markToMarket(Map.of("SPY", 94.0));
    }

    @Test
    void givenNoBreach_whenEvaluateExits_thenNoPositionsClosed() {
      PaperTradingEngine engine = buildEngine(activeConfig());
      when(portfolio.checkStopLossTakeProfit(any())).thenReturn(List.of());

      List<VirtualPortfolioTrade> closed = engine.evaluateExits(Map.of("SPY", 102.0));

      assertTrue(closed.isEmpty());
      verify(portfolio).markToMarket(Map.of("SPY", 102.0));
    }
  }
}
