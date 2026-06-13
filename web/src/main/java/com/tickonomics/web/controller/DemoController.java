package com.tickonomics.web.controller;

import com.tickonomics.computation.demo.KillSwitch;
import com.tickonomics.computation.demo.MarketPriceLookup;
import com.tickonomics.computation.demo.PaperTradingEngine;
import com.tickonomics.computation.demo.SignalQualityAnalyzer;
import com.tickonomics.computation.demo.SystemicResilienceMonitor;
import com.tickonomics.computation.demo.VirtualPortfolio;
import com.tickonomics.computation.leverage.LeverageSignaler;
import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import com.tickonomics.persistence.repository.SignalLogRepository;
import com.tickonomics.persistence.repository.VirtualPortfolioTradeRepository;
import java.util.LinkedHashMap;
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
  private final PaperTradingEngine tradingEngine;
  private final SignalQualityAnalyzer qualityAnalyzer;
  private final VirtualPortfolioTradeRepository tradeRepository;
  private final SignalLogRepository signalLogRepository;
  private final KillSwitch killSwitch;
  private final MarketPriceLookup priceLookup;
  private final LeverageSignaler leverageSignaler;
  private final SystemicResilienceMonitor resilienceMonitor;

  public DemoController(
      VirtualPortfolio portfolio,
      PaperTradingEngine tradingEngine,
      SignalQualityAnalyzer qualityAnalyzer,
      VirtualPortfolioTradeRepository tradeRepository,
      SignalLogRepository signalLogRepository,
      KillSwitch killSwitch,
      MarketPriceLookup priceLookup,
      LeverageSignaler leverageSignaler,
      SystemicResilienceMonitor resilienceMonitor) {
    this.portfolio = portfolio;
    this.tradingEngine = tradingEngine;
    this.qualityAnalyzer = qualityAnalyzer;
    this.tradeRepository = tradeRepository;
    this.signalLogRepository = signalLogRepository;
    this.killSwitch = killSwitch;
    this.priceLookup = priceLookup;
    this.leverageSignaler = leverageSignaler;
    this.resilienceMonitor = resilienceMonitor;
  }

  @GetMapping("/portfolio")
  public ResponseEntity<Map<String, Object>> getPortfolioSummary() {
    VirtualPortfolio.PortfolioSummary summary = portfolio.getPortfolioSummary();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("balance", summary.currentBalance());
    body.put("initialBalance", summary.initialBalance());
    body.put("realizedPnl", summary.realizedPnl());
    body.put("unrealizedPnl", summary.unrealizedPnl());
    body.put("totalPnl", summary.realizedPnl() + summary.unrealizedPnl());
    body.put("openPositions", summary.openPositions());
    body.put("totalTrades", summary.totalTrades());
    body.put("winRate", summary.winRate());
    body.put("enabled", summary.enabled());
    return ResponseEntity.ok(body);
  }

  @GetMapping("/positions")
  public ResponseEntity<List<Map<String, Object>>> getOpenPositions() {
    List<VirtualPortfolioPosition> positions = portfolio.findOpenPositions();
    List<Map<String, Object>> result = positions.stream().map(pos -> {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", pos.id());
      map.put("symbol", pos.symbol());
      map.put("direction", pos.direction());
      map.put("quantity", pos.quantity());
      map.put("entryPrice", pos.entryPrice());
      map.put("currentPrice", pos.currentPrice());
      map.put("unrealizedPnl", pos.unrealizedPnl());
      map.put("stopLossPrice", pos.stopLossPrice());
      map.put("takeProfitPrice", pos.takeProfitPrice());
      map.put("openedAt", pos.openedAt());
      return map;
    }).toList();
    return ResponseEntity.ok(result);
  }

  @GetMapping("/trades")
  public ResponseEntity<List<Map<String, Object>>> getTrades(
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    List<VirtualPortfolioTrade> trades = tradeRepository.findLatest(limit, offset);
    List<Map<String, Object>> result = trades.stream().map(trade -> {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", trade.id());
      map.put("symbol", trade.symbol());
      map.put("direction", trade.direction());
      map.put("quantity", trade.quantity());
      map.put("fillPrice", trade.fillPrice());
      map.put("commission", trade.commission());
      map.put("slippage", trade.slippage());
      map.put("realizedPnl", trade.realizedPnl());
      map.put("executedAt", trade.executedAt());
      map.put("tradeType", trade.tradeType());
      return map;
    }).toList();
    return ResponseEntity.ok(result);
  }

  @GetMapping("/signal-quality")
  public ResponseEntity<Map<String, Object>> getSignalQuality() {
    return qualityAnalyzer.findLatestReport()
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.ok(Map.of()));
  }

  @PostMapping("/close-position/{positionId}")
  public ResponseEntity<Map<String, Object>> closePosition(
      @PathVariable long positionId,
      @RequestParam double price) {
    VirtualPortfolioTrade trade = portfolio.closePosition(positionId, price);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tradeId", trade.id());
    body.put("positionId", positionId);
    body.put("realizedPnl", trade.realizedPnl());
    body.put("fillPrice", trade.fillPrice());
    return ResponseEntity.ok(body);
  }

  @PostMapping("/kill-switch/activate")
  public ResponseEntity<Map<String, Object>> activateKillSwitch(
      @RequestBody(required = false) Map<String, Double> prices) {
    killSwitch.activate();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("active", true);
    if (prices != null && !prices.isEmpty()) {
      List<VirtualPortfolioTrade> liquidated = killSwitch.liquidateAll(portfolio, prices);
      body.put("liquidatedTrades", liquidated.size());
    }
    return ResponseEntity.ok(body);
  }

  @PostMapping("/kill-switch/deactivate")
  public ResponseEntity<Map<String, Object>> deactivateKillSwitch() {
    killSwitch.deactivate();
    return ResponseEntity.ok(Map.of("active", false));
  }

  @GetMapping("/kill-switch/status")
  public ResponseEntity<Map<String, Object>> killSwitchStatus() {
    return ResponseEntity.ok(Map.of("active", killSwitch.isActive()));
  }

  @GetMapping("/safe-mode/status")
  public ResponseEntity<Map<String, Object>> safeModeStatus() {
    SystemicResilienceMonitor.StatusReport report = resilienceMonitor.status();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("active", report.active());
    body.put("autoActivated", report.autoActivated());
    body.put("manualOverride", report.manualOverride());
    body.put("enabled", report.enabled());
    body.put("lastReason", report.lastReason());
    body.put("lastActivationAt", report.lastActivationAt());
    body.put("degradedIndicators", report.degradedIndicators());
    body.put("recoveryReady", report.recoveryReady());
    return ResponseEntity.ok(body);
  }

  @PostMapping("/safe-mode/activate")
  public ResponseEntity<Map<String, Object>> activateSafeMode() {
    resilienceMonitor.activateManual();
    return ResponseEntity.ok(Map.of("active", true, "source", "manual_override"));
  }

  @PostMapping("/safe-mode/deactivate")
  public ResponseEntity<Map<String, Object>> deactivateSafeMode() {
    resilienceMonitor.deactivateManual();
    return ResponseEntity.ok(Map.of("active", false, "source", "manual_ack"));
  }

  @PostMapping("/leverage-rotation/evaluate")
  public ResponseEntity<Map<String, Object>> evaluateLeverageRotation(
      @RequestBody(required = false) Map<String, Double> prices) {
    VirtualPortfolio.LeverageRotationOutcome outcome = portfolio.applyLeverageRotation(
        priceLookup, leverageSignaler, prices);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("signal", outcome.signal().signal().name());
    body.put("benchmarkPrice", outcome.signal().currentPrice());
    body.put("sma200", outcome.signal().sma200());
    body.put("deviation", outcome.signal().deviation());
    body.put("closedTrades", outcome.closedTrades().size());
    return ResponseEntity.ok(body);
  }
}
