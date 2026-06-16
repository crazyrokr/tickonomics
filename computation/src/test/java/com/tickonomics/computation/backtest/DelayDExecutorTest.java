package com.tickonomics.computation.backtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DelayDExecutorTest {

  @Mock
  private Eq553SlippageModel slippageModel;

  private DelayDExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new DelayDExecutor(slippageModel);
  }

  @Nested
  class CompareDelays {

    @Test
    void givenPositiveReturns_whenCompareDelays_thenTwoResults() {
      List<Double> signals = List.of(0.01, 0.02, -0.01, 0.03, 0.01);
      List<Double> prices = List.of(0.01, 0.02, -0.01, 0.03, 0.01, 0.02);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(1.0);
      when(slippageModel.adjustReturn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenAnswer(inv -> (double) inv.getArgument(0) - 0.0001);
      when(slippageModel.isLiquidityFragile(anyDouble(), anyDouble())).thenReturn(false);

      List<BacktestResult> results = executor.compareDelays("TEST", signals, prices);

      assertEquals(2, results.size());
      assertEquals(ExecutionDelay.DELAY_0, results.get(0).delay());
      assertEquals(ExecutionDelay.DELAY_1, results.get(1).delay());
    }

    @Test
    void givenEmptyPrices_whenCompareDelays_thenZeroedResults() {
      List<BacktestResult> results = executor.compareDelays(
          "TEST", List.of(), List.of());

      assertEquals(2, results.size());
      assertEquals(0.0, results.get(0).sharpeRatio());
      assertEquals(0.0, results.get(0).totalReturn());
      assertTrue(results.get(0).tradeLog().isEmpty());
    }

    @Test
    void givenMismatchedSizes_whenCompareDelays_thenUsesMinLength() {
      List<Double> signals = List.of(0.01, 0.02);
      List<Double> prices = List.of(0.01, 0.02, -0.01, 0.03);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(0.5);
      when(slippageModel.adjustReturn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenAnswer(inv -> (Double) inv.getArgument(0));
      when(slippageModel.isLiquidityFragile(anyDouble(), anyDouble())).thenReturn(false);

      List<BacktestResult> results = executor.compareDelays("TEST", signals, prices);

      assertEquals(2, results.size());
      assertFalse(results.get(0).tradeLog().isEmpty());
    }
  }

  @Nested
  class SlippageIntegration {

    @Test
    void givenEq553Slippage_whenExecute_thenAdjustedReturnDiffersFromIdeal() {
      List<Double> signals = List.of(0.05, 0.03, 0.02);
      List<Double> prices = List.of(0.05, 0.03, 0.02, 0.01);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(5.0);
      when(slippageModel.adjustReturn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenAnswer(inv -> {
            double ideal = inv.getArgument(0);
            return ideal - 0.0005;
          });
      when(slippageModel.isLiquidityFragile(anyDouble(), anyDouble())).thenReturn(false);

      List<BacktestResult> results = executor.compareDelays("TEST", signals, prices);
      BacktestResult delay0 = results.get(0);

      assertFalse(delay0.tradeLog().isEmpty());
      Map<String, Object> firstTrade = delay0.tradeLog().get(0);
      assertEquals(5.0, (double) firstTrade.get("slippageBps"), 0.001);
    }

    @Test
    void givenZeroVolume_whenExecute_thenMaxSlippageReturned() {
      List<Double> signals = List.of(0.01);
      List<Double> prices = List.of(0.01, 0.01);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(Double.MAX_VALUE);
      when(slippageModel.adjustReturn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenAnswer(inv -> {
            double ideal = inv.getArgument(0);
            return ideal - Double.MAX_VALUE / 10000.0;
          });
      when(slippageModel.isLiquidityFragile(anyDouble(), anyDouble())).thenReturn(true);

      List<BacktestResult> results = executor.compareDelays("TEST", signals, prices);

      assertTrue(results.get(0).liquidityFragile());
    }
  }

  @Nested
  class TradeLogPopulation {

    @Test
    void givenThreeTrades_whenExecute_thenTradeLogHasThreeEntries() {
      List<Double> signals = List.of(0.01, 0.02, 0.03);
      List<Double> prices = List.of(0.01, 0.02, 0.03, 0.01);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(1.0);
      when(slippageModel.adjustReturn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenAnswer(inv -> (Double) inv.getArgument(0));
      when(slippageModel.isLiquidityFragile(anyDouble(), anyDouble())).thenReturn(false);

      List<BacktestResult> results = executor.compareDelays("TEST", signals, prices);

      assertEquals(3, results.get(0).tradeLog().size());
    }

    @Test
    void givenSlippageModel_whenExecute_thenTradeLogContainsSlippageBps() {
      List<Double> signals = List.of(0.05);
      List<Double> prices = List.of(0.05, 0.05);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(12.5);
      when(slippageModel.adjustReturn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenAnswer(inv -> (double) inv.getArgument(0) - 0.00125);
      when(slippageModel.isLiquidityFragile(anyDouble(), anyDouble())).thenReturn(false);

      List<BacktestResult> results = executor.compareDelays("TEST", signals, prices);
      Map<String, Object> trade = results.get(0).tradeLog().get(0);

      assertEquals(12.5, (double) trade.get("slippageBps"), 0.001);
      assertEquals(0.05, (double) trade.get("signalReturn"), 0.001);
      assertEquals(0.05, (double) trade.get("executionReturn"), 0.001);
      assertEquals(0.05 - 0.00125, (double) trade.get("adjustedReturn"), 0.0001);
    }

    @Test
    void givenTradeLog_whenExecute_thenIndexFieldPopulated() {
      List<Double> signals = List.of(0.01, 0.02);
      List<Double> prices = List.of(0.01, 0.02, 0.01);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(1.0);
      when(slippageModel.adjustReturn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenAnswer(inv -> (Double) inv.getArgument(0));
      when(slippageModel.isLiquidityFragile(anyDouble(), anyDouble())).thenReturn(false);

      List<BacktestResult> results = executor.compareDelays("TEST", signals, prices);

      assertEquals(0, results.get(0).tradeLog().get(0).get("index"));
      assertEquals(1, results.get(0).tradeLog().get(1).get("index"));
    }
  }

  @Nested
  class LiquidityFragility {

    @Test
    void givenHighSlippage_whenCompareDelays_thenLiquidityFragile() {
      List<Double> signals = List.of(0.01);
      List<Double> prices = List.of(0.01, 0.01);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(500.0);
      when(slippageModel.adjustReturn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenAnswer(inv -> {
            double ideal = inv.getArgument(0);
            return ideal - 0.05;
          });
      when(slippageModel.isLiquidityFragile(anyDouble(), anyDouble())).thenReturn(true);

      List<BacktestResult> results = executor.compareDelays("TEST", signals, prices);

      assertTrue(results.get(0).liquidityFragile());
    }

    @Test
    void givenNoSlippage_whenCompareDelays_thenNotLiquidityFragile() {
      List<Double> signals = List.of(0.01, 0.02);
      List<Double> prices = List.of(0.01, 0.02, 0.01);
      when(slippageModel.calculateSlippageBps(anyDouble(), anyDouble(), anyDouble()))
          .thenReturn(0.0);
      when(slippageModel.adjustReturn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
          .thenAnswer(inv -> (Double) inv.getArgument(0));
      when(slippageModel.isLiquidityFragile(anyDouble(), anyDouble())).thenReturn(false);

      List<BacktestResult> results = executor.compareDelays("TEST", signals, prices);

      assertFalse(results.get(0).liquidityFragile());
    }
  }
}
