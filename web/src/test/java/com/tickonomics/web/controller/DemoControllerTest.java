package com.tickonomics.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tickonomics.computation.demo.KillSwitch;
import com.tickonomics.computation.demo.MarketPriceLookup;
import com.tickonomics.computation.demo.PaperTradingEngine;
import com.tickonomics.computation.demo.SignalQualityAnalyzer;
import com.tickonomics.computation.demo.SystemicResilienceMonitor;
import com.tickonomics.computation.demo.VirtualPortfolio;
import com.tickonomics.computation.leverage.LeverageSignaler;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import com.tickonomics.persistence.repository.SignalLogRepository;
import com.tickonomics.persistence.repository.VirtualPortfolioTradeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DemoControllerTest {

  @Mock
  private VirtualPortfolio portfolio;
  @Mock
  private PaperTradingEngine tradingEngine;
  @Mock
  private SignalQualityAnalyzer qualityAnalyzer;
  @Mock
  private VirtualPortfolioTradeRepository tradeRepository;
  @Mock
  private SignalLogRepository signalLogRepository;
  @Mock
  private MarketPriceLookup priceLookup;
  @Mock
  private LeverageSignaler leverageSignaler;
  @Mock
  private SystemicResilienceMonitor resilienceMonitor;

  private KillSwitch killSwitch;
  private DemoController controller;

  @BeforeEach
  void setUp() {
    killSwitch = new KillSwitch();
    controller = new DemoController(portfolio, tradingEngine, qualityAnalyzer,
        tradeRepository, signalLogRepository, killSwitch, priceLookup, leverageSignaler,
        resilienceMonitor);
  }

  @Nested
  class Portfolio {

    @Test
    @SuppressWarnings("unchecked")
    void givenPortfolioSummary_whenGetPortfolioSummary_thenBalanceReturned() {
      when(portfolio.getPortfolioSummary()).thenReturn(
          new VirtualPortfolio.PortfolioSummary(
              100_000.0, 105_000.0, 4_000.0, 1_000.0, 2, 10, 0.6, true));

      ResponseEntity<Map<String, Object>> response = controller.getPortfolioSummary();

      assertEquals(200, response.getStatusCode().value());
      Map<String, Object> body = response.getBody();
      assertEquals(105_000.0, (Double) body.get("balance"));
      assertEquals(5_000.0, (Double) body.get("totalPnl"));
      assertEquals(0.6, (Double) body.get("winRate"));
      assertTrue((Boolean) body.get("enabled"));
    }
  }

  @Nested
  class Trades {

    @Test
    @SuppressWarnings("unchecked")
    void givenTrades_whenGetTrades_thenTradeListReturned() {
      VirtualPortfolioTrade trade = new VirtualPortfolioTrade(
          1L, Instant.now(), "SPY", "BUY", 10.0, 500.0, 1.0, 0.5, null, 1L, null, "PAPER");
      when(tradeRepository.findLatest(50, 0)).thenReturn(List.of(trade));

      ResponseEntity<List<Map<String, Object>>> response = controller.getTrades(50, 0);

      assertEquals(200, response.getStatusCode().value());
      assertEquals(1, response.getBody().size());
      assertEquals("SPY", response.getBody().get(0).get("symbol"));
    }
  }

  @Nested
  class SignalQuality {

    @Test
    @SuppressWarnings("unchecked")
    void givenReportExists_whenGetSignalQuality_thenReportReturned() {
      Map<String, Object> report = Map.of("hitRate5d", 0.62);
      when(qualityAnalyzer.findLatestReport()).thenReturn(Optional.of(report));

      ResponseEntity<Map<String, Object>> response = controller.getSignalQuality();

      assertEquals(0.62, (Double) response.getBody().get("hitRate5d"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenNoReport_whenGetSignalQuality_thenEmptyMap() {
      when(qualityAnalyzer.findLatestReport()).thenReturn(Optional.empty());

      ResponseEntity<Map<String, Object>> response = controller.getSignalQuality();

      assertTrue(response.getBody().isEmpty());
    }
  }

  @Nested
  class ClosePosition {

    @Test
    @SuppressWarnings("unchecked")
    void givenOpenPosition_whenClosePosition_thenTradeReturned() {
      when(portfolio.closePosition(eq(1L), eq(550.0))).thenReturn(
          new VirtualPortfolioTrade(2L, Instant.now(), "SPY", "SELL", 10.0,
              550.0, 0, 0, 500.0, 1L, null, "PAPER"));

      ResponseEntity<Map<String, Object>> response = controller.closePosition(1L, 550.0);

      assertEquals(2L, response.getBody().get("tradeId"));
      assertEquals(500.0, (Double) response.getBody().get("realizedPnl"));
    }
  }

  @Nested
  class KillSwitchEndpoints {

    @Test
    @SuppressWarnings("unchecked")
    void givenNoPrices_whenActivateKillSwitch_thenActivatedWithoutLiquidation() {
      ResponseEntity<Map<String, Object>> response = controller.activateKillSwitch(null);

      assertTrue(killSwitch.isActive());
      assertEquals(true, response.getBody().get("active"));
      assertFalse(response.getBody().containsKey("liquidatedTrades"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenPrices_whenActivateKillSwitch_thenLiquidatedCountReturned() {
      com.tickonomics.persistence.entity.VirtualPortfolioPosition open =
          new com.tickonomics.persistence.entity.VirtualPortfolioPosition(
              1L, Instant.now(), "SPY", "BUY", 10.0, 500.0, null, null, 475.0, 550.0, null, null);
      VirtualPortfolioTrade closed = new VirtualPortfolioTrade(
          1L, Instant.now(), "SPY", "SELL", 10.0, 510.0, 0, 0, 100.0, 1L, null, "PAPER");
      when(portfolio.findOpenPositions()).thenReturn(List.of(open));
      when(portfolio.closePosition(eq(1L), eq(510.0))).thenReturn(closed);

      ResponseEntity<Map<String, Object>> response =
          controller.activateKillSwitch(Map.of("SPY", 510.0));

      assertTrue(killSwitch.isActive());
      assertEquals(true, response.getBody().get("active"));
      assertEquals(1, response.getBody().get("liquidatedTrades"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenActiveKillSwitch_whenDeactivate_thenInactive() {
      killSwitch.activate();

      controller.deactivateKillSwitch();

      assertFalse(killSwitch.isActive());
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenKillSwitchState_whenStatus_thenReflectsState() {
      ResponseEntity<Map<String, Object>> response = controller.killSwitchStatus();

      assertEquals(false, response.getBody().get("active"));
    }
  }

  @Nested
  class SafeModeEndpoints {

    @Test
    @SuppressWarnings("unchecked")
    void givenStatusReport_whenSafeModeStatus_thenReportFieldsReturned() {
      SystemicResilienceMonitor.StatusReport report = new SystemicResilienceMonitor.StatusReport(
          true, true, false, true, "overflow_utilization_exceeded + analytics_worker_degraded",
          Instant.parse("2026-06-13T10:00:00Z"),
          List.of("overflow_utilization_exceeded", "analytics_worker_degraded"), false);
      when(resilienceMonitor.status()).thenReturn(report);

      ResponseEntity<Map<String, Object>> response = controller.safeModeStatus();

      assertEquals(200, response.getStatusCode().value());
      Map<String, Object> body = response.getBody();
      assertEquals(true, body.get("active"));
      assertEquals(true, body.get("autoActivated"));
      assertEquals(false, body.get("manualOverride"));
      assertEquals(true, body.get("enabled"));
      assertEquals("overflow_utilization_exceeded + analytics_worker_degraded",
          body.get("lastReason"));
      assertEquals(2, ((List<?>) body.get("degradedIndicators")).size());
      assertEquals(false, body.get("recoveryReady"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenActivateSafeMode_whenCalled_thenDelegatesAndReturnsActive() {
      ResponseEntity<Map<String, Object>> response = controller.activateSafeMode();

      verify(resilienceMonitor).activateManual();
      assertEquals(true, response.getBody().get("active"));
      assertEquals("manual_override", response.getBody().get("source"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenDeactivateSafeMode_whenCalled_thenDelegatesAndReturnsInactive() {
      ResponseEntity<Map<String, Object>> response = controller.deactivateSafeMode();

      verify(resilienceMonitor).deactivateManual();
      assertEquals(false, response.getBody().get("active"));
      assertEquals("manual_ack", response.getBody().get("source"));
    }
  }

  @Nested
  class LeverageRotation {

    @Test
    @SuppressWarnings("unchecked")
    void givenRotationOutcome_whenEvaluate_thenSignalReturned() {
      LeverageSignaler.LeverageSignal signal = new LeverageSignaler.LeverageSignal(
          LeverageSignaler.Signal.LEVERAGE_OFF, 90.0, 100.0, -0.10, Instant.now());
      when(portfolio.applyLeverageRotation(eq(priceLookup), eq(leverageSignaler), any()))
          .thenReturn(new VirtualPortfolio.LeverageRotationOutcome(signal, List.of()));

      ResponseEntity<Map<String, Object>> response =
          controller.evaluateLeverageRotation(Map.of("SPY", 90.0));

      assertEquals("LEVERAGE_OFF", response.getBody().get("signal"));
      assertEquals(90.0, (Double) response.getBody().get("benchmarkPrice"));
      assertEquals(0, response.getBody().get("closedTrades"));
      verify(portfolio).applyLeverageRotation(eq(priceLookup), eq(leverageSignaler), any());
    }
  }
}
