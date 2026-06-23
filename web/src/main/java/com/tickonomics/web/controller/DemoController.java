package com.tickonomics.web.controller;

import com.tickonomics.computation.demo.KillSwitch;
import com.tickonomics.computation.demo.MarketPriceLookup;
import com.tickonomics.computation.demo.SignalQualityAnalyzer;
import com.tickonomics.computation.demo.SystemicResilienceMonitor;
import com.tickonomics.computation.demo.VirtualPortfolio;
import com.tickonomics.computation.leverage.LeverageSignaler;
import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import com.tickonomics.persistence.repository.VirtualPortfolioTradeRepository;
import com.tickonomics.web.config.AuthenticatedWrite;
import com.tickonomics.web.controller.dto.DemoCloseResultResponse;
import com.tickonomics.web.controller.dto.DemoPortfolioResponse;
import com.tickonomics.web.controller.dto.DemoPositionResponse;
import com.tickonomics.web.controller.dto.DemoTradeResponse;
import com.tickonomics.web.controller.dto.KillSwitchResponse;
import com.tickonomics.web.controller.dto.LeverageRotationResponse;
import com.tickonomics.web.controller.dto.SafeModeStatusResponse;
import com.tickonomics.web.controller.dto.SafeModeToggleResponse;
import com.tickonomics.web.exception.RateLimitExceededException;
import com.tickonomics.web.security.RateLimitGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

  private final VirtualPortfolio portfolio;
  private final SignalQualityAnalyzer qualityAnalyzer;
  private final VirtualPortfolioTradeRepository tradeRepository;
  private final KillSwitch killSwitch;
  private final MarketPriceLookup priceLookup;
  private final LeverageSignaler leverageSignaler;
  private final SystemicResilienceMonitor resilienceMonitor;
  private final RateLimitGuard rateLimiter;

  public DemoController(
      VirtualPortfolio portfolio,
      SignalQualityAnalyzer qualityAnalyzer,
      VirtualPortfolioTradeRepository tradeRepository,
      KillSwitch killSwitch,
      MarketPriceLookup priceLookup,
      LeverageSignaler leverageSignaler,
      SystemicResilienceMonitor resilienceMonitor,
      RateLimitGuard rateLimiter) {
    this.portfolio = portfolio;
    this.qualityAnalyzer = qualityAnalyzer;
    this.tradeRepository = tradeRepository;
    this.killSwitch = killSwitch;
    this.priceLookup = priceLookup;
    this.leverageSignaler = leverageSignaler;
    this.resilienceMonitor = resilienceMonitor;
    this.rateLimiter = rateLimiter;
  }

  @GetMapping("/portfolio")
  public ResponseEntity<DemoPortfolioResponse> getPortfolioSummary() {
    VirtualPortfolio.PortfolioSummary summary = portfolio.getPortfolioSummary();
    return ResponseEntity.ok(new DemoPortfolioResponse(
        summary.currentBalance(),
        summary.initialBalance(),
        summary.realizedPnl(),
        summary.unrealizedPnl(),
        summary.realizedPnl().add(summary.unrealizedPnl()),
        summary.openPositions(),
        summary.totalTrades(),
        summary.winRate(),
        summary.enabled()));
  }

  @GetMapping("/positions")
  public ResponseEntity<List<DemoPositionResponse>> getOpenPositions() {
    List<DemoPositionResponse> result = portfolio.findOpenPositions().stream()
        .map(pos -> new DemoPositionResponse(
            pos.id(),
            pos.symbol(),
            pos.direction(),
            pos.quantity(),
            pos.entryPrice(),
            pos.currentPrice(),
            pos.unrealizedPnl(),
            pos.stopLossPrice(),
            pos.takeProfitPrice(),
            pos.openedAt()))
        .toList();
    return ResponseEntity.ok(result);
  }

  @GetMapping("/trades")
  public ResponseEntity<List<DemoTradeResponse>> getTrades(
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    List<DemoTradeResponse> result = tradeRepository.findLatest(limit, offset).stream()
        .map(trade -> new DemoTradeResponse(
            trade.id(),
            trade.symbol(),
            trade.direction(),
            trade.quantity(),
            trade.fillPrice(),
            trade.commission(),
            trade.slippage(),
            trade.realizedPnl(),
            trade.executedAt(),
            trade.tradeType()))
        .toList();
    return ResponseEntity.ok(result);
  }

  @GetMapping("/signal-quality")
  public ResponseEntity<Map<String, Object>> getSignalQuality() {
    // SignalQuality is a free-form report (contract schema: additionalProperties: true), so the
    // analyzer's native Map is the correct typed surface here.
    return qualityAnalyzer.findLatestReport()
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.ok(Map.of()));
  }

  @AuthenticatedWrite
  @PostMapping("/close-position/{positionId}")
  public ResponseEntity<DemoCloseResultResponse> closePosition(
      @PathVariable long positionId,
      @RequestParam BigDecimal price) {
    if (!rateLimiter.tryAcquire("demo.close-position")) {
      throw new RateLimitExceededException("close-position rate limit exceeded; retry later");
    }
    VirtualPortfolioTrade trade = portfolio.closePosition(positionId, price);
    return ResponseEntity.ok(new DemoCloseResultResponse(
        trade.id(), positionId, trade.realizedPnl(), trade.fillPrice()));
  }

  @AuthenticatedWrite
  @PostMapping("/kill-switch/activate")
  public ResponseEntity<KillSwitchResponse> activateKillSwitch(
      @RequestBody(required = false) Map<String, BigDecimal> prices) {
    if (!rateLimiter.tryAcquire("demo.kill-switch")) {
      throw new RateLimitExceededException("kill-switch rate limit exceeded; retry later");
    }
    killSwitch.activate();
    Integer liquidatedTrades = null;
    if (prices != null && !prices.isEmpty()) {
      liquidatedTrades = killSwitch.liquidateAll(portfolio, prices).size();
    }
    return ResponseEntity.ok(new KillSwitchResponse(true, liquidatedTrades));
  }

  @AuthenticatedWrite
  @PostMapping("/kill-switch/deactivate")
  public ResponseEntity<KillSwitchResponse> deactivateKillSwitch() {
    killSwitch.deactivate();
    return ResponseEntity.ok(new KillSwitchResponse(false, null));
  }

  @GetMapping("/kill-switch/status")
  public ResponseEntity<KillSwitchResponse> killSwitchStatus() {
    return ResponseEntity.ok(new KillSwitchResponse(killSwitch.isActive(), null));
  }

  @GetMapping("/safe-mode/status")
  public ResponseEntity<SafeModeStatusResponse> safeModeStatus() {
    SystemicResilienceMonitor.StatusReport report = resilienceMonitor.status();
    return ResponseEntity.ok(new SafeModeStatusResponse(
        report.active(),
        report.autoActivated(),
        report.manualOverride(),
        report.enabled(),
        report.lastReason(),
        report.lastActivationAt(),
        report.degradedIndicators(),
        report.recoveryReady()));
  }

  @AuthenticatedWrite
  @PostMapping("/safe-mode/activate")
  public ResponseEntity<SafeModeToggleResponse> activateSafeMode() {
    resilienceMonitor.activateManual();
    return ResponseEntity.ok(new SafeModeToggleResponse(true, "manual_override"));
  }

  @AuthenticatedWrite
  @PostMapping("/safe-mode/deactivate")
  public ResponseEntity<SafeModeToggleResponse> deactivateSafeMode() {
    resilienceMonitor.deactivateManual();
    return ResponseEntity.ok(new SafeModeToggleResponse(false, "manual_ack"));
  }

  @AuthenticatedWrite
  @PostMapping("/leverage-rotation/evaluate")
  public ResponseEntity<LeverageRotationResponse> evaluateLeverageRotation(
      @RequestBody(required = false) Map<String, BigDecimal> prices) {
    VirtualPortfolio.LeverageRotationOutcome outcome =
        portfolio.applyLeverageRotation(priceLookup, leverageSignaler, prices);
    return ResponseEntity.ok(new LeverageRotationResponse(
        outcome.signal().signal().name(),
        outcome.signal().currentPrice(),
        outcome.signal().sma200(),
        outcome.signal().deviation(),
        outcome.closedTrades().size()));
  }
}
