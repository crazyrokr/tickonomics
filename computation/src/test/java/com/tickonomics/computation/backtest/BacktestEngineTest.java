package com.tickonomics.computation.backtest;

import com.tickonomics.computation.equity.BaseEquityStrategy;
import com.tickonomics.computation.equity.EquityStrategyRegistry;
import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;
import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.repository.AlphaSignalRepository;
import com.tickonomics.persistence.repository.BacktestResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacktestEngineTest {

  @Mock
  private HistoricalDataReplay dataReplay;

  @Mock
  private EquityStrategyRegistry equityRegistry;

  @Mock
  private DelayDExecutor delayDExecutor;

  @Mock
  private BacktestResultRepository backtestResultRepository;

  @Mock
  private AlphaSignalRepository alphaSignalRepository;

  @Mock
  private BaseEquityStrategy strategy;

  private BacktestEngine engine;

  private static final Instant FROM = Instant.parse("2025-01-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2025-01-31T00:00:00Z");
  private static final StrategyContext CTX = new StrategyContext(1.0, 0.9);
  private static final UUID STRATEGY_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    engine = new BacktestEngine(dataReplay, equityRegistry, delayDExecutor,
        backtestResultRepository, alphaSignalRepository);
  }

  @Nested
  class RunEquityBacktest {

    @Test
    void givenValidData_whenRun_thenResultHasMetrics() {
      List<TickData> ticks = List.of(
          new TickData(Instant.parse("2025-01-01T10:00:00Z"), "SPY", 100.0, 1000, new int[]{}),
          new TickData(Instant.parse("2025-01-01T16:00:00Z"), "SPY", 102.0, 1500, new int[]{}),
          new TickData(Instant.parse("2025-01-02T10:00:00Z"), "SPY", 105.0, 1200, new int[]{}),
          new TickData(Instant.parse("2025-01-03T10:00:00Z"), "SPY", 103.0, 800, new int[]{}));
      List<Double> dailyReturns = List.of(0.0294, -0.0190);

      when(equityRegistry.get("RSI_OSCILLATOR")).thenReturn(Optional.of(strategy));
      when(dataReplay.replay("SPY", FROM, TO)).thenReturn(ticks);
      when(dataReplay.computeDailyReturns(ticks)).thenReturn(dailyReturns);
      when(strategy.strategyId()).thenReturn(STRATEGY_ID);
      when(strategy.compute(any(), any())).thenReturn(
          new AlphaSignal(STRATEGY_ID, "SPY", "LONG", 0.8, 0.9, Instant.now(), Map.of()));
      when(delayDExecutor.compareDelays(anyString(), any(), any()))
          .thenReturn(List.of(
              new BacktestResult("RSI_OSCILLATOR", ExecutionDelay.DELAY_0,
                  1.5, 0.05, 0.02, 0.6, 0.05, 0.045, 0.5, false, List.of()),
              new BacktestResult("RSI_OSCILLATOR", ExecutionDelay.DELAY_1,
                  1.2, 0.04, 0.03, 0.5, 0.04, 0.035, 0.6, false, List.of())));

      BacktestResult result = engine.runEquityBacktest("RSI_OSCILLATOR", "SPY", FROM, TO, CTX);

      assertEquals("RSI_OSCILLATOR", result.strategyName());
      assertEquals(1.5, result.sharpeRatio());
      assertEquals(0.05, result.totalReturn());
    }

    @Test
    void givenNoTicks_whenRun_thenZeroedResult() {
      when(equityRegistry.get("RSI_OSCILLATOR")).thenReturn(Optional.of(strategy));
      when(dataReplay.replay("SPY", FROM, TO)).thenReturn(List.of());

      BacktestResult result = engine.runEquityBacktest("RSI_OSCILLATOR", "SPY", FROM, TO, CTX);

      assertEquals(0.0, result.sharpeRatio());
      assertEquals(0.0, result.totalReturn());
      assertTrue(result.tradeLog().isEmpty());
    }

    @Test
    void givenUnknownStrategy_whenRun_thenThrows() {
      when(equityRegistry.get("NONEXISTENT")).thenReturn(Optional.empty());

      assertThrows(IllegalArgumentException.class,
          () -> engine.runEquityBacktest("NONEXISTENT", "SPY", FROM, TO, CTX));
    }
  }

  @Nested
  class RunAllEquityStrategies {

    @Test
    void givenStrategies_whenRunAll_thenMapHasEntries() {
      when(dataReplay.replay(anyString(), any(), any())).thenReturn(List.of());
      when(equityRegistry.all()).thenReturn(List.of(strategy));
      when(strategy.name()).thenReturn("TEST_STRAT");
      when(equityRegistry.get("TEST_STRAT")).thenReturn(Optional.of(strategy));

      Map<String, List<BacktestResult>> results =
          engine.runAllEquityStrategies(List.of("SPY"), FROM, TO, CTX);

      assertEquals(1, results.size());
      assertTrue(results.containsKey("TEST_STRAT"));
    }
  }

  @Nested
  class PersistResult {

    @Test
    void givenResult_whenPersist_thenRepositoryCalled() {
      when(backtestResultRepository.save(any())).thenReturn(1L);

      BacktestResult result = new BacktestResult("RSI_OSCILLATOR", ExecutionDelay.DELAY_0,
          1.5, 0.05, 0.02, 0.6, 0.05, 0.045, 0.5, false, List.of());

      long id = engine.persistResult(result, "SPY", FROM, TO);

      assertEquals(1L, id);
    }
  }
}
