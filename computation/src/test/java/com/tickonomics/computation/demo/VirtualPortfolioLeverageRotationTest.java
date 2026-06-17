package com.tickonomics.computation.demo;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tickonomics.computation.kpi.SignalResult;
import com.tickonomics.computation.leverage.LeverageSignaler;
import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import com.tickonomics.persistence.repository.VirtualPortfolioPositionRepository;
import com.tickonomics.persistence.repository.VirtualPortfolioTradeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VirtualPortfolioLeverageRotationTest {

  @Mock
  private VirtualPortfolioPositionRepository positionRepository;
  @Mock
  private VirtualPortfolioTradeRepository tradeRepository;
  @Mock
  private MarketPriceLookup priceLookup;

  private VirtualPortfolio portfolio;
  private final LeverageSignaler leverageSignaler = new LeverageSignaler();

  private final SignalResult buySignal = new SignalResult(
      "SPY", SignalResult.DIR_BUY, SignalResult.STATUS_ACTIONABLE,
      85.0, 1.5, 0.02, 5.0, 0.7);

  @BeforeEach
  void setUp() {
    DemoConfig config = DemoConfig.core(
        true, new BigDecimal("100000.00"), 5.0, 5.0, 10.0, true, true, true, 10_000_000.0);
    portfolio = new VirtualPortfolio(positionRepository, tradeRepository, config);
  }

  private List<Double> flatHistory(int size, double value) {
    List<Double> prices = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      prices.add(value);
    }
    return prices;
  }

  @Nested
  class OpenPositionOverload {

    @Test
    void givenCustomStopsAndCommission_whenOpenPosition_thenAppliedToSavedTrade() {
      when(positionRepository.save(any())).thenReturn(1L);

      portfolio.openPosition(buySignal, new BigDecimal("500.0"), 4.0, 8.0, new BigDecimal("12.5"));

      verify(positionRepository).save(any());
    }

    @Test
    void givenTwoArgOverload_whenOpenPosition_thenConfigStopsUsedByDefault() {
      when(positionRepository.save(any())).thenReturn(1L);

      portfolio.openPosition(buySignal, new BigDecimal("500.0"));

      verify(positionRepository).save(any());
    }
  }

  @Nested
  class ApplyLeverageRotation {

    @Test
    void givenPriceBelowSma_whenApplyLeverageRotation_thenPositionsFlattened() {
      when(priceLookup.closingPrices(eq("SPY"), any(), any()))
          .thenReturn(flatHistory(200, 100.0));
      VirtualPortfolioPosition open = new VirtualPortfolioPosition(1L, Instant.now(), "SPY", "BUY",
          new BigDecimal("10.0"), new BigDecimal("100.0"), null, null,
          new BigDecimal("95.0"), new BigDecimal("110.0"), null, null);
      when(positionRepository.findOpenPositions()).thenReturn(List.of(open));
      when(tradeRepository.save(any())).thenReturn(2L);

      VirtualPortfolio.LeverageRotationOutcome outcome = portfolio.applyLeverageRotation(
          priceLookup, leverageSignaler, Map.of("SPY", new BigDecimal("90.0")));

      assertEquals(LeverageSignaler.Signal.LEVERAGE_OFF, outcome.signal().signal());
      assertEquals(1, outcome.closedTrades().size());
      verify(positionRepository).close(eq(1L), any(), eq(new BigDecimal("90.0")));
    }

    @Test
    void givenPriceAboveSma_whenApplyLeverageRotation_thenNoCloses() {
      when(priceLookup.closingPrices(eq("SPY"), any(), any()))
          .thenReturn(flatHistory(200, 100.0));

      VirtualPortfolio.LeverageRotationOutcome outcome = portfolio.applyLeverageRotation(
          priceLookup, leverageSignaler, Map.of("SPY", new BigDecimal("110.0")));

      assertEquals(LeverageSignaler.Signal.LEVERAGE_ON, outcome.signal().signal());
      assertTrue(outcome.closedTrades().isEmpty());
    }

    @Test
    void givenNullCurrentPrices_whenApplyLeverageRotation_thenNoClosesEvenWhenOff() {
      when(priceLookup.closingPrices(eq("SPY"), any(), any()))
          .thenReturn(flatHistory(200, 100.0));

      VirtualPortfolio.LeverageRotationOutcome outcome = portfolio.applyLeverageRotation(
          priceLookup, leverageSignaler, null);

      assertTrue(outcome.closedTrades().isEmpty());
    }
  }
}
