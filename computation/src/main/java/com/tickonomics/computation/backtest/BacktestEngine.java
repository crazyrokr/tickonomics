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
import org.springframework.stereotype.Component;

@Component
public class BacktestEngine {

  private final HistoricalDataReplay dataReplay;
  private final EquityStrategyRegistry equityRegistry;
  private final DelayDExecutor delayDExecutor;
  private final BacktestResultRepository backtestResultRepository;
  private final AlphaSignalRepository alphaSignalRepository;

  public BacktestEngine(
      HistoricalDataReplay dataReplay,
      EquityStrategyRegistry equityRegistry,
      DelayDExecutor delayDExecutor,
      BacktestResultRepository backtestResultRepository,
      AlphaSignalRepository alphaSignalRepository) {
    this.dataReplay = dataReplay;
    this.equityRegistry = equityRegistry;
    this.delayDExecutor = delayDExecutor;
    this.backtestResultRepository = backtestResultRepository;
    this.alphaSignalRepository = alphaSignalRepository;
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

    List<Double> dailyReturns = dataReplay.computeDailyReturns(ticks);

    List<Double> signalReturns = new ArrayList<>();
    List<Double> executionPrices = new ArrayList<>();
    List<AlphaSignal> generatedSignals = new ArrayList<>();

    Map<String, Double> input = new LinkedHashMap<>();
    for (int i = 0; i < dailyReturns.size(); i++) {
      input.put("return_" + i, dailyReturns.get(i));
      AlphaSignal signal = strategy.compute(input, ctx);
      generatedSignals.add(signal);
      signalReturns.add(dailyReturns.get(i));
      executionPrices.add(dailyReturns.get(i));
    }

    if (signalReturns.isEmpty()) {
      return new BacktestResult(strategyName, ExecutionDelay.DELAY_0,
          0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, List.of());
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
