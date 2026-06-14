package com.tickonomics.computation.demo;

import com.tickonomics.computation.kpi.SignalResult;
import com.tickonomics.computation.leverage.LeverageSignaler;
import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import com.tickonomics.persistence.repository.VirtualPortfolioPositionRepository;
import com.tickonomics.persistence.repository.VirtualPortfolioTradeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class VirtualPortfolio {

  private final VirtualPortfolioPositionRepository positionRepository;
  private final VirtualPortfolioTradeRepository tradeRepository;
  private final DemoConfig config;

  public VirtualPortfolio(
      VirtualPortfolioPositionRepository positionRepository,
      VirtualPortfolioTradeRepository tradeRepository,
      DemoConfig config) {
    this.positionRepository = positionRepository;
    this.tradeRepository = tradeRepository;
    this.config = config;
  }

  public VirtualPortfolioPosition findOpenBySymbol(String symbol) {
    Optional<VirtualPortfolioPosition> found = positionRepository.findOpenBySymbol(symbol);
    return found.orElse(null);
  }

  public List<VirtualPortfolioPosition> findOpenPositions() {
    return positionRepository.findOpenPositions();
  }

  public VirtualPortfolioPosition openPosition(SignalResult signal, double fillPrice) {
    return openPosition(signal, fillPrice, config.stopLossPct(), config.takeProfitPct(), 0.0);
  }

  public VirtualPortfolioPosition openPosition(SignalResult signal, double fillPrice,
      double stopLossPct, double takeProfitPct, double commission) {
    double positionSize = config.virtualBalance() * config.positionSizePct() / 100.0;
    double quantity = positionSize / fillPrice;
    double stopLossPrice = fillPrice * (1 - stopLossPct / 100.0);
    double takeProfitPrice = fillPrice * (1 + takeProfitPct / 100.0);

    VirtualPortfolioPosition position = new VirtualPortfolioPosition(
        null, Instant.now(), signal.symbol(), signal.direction(), quantity, fillPrice,
        null, null, stopLossPrice, takeProfitPrice, null, null);
    long positionId = positionRepository.save(position);

    VirtualPortfolioTrade openTrade = new VirtualPortfolioTrade(
        null, Instant.now(), signal.symbol(), signal.direction(), quantity, fillPrice,
        commission, 0.0, null, positionId, null, "PAPER");
    tradeRepository.save(openTrade);

    return new VirtualPortfolioPosition(
        positionId, position.openedAt(), position.symbol(), position.direction(),
        position.quantity(), position.entryPrice(), position.currentPrice(),
        position.unrealizedPnl(), position.stopLossPrice(), position.takeProfitPrice(),
        position.signalId(), position.closedAt());
  }

  public VirtualPortfolioTrade closePosition(long positionId, double exitPrice) {
    VirtualPortfolioPosition position = positionRepository.findOpenPositions().stream()
        .filter(p -> p.id().longValue() == positionId)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No open position with id: " + positionId));

    double realizedPnl = computeRealizedPnl(position, exitPrice);
    String closeDirection = SignalResult.DIR_BUY.equals(position.direction()) ? "SELL" : "BUY";

    VirtualPortfolioTrade closeTrade = new VirtualPortfolioTrade(
        null, Instant.now(), position.symbol(), closeDirection, position.quantity(),
        exitPrice, 0.0, 0.0, realizedPnl, positionId, null, "PAPER");
    tradeRepository.save(closeTrade);

    positionRepository.close(positionId, Instant.now(), exitPrice);
    return closeTrade;
  }

  public void markToMarket(Map<String, Double> currentPrices) {
    List<VirtualPortfolioPosition> openPositions = positionRepository.findOpenPositions();
    for (VirtualPortfolioPosition position : openPositions) {
      Double price = currentPrices.get(position.symbol());
      if (price != null) {
        double unrealizedPnl = computeRealizedPnl(position, price);
        positionRepository.updateMarkToMarket(position.id(), price, unrealizedPnl);
      }
    }
  }

  public List<VirtualPortfolioPosition> checkStopLossTakeProfit(Map<String, Double> currentPrices) {
    List<VirtualPortfolioPosition> openPositions = positionRepository.findOpenPositions();
    List<VirtualPortfolioPosition> breached = new ArrayList<>();
    for (VirtualPortfolioPosition position : openPositions) {
      Double price = currentPrices.get(position.symbol());
      if (price == null) {
        continue;
      }
      boolean stopBreach = position.stopLossPrice() != null && price <= position.stopLossPrice();
      boolean targetBreach = position.takeProfitPrice() != null && price >= position.takeProfitPrice();

      if (SignalResult.DIR_SELL.equals(position.direction())) {
        stopBreach = position.stopLossPrice() != null && price >= position.stopLossPrice();
        targetBreach = position.takeProfitPrice() != null && price <= position.takeProfitPrice();
      }

      if (stopBreach || targetBreach) {
        breached.add(position);
      }
    }
    return breached;
  }

  /**
   * Apply {@link LeverageSignaler} rotation for the configured benchmark symbol: on
   * {@code LEVERAGE_OFF} flatten every open position at its current price (rotate into cash /
   * Treasuries), truncating tail risk; on {@code LEVERAGE_ON} take no action.
   */
  public LeverageRotationOutcome applyLeverageRotation(MarketPriceLookup priceLookup,
      LeverageSignaler leverageSignaler, Map<String, Double> currentPrices) {
    DemoConfig.LeverageRotation rotation = config.leverageRotation();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    List<Double> history = priceLookup.closingPrices(
        rotation.benchmarkSymbol(), today.minusDays(rotation.maWindowDays()), today);

    Double live = currentPrices != null ? currentPrices.get(rotation.benchmarkSymbol()) : null;
    double currentPrice = live != null ? live : (history.isEmpty() ? 0.0 : history.getLast());

    LeverageSignaler.LeverageSignal signal = leverageSignaler.evaluate(history, currentPrice);

    if (signal.signal() != LeverageSignaler.Signal.LEVERAGE_OFF || currentPrices == null) {
      return new LeverageRotationOutcome(signal, List.of());
    }
    List<VirtualPortfolioTrade> closed = positionRepository.findOpenPositions().stream()
        .filter(pos -> currentPrices.containsKey(pos.symbol()))
        .map(pos -> closePosition(pos.id(), currentPrices.get(pos.symbol())))
        .toList();
    return new LeverageRotationOutcome(signal, closed);
  }

  public PortfolioSummary getPortfolioSummary() {
    List<VirtualPortfolioPosition> openPositions = positionRepository.findOpenPositions();
    int totalTrades = tradeRepository.countByTradeType("PAPER");

    double unrealizedPnl = openPositions.stream()
        .mapToDouble(p -> p.unrealizedPnl() != null ? p.unrealizedPnl() : 0.0)
        .sum();

    double realizedPnl = 0.0;
    int wins = 0;
    List<VirtualPortfolioTrade> trades = tradeRepository.findLatest(totalTrades, 0);
    for (VirtualPortfolioTrade trade : trades) {
      if (trade.realizedPnl() != null) {
        realizedPnl += trade.realizedPnl();
        if (trade.realizedPnl() > 0) {
          wins++;
        }
      }
    }

    int closingTrades = (int) trades.stream()
        .filter(t -> t.realizedPnl() != null)
        .count();
    double winRate = closingTrades > 0 ? (double) wins / closingTrades : 0.0;

    return new PortfolioSummary(
        config.virtualBalance(),
        config.virtualBalance() + realizedPnl + unrealizedPnl,
        realizedPnl,
        unrealizedPnl,
        openPositions.size(),
        totalTrades,
        winRate,
        config.enabled());
  }

  private double computeRealizedPnl(VirtualPortfolioPosition position, double exitPrice) {
    if (SignalResult.DIR_BUY.equals(position.direction())) {
      return (exitPrice - position.entryPrice()) * position.quantity();
    }
    return (position.entryPrice() - exitPrice) * position.quantity();
  }

  public record PortfolioSummary(
      double initialBalance,
      double currentBalance,
      double realizedPnl,
      double unrealizedPnl,
      int openPositions,
      int totalTrades,
      double winRate,
      boolean enabled) {}

  public record LeverageRotationOutcome(
      LeverageSignaler.LeverageSignal signal,
      List<VirtualPortfolioTrade> closedTrades) {}
}
