package com.tickonomics.web.controller;

import com.tickonomics.computation.demo.PaperTradingEngine;
import com.tickonomics.computation.demo.SignalQualityAnalyzer;
import com.tickonomics.computation.demo.VirtualPortfolio;
import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import com.tickonomics.persistence.repository.SignalLogRepository;
import com.tickonomics.persistence.repository.VirtualPortfolioTradeRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

  public DemoController(
      VirtualPortfolio portfolio,
      PaperTradingEngine tradingEngine,
      SignalQualityAnalyzer qualityAnalyzer,
      VirtualPortfolioTradeRepository tradeRepository,
      SignalLogRepository signalLogRepository) {
    this.portfolio = portfolio;
    this.tradingEngine = tradingEngine;
    this.qualityAnalyzer = qualityAnalyzer;
    this.tradeRepository = tradeRepository;
    this.signalLogRepository = signalLogRepository;
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
}
