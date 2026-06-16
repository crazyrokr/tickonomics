package com.tickonomics.computation.demo;

import com.tickonomics.computation.backtest.Eq553SlippageModel;
import com.tickonomics.computation.execution.ComparativeExecutionAnalysis;
import com.tickonomics.computation.execution.ComparativeExecutionAnalysis.ExecutionSlippage;
import com.tickonomics.computation.kpi.IliResult;
import com.tickonomics.computation.kpi.SignalResult;
import com.tickonomics.computation.portfolio.PortfolioManagementAlgebra;
import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Bridges {@code SignalGenerator} output to the {@link VirtualPortfolio}, applying the v1 data-gate
 * chain (demo-enabled, ILI dislocated/degraded, ACTIONABLE-only) and the v5 execution-model
 * guardrails: kill-switch, global safe mode, market-stability circuit breaker, pre-trade systemic-impact check,
 * market-maker / fill-probability execution, dynamic Markov stops, standardized cost model,
 * randomized execution window, and dual passive-vs-sniper execution sampling for the comparative
 * analysis surfaced in the signal-quality report.
 */
@Service
public class PaperTradingEngine {

  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
  private static final BigDecimal BPS_DIVISOR = new BigDecimal("10000");

  private final VirtualPortfolio portfolio;
  private final Eq553SlippageModel slippageModel;
  private final DemoConfig config;
  private final KillSwitch killSwitch;
  private final SystemicResilienceMonitor resilienceMonitor;
  private final MarketStabilityGuard marketStabilityGuard;
  private final OrderImpactPredictor orderImpactPredictor;
  private final MarkovStopHandler markovStopHandler;
  private final PortfolioManagementAlgebra portfolioAlgebra;
  private final MarketMakerExecutionModel marketMakerModel;
  private final FillProbabilityEngine fillProbabilityEngine;
  private final PassiveExecutionHandler passiveHandler;
  private final SniperExecutionHandler sniperHandler;
  private final ComparativeExecutionAnalysis comparativeExecutionAnalysis;
  private final RandomizedExecutionWindow randomizedExecutionWindow;

  private final List<ExecutionSlippage> passiveSamples = new ArrayList<>();
  private final List<ExecutionSlippage> aggressiveSamples = new ArrayList<>();

  public PaperTradingEngine(VirtualPortfolio portfolio,
      Eq553SlippageModel slippageModel,
      DemoConfig config,
      KillSwitch killSwitch,
      SystemicResilienceMonitor resilienceMonitor,
      MarketStabilityGuard marketStabilityGuard,
      OrderImpactPredictor orderImpactPredictor,
      MarkovStopHandler markovStopHandler,
      PortfolioManagementAlgebra portfolioAlgebra,
      MarketMakerExecutionModel marketMakerModel,
      FillProbabilityEngine fillProbabilityEngine,
      PassiveExecutionHandler passiveHandler,
      SniperExecutionHandler sniperHandler,
      ComparativeExecutionAnalysis comparativeExecutionAnalysis,
      RandomizedExecutionWindow randomizedExecutionWindow) {
    this.portfolio = portfolio;
    this.slippageModel = slippageModel;
    this.config = config;
    this.killSwitch = killSwitch;
    this.resilienceMonitor = resilienceMonitor;
    this.marketStabilityGuard = marketStabilityGuard;
    this.orderImpactPredictor = orderImpactPredictor;
    this.markovStopHandler = markovStopHandler;
    this.portfolioAlgebra = portfolioAlgebra;
    this.marketMakerModel = marketMakerModel;
    this.fillProbabilityEngine = fillProbabilityEngine;
    this.passiveHandler = passiveHandler;
    this.sniperHandler = sniperHandler;
    this.comparativeExecutionAnalysis = comparativeExecutionAnalysis;
    this.randomizedExecutionWindow = randomizedExecutionWindow;
  }

  public TradeResult processSignal(SignalResult signal, IliResult iliResult, BigDecimal price) {
    return processSignal(signal, iliResult, price, ExecutionContext.empty());
  }

  public TradeResult processSignal(SignalResult signal, IliResult iliResult, BigDecimal price,
      ExecutionContext ctx) {
    if (!config.enabled() || !config.autoExecuteSignals()) {
      return TradeResult.skipped("demo_disabled");
    }
    if (killSwitch.isActive()) {
      return TradeResult.skipped("kill_switch_active");
    }
    if (resilienceMonitor.isSafeModeActive()) {
      return TradeResult.skipped("global_safe_mode");
    }
    if (config.marketStabilityGuard().enabled()
        && ctx.recentPortfolioReturns() != null && !ctx.recentPortfolioReturns().isEmpty()
        && !marketStabilityGuard.isStable(
            ctx.recentPortfolioReturns(), config.marketStabilityGuard().volatilityThresholdPct())) {
      return TradeResult.skipped("market_unstable");
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

    BigDecimal orderNotional = config.virtualBalance()
        .multiply(BigDecimal.valueOf(config.positionSizePct()))
        .divide(ONE_HUNDRED, MathContext.DECIMAL64);
    if (config.orderImpactPredictor().enabled()) {
      double liquidityNotional = ctx.liquidityNotional().compareTo(BigDecimal.ZERO) > 0
          ? ctx.liquidityNotional().doubleValue() : config.defaultAddv();
      if (!orderImpactPredictor.isAcceptable(
          orderNotional.doubleValue(), liquidityNotional,
          config.orderImpactPredictor().maxImpactThresholdBps())) {
        return TradeResult.skipped("order_impact_excessive");
      }
    }

    double rawSlippageBps = slippageModel.calculateSlippageBps(
        0.20, config.defaultAddv(), orderNotional.doubleValue());
    double slippageMultiplier = fillProbabilityEngine.slippageMultiplier(ctx.pli().doubleValue());
    double effectiveSlippageBps = rawSlippageBps * slippageMultiplier;

    BigDecimal fillPrice;
    double spreadCaptureBps = 0.0;
    String strategy = "MARKET";
    if (config.marketMakerMode().enabled()) {
      MarketMakerExecutionModel.ExecutionDecision decision =
          marketMakerModel.decide(ctx.bid().doubleValue(), ctx.ask().doubleValue(),
              price.doubleValue(), config.marketMakerMode().preferLimitOrders());
      strategy = decision.strategy().name();
      if (decision.strategy() == MarketMakerExecutionModel.Strategy.LIMIT) {
        fillPrice = BigDecimal.valueOf(decision.expectedFillPrice());
        spreadCaptureBps = decision.spreadCaptureBps();
      } else {
        fillPrice = applySlippage(price, signal.direction(), effectiveSlippageBps);
      }
    } else {
      fillPrice = applySlippage(price, signal.direction(), effectiveSlippageBps);
    }

    double stopLossPct = config.stopLossPct();
    double takeProfitPct = config.takeProfitPct();
    if (config.dynamicStops().enabled()) {
      Optional<MarkovStopHandler.CalibratedStops> stops = markovStopHandler.stopsFor(signal.symbol());
      if (stops.isPresent()) {
        stopLossPct = stops.get().stopLossPct();
        takeProfitPct = stops.get().takeProfitPct();
      }
    }

    BigDecimal commission = BigDecimal.ZERO;
    if (config.portfolioAlgebra().useStandardizedCostModel()) {
      DemoConfig.AdvancedCostModel cost = config.advancedCostModel();
      commission = portfolioAlgebra.computeCosts(
          fillPrice.doubleValue() > 0
              ? orderNotional.divide(fillPrice, 0, java.math.RoundingMode.CEILING).longValue()
              : 0,
          fillPrice,
          BigDecimal.valueOf(cost.passiveBps()).divide(BPS_DIVISOR, MathContext.DECIMAL64),
          BigDecimal.valueOf(cost.aggressiveBps()).divide(BPS_DIVISOR, MathContext.DECIMAL64),
          BigDecimal.valueOf(effectiveSlippageBps)).totalCost();
    }

    VirtualPortfolioPosition position = portfolio.openPosition(
        signal, fillPrice, stopLossPct, takeProfitPct, commission);

    recordExecutionSamples(signal, price.doubleValue(), effectiveSlippageBps);

    long executionDelaySeconds = config.randomizedExecution().enabled()
        ? randomizedExecutionWindow.delaySeconds(
            config.randomizedExecution().windowSeconds(),
            config.randomizedExecution().avoidRoundMarks())
        : 0L;

    return TradeResult.opened(position.id(), signal.symbol(), signal.direction(),
        position.quantity(), fillPrice, effectiveSlippageBps, commission, strategy,
        executionDelaySeconds, spreadCaptureBps);
  }

  private BigDecimal applySlippage(BigDecimal price, String direction, double slippageBps) {
    BigDecimal factor = BigDecimal.valueOf(slippageBps).divide(BPS_DIVISOR, MathContext.DECIMAL64);
    return SignalResult.DIR_SELL.equals(direction)
        ? price.multiply(BigDecimal.ONE.subtract(factor))
        : price.multiply(BigDecimal.ONE.add(factor));
  }

  private void recordExecutionSamples(SignalResult signal, double referencePrice,
      double effectiveSlippageBps) {
    FillEstimate passive = passiveHandler.fill(signal, referencePrice);
    passiveSamples.add(new ExecutionSlippage(passive.type(), passive.slippageBps(), 1));
    FillEstimate sniper = sniperHandler.fill(signal, referencePrice, effectiveSlippageBps);
    aggressiveSamples.add(new ExecutionSlippage(sniper.type(), sniper.slippageBps(), 1));
  }

  public ComparativeExecutionAnalysis.ExecutionComparison executionComparison() {
    return comparativeExecutionAnalysis.compare(passiveSamples, aggressiveSamples);
  }

  public void resetExecutionSamples() {
    passiveSamples.clear();
    aggressiveSamples.clear();
  }

  public List<VirtualPortfolioTrade> evaluateExits(Map<String, BigDecimal> currentPrices) {
    portfolio.markToMarket(currentPrices);
    List<VirtualPortfolioPosition> breached = portfolio.checkStopLossTakeProfit(currentPrices);

    return breached.stream()
        .map(pos -> {
          BigDecimal price = currentPrices.get(pos.symbol());
          return portfolio.closePosition(pos.id(), price);
        })
        .toList();
  }

  public record ExecutionContext(
      List<Double> recentPortfolioReturns,
      BigDecimal pli,
      BigDecimal bid,
      BigDecimal ask,
      BigDecimal liquidityNotional) {

    public static ExecutionContext empty() {
      return new ExecutionContext(List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO);
    }
  }

  public record TradeResult(
      String action,
      Long positionId,
      String symbol,
      String direction,
      BigDecimal quantity,
      BigDecimal fillPrice,
      double slippageBps,
      BigDecimal commission,
      String strategy,
      long executionDelaySeconds,
      double spreadCaptureBps,
      String reason) {

    static TradeResult skipped(String reason) {
      return new TradeResult("SKIPPED", null, null, null, BigDecimal.ZERO,
          BigDecimal.ZERO, 0, BigDecimal.ZERO, null, 0L, 0.0, reason);
    }

    static TradeResult opened(long positionId, String symbol, String direction,
        BigDecimal quantity, BigDecimal fillPrice, double slippageBps, BigDecimal commission,
        String strategy, long executionDelaySeconds, double spreadCaptureBps) {
      return new TradeResult("OPENED", positionId, symbol, direction,
          quantity, fillPrice, slippageBps, commission, strategy,
          executionDelaySeconds, spreadCaptureBps, null);
    }
  }
}
