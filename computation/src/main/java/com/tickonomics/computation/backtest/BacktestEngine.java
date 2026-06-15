package com.tickonomics.computation.backtest;

import com.tickonomics.computation.equity.BaseEquityStrategy;
import com.tickonomics.computation.equity.EquityStrategyRegistry;
import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;
import com.tickonomics.persistence.entity.AlphaSignalRecord;
import com.tickonomics.persistence.entity.BacktestResultRecord;
import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.repository.AlphaSignalRepository;
import com.tickonomics.persistence.repository.BacktestResultRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BacktestEngine {

  private static final Logger log = LoggerFactory.getLogger(BacktestEngine.class);

  private final HistoricalDataReplay dataReplay;
  private final EquityStrategyRegistry equityRegistry;
  private final DelayDExecutor delayDExecutor;
  private final BacktestResultRepository backtestResultRepository;
  private final AlphaSignalRepository alphaSignalRepository;
  private final IndicatorComputer indicatorComputer;

  public BacktestEngine(
      HistoricalDataReplay dataReplay,
      EquityStrategyRegistry equityRegistry,
      DelayDExecutor delayDExecutor,
      BacktestResultRepository backtestResultRepository,
      AlphaSignalRepository alphaSignalRepository,
      IndicatorComputer indicatorComputer) {
    this.dataReplay = dataReplay;
    this.equityRegistry = equityRegistry;
    this.delayDExecutor = delayDExecutor;
    this.backtestResultRepository = backtestResultRepository;
    this.alphaSignalRepository = alphaSignalRepository;
    this.indicatorComputer = indicatorComputer;
  }

  public BacktestResult runEquityBacktest(
      String strategyName,
      String symbol,
      Instant from,
      Instant to,
      StrategyContext ctx) {

    BaseEquityStrategy strategy = equityRegistry.get(strategyName)
        .orElseThrow(() -> new IllegalArgumentException(
            "Unknown equity strategy: " + strategyName));

    List<TickData> ticks = dataReplay.replay(symbol, from, to);
    if (ticks.isEmpty()) {
      return new BacktestResult(strategyName, ExecutionDelay.DELAY_0,
          0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, List.of());
    }

    List<Double> signalReturns = new ArrayList<>();
    List<Double> executionPrices = new ArrayList<>();
    List<AlphaSignal> generatedSignals = new ArrayList<>();

    for (int i = 1; i < ticks.size(); i++) {
      Map<String, Double> input = indicatorComputer.compute(ticks, i);
      AlphaSignal signal = strategy.compute(input, ctx);
      generatedSignals.add(signal);
      double previousPrice = ticks.get(i - 1).price();
      double barReturn = previousPrice != 0.0
          ? (ticks.get(i).price() - previousPrice) / previousPrice
          : 0.0;
      signalReturns.add(barReturn);
      executionPrices.add(ticks.get(i).price());
    }

    if (signalReturns.isEmpty()) {
      return new BacktestResult(strategyName, ExecutionDelay.DELAY_0,
          0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, List.of());
    }

    long nonNeutralSignals = generatedSignals.stream()
        .filter(signal -> !"NEUTRAL".equals(signal.direction()))
        .count();
    if (nonNeutralSignals == 0L) {
      log.info("Backtest of {} produced only NEUTRAL signals; the strategy may require indicators "
          + "not provided by {}", strategyName, indicatorComputer.getClass().getSimpleName());
    }

    List<BacktestResult> delayResults = delayDExecutor.compareDelays(
        strategyName, signalReturns, executionPrices);

    persistSignals(generatedSignals, strategy.strategyId());
    return delayResults.getFirst();
  }

  public Map<String, List<BacktestResult>> runAllEquityStrategies(
      List<String> symbols,
      Instant from,
      Instant to,
      StrategyContext ctx) {

    Map<String, List<BacktestResult>> results = new LinkedHashMap<>();
    for (BaseEquityStrategy strategy : equityRegistry.all()) {
      List<BacktestResult> strategyResults = new ArrayList<>();
      for (String symbol : symbols) {
        BacktestResult result = runEquityBacktest(
            strategy.name(), symbol, from, to, ctx);
        strategyResults.add(result);
      }
      results.put(strategy.name(), strategyResults);
    }
    return results;
  }

  public long persistResult(
      BacktestResult result, String symbol, Instant from, Instant to) {

    BacktestResultRecord record = new BacktestResultRecord(
        null,
        Instant.now(),
        "{\"strategy\":\"" + result.strategyName() + "\"}",
        "[" + from + "," + to + "]",
        result.sharpeRatio(),
        result.maxDrawdown(),
        result.winRate(),
        null,
        null,
        null, null, null, null, null, null);

    return backtestResultRepository.save(record);
  }

  private void persistSignals(List<AlphaSignal> signals, UUID strategyId) {
    List<AlphaSignalRecord> records = signals.stream()
        .filter(s -> !"NEUTRAL".equals(s.direction()))
        .map(s -> new AlphaSignalRecord(
            s.timestamp(),
            s.strategyId(),
            s.symbol(),
            s.direction(),
            s.strength(),
            s.confidence(),
            null,
            s.metrics() != null ? s.metrics().toString() : null))
        .toList();

    if (!records.isEmpty()) {
      alphaSignalRepository.saveAll(records);
    }
  }
}
