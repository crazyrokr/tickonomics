package com.tickonomics.computation.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.tickonomics.persistence.entity.DailyClose;
import com.tickonomics.persistence.repository.OhlcvDailyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OhlcvDailyMarketPriceLookupTest {

  @Mock
  private OhlcvDailyRepository ohlcvDailyRepository;

  private MarketPriceLookup lookup;

  @BeforeEach
  void setUp() {
    lookup = new OhlcvDailyMarketPriceLookup(ohlcvDailyRepository);
  }

  @Nested
  class ClosingPrice {

    @Test
    void givenCloseExists_whenClosingPrice_thenPresent() {
      LocalDate day = LocalDate.of(2026, 6, 13);
      when(ohlcvDailyRepository.findClose("SPY", day)).thenReturn(Optional.of(500.25));

      Optional<Double> result = lookup.closingPrice("SPY", day);

      assertTrue(result.isPresent());
      assertEquals(500.25, result.get());
    }

    @Test
    void givenNoClose_whenClosingPrice_thenEmpty() {
      LocalDate day = LocalDate.of(2026, 6, 13);
      when(ohlcvDailyRepository.findClose("SPY", day)).thenReturn(Optional.empty());

      Optional<Double> result = lookup.closingPrice("SPY", day);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class ClosingPrices {

    @Test
    void givenRangeWithCloses_whenClosingPrices_thenOrderedClosesReturned() {
      LocalDate from = LocalDate.of(2026, 6, 10);
      LocalDate to = LocalDate.of(2026, 6, 13);
      when(ohlcvDailyRepository.findClosesBetween("SPY", from, to)).thenReturn(List.of(
          new DailyClose(LocalDate.of(2026, 6, 10), 100.0),
          new DailyClose(LocalDate.of(2026, 6, 11), 102.0),
          new DailyClose(LocalDate.of(2026, 6, 12), 101.0)));

      List<Double> prices = lookup.closingPrices("SPY", from, to);

      assertEquals(List.of(100.0, 102.0, 101.0), prices);
    }

    @Test
    void givenEmptyRange_whenClosingPrices_thenEmptyList() {
      LocalDate from = LocalDate.of(2026, 6, 10);
      LocalDate to = LocalDate.of(2026, 6, 13);
      when(ohlcvDailyRepository.findClosesBetween("SPY", from, to)).thenReturn(List.of());

      List<Double> prices = lookup.closingPrices("SPY", from, to);

      assertTrue(prices.isEmpty());
    }
  }
}
