package com.tickonomics.computation.demo;

import com.tickonomics.computation.backtest.Eq553SlippageModel;
import com.tickonomics.computation.kpi.IliResult;
import com.tickonomics.computation.kpi.SignalResult;
import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaperTradingEngine {

  private final VirtualPortfolio portfolio;
  private final Eq553SlippageModel slippageModel;
  private final DemoConfig config;

  public PaperTradingEngine(VirtualPortfolio portfolio,
      Eq553SlippageModel slippageModel,
      DemoConfig config) {
    this.portfolio = portfolio;
    this.slippageModel = slippageModel;
    this.config = config;
  }

  public TradeResult processSignal(SignalResult signal, IliResult iliResult, double price) {
    if (!config.enabled() || !config.autoExecuteSignals()) {
      return TradeResult.skipped("demo_disabled");
    }

    if (IliResult.STATUS_DISLOCATED.equals(iliResult.dataStatus())
        && config.skipDislocatedSignals()) {
      return TradeResult.skipped("dislocated_data");
    }

    if (IliResult.STATUS_DEGRADED.equals(iliResult.dataStatus())
        && !config.executeDegradedSignals()) {
      return TradeResult.skipped("degraded_data");
    }

    if (!SignalResult.STATUS_ACTIONABLE.equals(signal.status())) {
      return TradeResult.skipped("non_actionable:" + signal.status());
    }

    VirtualPortfolioPosition existing = portfolio.findOpenBySymbol(signal.symbol());
    if (existing != null) {
      if (existing.direction().equals(signal.direction())) {
        return TradeResult.skipped("existing_same_direction");
      }
      portfolio.closePosition(existing.id(), price);
    }

    double slippageBps = slippageModel.calculateSlippageBps(
        0.20, config.defaultAddv(), config.virtualBalance() * config.positionSizePct() / 100.0);

    double fillPrice;
    if (SignalResult.DIR_BUY.equals(signal.direction())) {
      fillPrice = price * (1 + slippageBps / 10000.0);
    } else {
      fillPrice = price * (1 - slippageBps / 10000.0);
    }

    VirtualPortfolioPosition position = portfolio.openPosition(signal, fillPrice);
    return TradeResult.opened(position.id(), signal.symbol(), signal.direction(),
        position.quantity(), fillPrice, slippageBps);
  }

  public List<VirtualPortfolioTrade> evaluateExits(Map<String, Double> currentPrices) {
    portfolio.markToMarket(currentPrices);
    List<VirtualPortfolioPosition> breached = portfolio.checkStopLossTakeProfit(currentPrices);

    return breached.stream()
        .map(pos -> {
          Double price = currentPrices.get(pos.symbol());
          return portfolio.closePosition(pos.id(), price);
        })
        .toList();
  }

  public record TradeResult(
      String action,
      Long positionId,
      String symbol,
      String direction,
      double quantity,
      double fillPrice,
      double slippageBps,
      String reason) {

    static TradeResult skipped(String reason) {
      return new TradeResult("SKIPPED", null, null, null, 0, 0, 0, reason);
    }

    static TradeResult opened(long positionId, String symbol, String direction,
        double quantity, double fillPrice, double slippageBps) {
      return new TradeResult("OPENED", positionId, symbol, direction,
          quantity, fillPrice, slippageBps, null);
    }
  }
}
