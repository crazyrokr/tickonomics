package com.tickonomics.computation.backtest;

import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.repository.TickDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class HistoricalDataReplayTest {

  @Mock
  private TickDataRepository tickDataRepository;

  private HistoricalDataReplay replay;

  private static final Instant T0 = Instant.parse("2025-01-01T10:00:00Z");
  private static final Instant T1 = Instant.parse("2025-01-01T14:00:00Z");
  private static final Instant T2 = Instant.parse("2025-01-02T10:00:00Z");
  private static final Instant T3 = Instant.parse("2025-01-02T16:00:00Z");
  private static final Instant T4 = Instant.parse("2025-01-03T10:00:00Z");

  @BeforeEach
  void setUp() {
    replay = new HistoricalDataReplay(tickDataRepository);
  }

  @Nested
  class ReplaySingleSymbol {

    @Test
    void givenSymbolAndRange_whenReplay_thenReturnTicks() {
      List<TickData> ticks = List.of(
          new TickData(T0, "SPY", BigDecimal.valueOf(100.0), 1000, new int[]{}),
          new TickData(T1, "SPY", BigDecimal.valueOf(101.0), 1500, new int[]{}));
      when(tickDataRepository.findBySymbolAndTimeBetween("SPY", T0, T4))
          .thenReturn(ticks);

      List<TickData> result = replay.replay("SPY", T0, T4);

      assertEquals(2, result.size());
    }

    @Test
    void givenNoTicks_whenReplay_thenReturnEmpty() {
      when(tickDataRepository.findBySymbolAndTimeBetween("EMPTY", T0, T4))
          .thenReturn(List.of());

      List<TickData> result = replay.replay("EMPTY", T0, T4);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class ReplayMultipleSymbols {

    @Test
    void givenMultipleSymbols_whenReplay_thenReturnMap() {
      List<TickData> spyTicks = List.of(
          new TickData(T0, "SPY", BigDecimal.valueOf(100.0), 1000, new int[]{}));
      List<TickData> qqqTicks = List.of(
          new TickData(T0, "QQQ", BigDecimal.valueOf(200.0), 2000, new int[]{}));

      when(tickDataRepository.findBySymbolAndTimeBetween("SPY", T0, T4))
          .thenReturn(spyTicks);
      when(tickDataRepository.findBySymbolAndTimeBetween("QQQ", T0, T4))
          .thenReturn(qqqTicks);

      Map<String, List<TickData>> result = replay.replay(List.of("SPY", "QQQ"), T0, T4);

      assertEquals(2, result.size());
      assertEquals(1, result.get("SPY").size());
      assertEquals(1, result.get("QQQ").size());
    }
  }

  @Nested
  class ComputeDailyReturns {

    @Test
    void givenMultiDayTicks_whenComputeDailyReturns_thenReturnReturns() {
      List<TickData> ticks = List.of(
          new TickData(T0, "SPY", BigDecimal.valueOf(100.0), 1000, new int[]{}),
          new TickData(T1, "SPY", BigDecimal.valueOf(102.0), 1500, new int[]{}),
          new TickData(T2, "SPY", BigDecimal.valueOf(105.0), 1200, new int[]{}),
          new TickData(T4, "SPY", BigDecimal.valueOf(99.0), 800, new int[]{}));

      List<Double> returns = replay.computeDailyReturns(ticks);

      assertEquals(2, returns.size());
      assertEquals((105.0 - 102.0) / 102.0, returns.get(0), 0.0001);
      assertEquals((99.0 - 105.0) / 105.0, returns.get(1), 0.0001);
    }

    @Test
    void givenSingleTick_whenComputeDailyReturns_thenReturnEmpty() {
      List<TickData> ticks = List.of(
          new TickData(T0, "SPY", BigDecimal.valueOf(100.0), 1000, new int[]{}));

      List<Double> returns = replay.computeDailyReturns(ticks);

      assertTrue(returns.isEmpty());
    }

    @Test
    void givenEmptyTicks_whenComputeDailyReturns_thenReturnEmpty() {
      List<Double> returns = replay.computeDailyReturns(List.of());

      assertTrue(returns.isEmpty());
    }

    @Test
    void givenNullTicks_whenComputeDailyReturns_thenReturnEmpty() {
      List<Double> returns = replay.computeDailyReturns(null);

      assertTrue(returns.isEmpty());
    }

    @Test
    void givenSameDayTicksOnly_whenComputeDailyReturns_thenReturnEmpty() {
      List<TickData> ticks = List.of(
          new TickData(T0, "SPY", BigDecimal.valueOf(100.0), 1000, new int[]{}),
          new TickData(T1, "SPY", BigDecimal.valueOf(102.0), 1500, new int[]{}));

      List<Double> returns = replay.computeDailyReturns(ticks);

      assertTrue(returns.isEmpty());
    }
  }
}
