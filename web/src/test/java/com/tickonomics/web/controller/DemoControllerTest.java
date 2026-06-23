package com.tickonomics.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tickonomics.computation.demo.KillSwitch;
import com.tickonomics.computation.demo.MarketPriceLookup;
import com.tickonomics.computation.demo.SignalQualityAnalyzer;
import com.tickonomics.computation.demo.SystemicResilienceMonitor;
import com.tickonomics.computation.demo.VirtualPortfolio;
import com.tickonomics.computation.leverage.LeverageSignaler;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import com.tickonomics.persistence.repository.VirtualPortfolioTradeRepository;
import com.tickonomics.web.controller.dto.DemoCloseResultResponse;
import com.tickonomics.web.controller.dto.DemoPortfolioResponse;
import com.tickonomics.web.controller.dto.DemoTradeResponse;
import com.tickonomics.web.controller.dto.KillSwitchResponse;
import com.tickonomics.web.controller.dto.LeverageRotationResponse;
import com.tickonomics.web.controller.dto.SafeModeStatusResponse;
import com.tickonomics.web.controller.dto.SafeModeToggleResponse;
import com.tickonomics.web.exception.RateLimitExceededException;
import com.tickonomics.web.security.RateLimitGuard;
import java.math.BigDecimal;
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
  private SignalQualityAnalyzer qualityAnalyzer;
  @Mock
  private VirtualPortfolioTradeRepository tradeRepository;
  @Mock
  private MarketPriceLookup priceLookup;
  @Mock
  private LeverageSignaler leverageSignaler;
  @Mock
  private SystemicResilienceMonitor resilienceMonitor;
  @Mock
  private RateLimitGuard rateLimiter;

  private KillSwitch killSwitch;
  private DemoController controller;

  @BeforeEach
  void setUp() {
    killSwitch = new KillSwitch();
    controller = new DemoController(portfolio, qualityAnalyzer, tradeRepository,
        killSwitch, priceLookup, leverageSignaler, resilienceMonitor, rateLimiter);
  }

  @Nested
  class Portfolio {

    @Test
    void givenPortfolioSummary_whenGetPortfolioSummary_thenBalanceReturned() {
      when(portfolio.getPortfolioSummary()).thenReturn(
          new VirtualPortfolio.PortfolioSummary(
              new BigDecimal("100000.00"), new BigDecimal("105000.00"),
              new BigDecimal("4000.00"), new BigDecimal("1000.00"),
              2, 10, 0.6, true));

      ResponseEntity<DemoPortfolioResponse> response = controller.getPortfolioSummary();

      assertEquals(200, response.getStatusCode().value());
      DemoPortfolioResponse body = response.getBody();
      assertEquals(0, new BigDecimal("105000.00").compareTo(body.balance()));
      assertEquals(0, new BigDecimal("5000.00").compareTo(body.totalPnl()));
      assertEquals(0.6, body.winRate());
      assertTrue(body.enabled());
    }
  }

  @Nested
  class Trades {

    @Test
    void givenTrades_whenGetTrades_thenTradeListReturned() {
      VirtualPortfolioTrade trade = new VirtualPortfolioTrade(
          1L, Instant.now(), "SPY", "BUY", new BigDecimal("10.0"), new BigDecimal("500.0"),
          new BigDecimal("1.0"), new BigDecimal("0.5"), null, 1L, null, "PAPER");
      when(tradeRepository.findLatest(50, 0)).thenReturn(List.of(trade));

      ResponseEntity<List<DemoTradeResponse>> response = controller.getTrades(50, 0);

      assertEquals(200, response.getStatusCode().value());
      assertEquals(1, response.getBody().size());
      assertEquals("SPY", response.getBody().get(0).symbol());
    }
  }

  @Nested
  class SignalQuality {

    @Test
    void givenReportExists_whenGetSignalQuality_thenReportReturned() {
      Map<String, Object> report = Map.of("hitRate5d", 0.62);
      when(qualityAnalyzer.findLatestReport()).thenReturn(Optional.of(report));

      ResponseEntity<Map<String, Object>> response = controller.getSignalQuality();

      assertEquals(0.62, (Double) response.getBody().get("hitRate5d"));
    }

    @Test
    void givenNoReport_whenGetSignalQuality_thenEmptyMap() {
      when(qualityAnalyzer.findLatestReport()).thenReturn(Optional.empty());

      ResponseEntity<Map<String, Object>> response = controller.getSignalQuality();

      assertTrue(response.getBody().isEmpty());
    }
  }

  @Nested
  class ClosePosition {

    @Test
    void givenOpenPosition_whenClosePosition_thenTradeReturned() {
      when(rateLimiter.tryAcquire("demo.close-position")).thenReturn(true);
      when(portfolio.closePosition(eq(1L), eq(new BigDecimal("550.0")))).thenReturn(
          new VirtualPortfolioTrade(2L, Instant.now(), "SPY", "SELL",
              new BigDecimal("10.0"), new BigDecimal("550.0"),
              BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500.0"),
              1L, null, "PAPER"));

      ResponseEntity<DemoCloseResultResponse> response =
          controller.closePosition(1L, new BigDecimal("550.0"));

      DemoCloseResultResponse body = response.getBody();
      assertEquals(2L, body.tradeId());
      assertEquals(0, new BigDecimal("500.0").compareTo(body.realizedPnl()));
    }

    @Test
    void givenRateLimited_whenClosePosition_thenThrowsRateLimitExceeded() {
      // Given the rate limiter denies further calls
      when(rateLimiter.tryAcquire("demo.close-position")).thenReturn(false);

      // When closing a position
      // Then a RateLimitExceededException is raised (translated to HTTP 429 by the advice)
      assertThrows(RateLimitExceededException.class,
          () -> controller.closePosition(1L, new BigDecimal("550.0")));
    }
  }

  @Nested
  class KillSwitchEndpoints {

    @Test
    void givenNoPrices_whenActivateKillSwitch_thenActivatedWithoutLiquidation() {
      when(rateLimiter.tryAcquire("demo.kill-switch")).thenReturn(true);
      ResponseEntity<KillSwitchResponse> response = controller.activateKillSwitch(null);

      assertTrue(killSwitch.isActive());
      assertTrue(response.getBody().active());
      assertNull(response.getBody().liquidatedTrades());
    }

    @Test
    void givenPrices_whenActivateKillSwitch_thenLiquidatedCountReturned() {
      when(rateLimiter.tryAcquire("demo.kill-switch")).thenReturn(true);
      com.tickonomics.persistence.entity.VirtualPortfolioPosition open =
          new com.tickonomics.persistence.entity.VirtualPortfolioPosition(
              1L, Instant.now(), "SPY", "BUY",
              new BigDecimal("10.0"), new BigDecimal("500.0"),
              null, null, new BigDecimal("475.0"), new BigDecimal("550.0"), null, null);
      VirtualPortfolioTrade closed = new VirtualPortfolioTrade(
          1L, Instant.now(), "SPY", "SELL", new BigDecimal("10.0"), new BigDecimal("510.0"),
          BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.0"), 1L, null, "PAPER");
      when(portfolio.findOpenPositions()).thenReturn(List.of(open));
      when(portfolio.closePosition(eq(1L), eq(new BigDecimal("510.0")))).thenReturn(closed);

      ResponseEntity<KillSwitchResponse> response =
          controller.activateKillSwitch(Map.of("SPY", new BigDecimal("510.0")));

      assertTrue(killSwitch.isActive());
      assertTrue(response.getBody().active());
      assertEquals(1, response.getBody().liquidatedTrades());
    }

    @Test
    void givenActiveKillSwitch_whenDeactivate_thenInactive() {
      killSwitch.activate();

      controller.deactivateKillSwitch();

      assertFalse(killSwitch.isActive());
    }

    @Test
    void givenKillSwitchState_whenStatus_thenReflectsState() {
      ResponseEntity<KillSwitchResponse> response = controller.killSwitchStatus();

      assertFalse(response.getBody().active());
    }
  }

  @Nested
  class SafeModeEndpoints {

    @Test
    void givenStatusReport_whenSafeModeStatus_thenReportFieldsReturned() {
      SystemicResilienceMonitor.StatusReport report = new SystemicResilienceMonitor.StatusReport(
          true, true, false, true, "overflow_utilization_exceeded + analytics_worker_degraded",
          Instant.parse("2026-06-13T10:00:00Z"),
          List.of("overflow_utilization_exceeded", "analytics_worker_degraded"), false);
      when(resilienceMonitor.status()).thenReturn(report);

      ResponseEntity<SafeModeStatusResponse> response = controller.safeModeStatus();

      assertEquals(200, response.getStatusCode().value());
      SafeModeStatusResponse body = response.getBody();
      assertTrue(body.active());
      assertTrue(body.autoActivated());
      assertFalse(body.manualOverride());
      assertTrue(body.enabled());
      assertEquals("overflow_utilization_exceeded + analytics_worker_degraded", body.lastReason());
      assertEquals(2, body.degradedIndicators().size());
      assertFalse(body.recoveryReady());
    }

    @Test
    void givenActivateSafeMode_whenCalled_thenDelegatesAndReturnsActive() {
      ResponseEntity<SafeModeToggleResponse> response = controller.activateSafeMode();

      verify(resilienceMonitor).activateManual();
      assertTrue(response.getBody().active());
      assertEquals("manual_override", response.getBody().source());
    }

    @Test
    void givenDeactivateSafeMode_whenCalled_thenDelegatesAndReturnsInactive() {
      ResponseEntity<SafeModeToggleResponse> response = controller.deactivateSafeMode();

      verify(resilienceMonitor).deactivateManual();
      assertFalse(response.getBody().active());
      assertEquals("manual_ack", response.getBody().source());
    }
  }

  @Nested
  class LeverageRotation {

    @Test
    void givenRotationOutcome_whenEvaluate_thenSignalReturned() {
      LeverageSignaler.LeverageSignal signal = new LeverageSignaler.LeverageSignal(
          LeverageSignaler.Signal.LEVERAGE_OFF, 90.0, 100.0, -0.10, Instant.now());
      when(portfolio.applyLeverageRotation(eq(priceLookup), eq(leverageSignaler), any()))
          .thenReturn(new VirtualPortfolio.LeverageRotationOutcome(signal, List.of()));

      ResponseEntity<LeverageRotationResponse> response =
          controller.evaluateLeverageRotation(Map.of("SPY", new BigDecimal("90.0")));

      assertEquals("LEVERAGE_OFF", response.getBody().signal());
      assertEquals(90.0, response.getBody().benchmarkPrice(), 0.001);
      assertEquals(0, response.getBody().closedTrades());
      verify(portfolio).applyLeverageRotation(eq(priceLookup), eq(leverageSignaler), any());
    }
  }
}
