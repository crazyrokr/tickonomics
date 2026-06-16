package com.tickonomics.computation.forecast;

import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.entity.VolatilityForecast;
import com.tickonomics.persistence.repository.TickDataRepository;
import com.tickonomics.persistence.repository.VolatilityForecastRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class ForecastPersistenceServiceTest {

  @Mock
  private VolatilityForecastRepository forecastRepository;

  @Mock
  private TickDataRepository tickDataRepository;

  private ForecastPersistenceService service;

  private static final Instant NOW = Instant.now();

  @BeforeEach
  void setUp() {
    service = new ForecastPersistenceService(forecastRepository, tickDataRepository);
  }

  private Map<String, Object> buildResponse(List<Double> forecast, int nObservations, Double mae) {
    return Map.of(
        "forecast", forecast,
        "model", "garch",
        "n_observations", nObservations,
        "mae_vs_baseline", mae != null ? mae : 0.02,
        "parameters", Map.of("omega", 0.1, "alpha", 0.2, "beta", 0.7));
  }

  @Nested
  class PersistForecastResponse {

    @Test
    void givenValidResponse_whenPersist_thenSavesEachHorizonStep() {
      Map<String, Object> response = buildResponse(List.of(0.18, 0.19, 0.20, 0.21, 0.22), 500, 0.02);

      service.persistForecastResponse("SPY", response);

      verify(forecastRepository, times(5)).save(any(VolatilityForecast.class));
    }

    @Test
    void givenInsufficientObservations_whenPersist_thenSkips() {
      Map<String, Object> response = buildResponse(List.of(0.18, 0.19), 30, 0.02);

      service.persistForecastResponse("SPY", response);

      verify(forecastRepository, never()).save(any());
    }

    @Test
    void givenErrorResponse_whenPersist_thenSkips() {
      Map<String, Object> response = Map.of("error", "connection refused");

      service.persistForecastResponse("SPY", response);

      verify(forecastRepository, never()).save(any());
    }

    @Test
    void givenNullResponse_whenPersist_thenSkips() {
      service.persistForecastResponse("SPY", null);

      verify(forecastRepository, never()).save(any());
    }

    @Test
    void givenEmptyForecastList_whenPersist_thenNoRowsSaved() {
      Map<String, Object> response = buildResponse(List.of(), 500, 0.02);

      service.persistForecastResponse("SPY", response);

      verify(forecastRepository, never()).save(any());
    }

    @Test
    void givenValidResponse_whenPersist_thenCorrectForecastVolPerStep() {
      Map<String, Object> response = buildResponse(List.of(0.15, 0.20, 0.25), 200, 0.01);

      service.persistForecastResponse("AAPL", response);

      ArgumentCaptor<VolatilityForecast> captor = ArgumentCaptor.forClass(VolatilityForecast.class);
      verify(forecastRepository, times(3)).save(captor.capture());

      List<VolatilityForecast> saved = captor.getAllValues();
      assertEquals("AAPL", saved.get(0).symbol());
      assertEquals(0.15, saved.get(0).forecastVol(), 1e-9);
      assertEquals(1, saved.get(0).horizonDays());

      assertEquals(0.20, saved.get(1).forecastVol(), 1e-9);
      assertEquals(2, saved.get(1).horizonDays());

      assertEquals(0.25, saved.get(2).forecastVol(), 1e-9);
      assertEquals(3, saved.get(2).horizonDays());
    }
  }

  @Nested
  class BackfillRealizedVolatility {

    @Test
    void givenElapsedForecastWithTicks_whenBackfill_thenUpdatesRealizedVol() {
      Instant pastTime = NOW.minusSeconds(10 * 86400);
      VolatilityForecast forecast = new VolatilityForecast(
          pastTime, "SPY", "garch", 5, 0.18, null, 0.02, 500, null, null, pastTime);

      when(forecastRepository.findBySymbolAndTimeBetween(eq("SPY"), any(Instant.class), any(Instant.class)))
          .thenReturn(List.of(forecast));

      List<TickData> ticks = List.of(
          new TickData(pastTime.minusSeconds(86400 * 5), "SPY", BigDecimal.valueOf(100.0), 1000, new int[0]),
          new TickData(pastTime.minusSeconds(86400 * 4), "SPY", BigDecimal.valueOf(101.0), 1000, new int[0]),
          new TickData(pastTime.minusSeconds(86400 * 3), "SPY", BigDecimal.valueOf(102.0), 1000, new int[0]),
          new TickData(pastTime.minusSeconds(86400 * 2), "SPY", BigDecimal.valueOf(103.0), 1000, new int[0]),
          new TickData(pastTime.minusSeconds(86400), "SPY", BigDecimal.valueOf(104.0), 1000, new int[0]));
      when(tickDataRepository.findBySymbolAndTimeBetween(eq("SPY"), any(Instant.class), any(Instant.class)))
          .thenReturn(ticks);

      service.backfillRealizedVolatility("SPY");

      verify(forecastRepository).updateRealizedVol(eq(pastTime), eq("SPY"), eq(5), anyDouble());
    }

    @Test
    void givenNoElapsedForecasts_whenBackfill_thenNoUpdates() {
      when(forecastRepository.findBySymbolAndTimeBetween(eq("SPY"), any(Instant.class), any(Instant.class)))
          .thenReturn(List.of());

      service.backfillRealizedVolatility("SPY");

      verify(forecastRepository, never()).updateRealizedVol(any(), anyString(), anyInt(), anyDouble());
    }
  }

  @Nested
  class ComputeAnnualizedVol {

    @Test
    void givenConstantPrices_whenCompute_thenZeroVol() {
      List<TickData> ticks = List.of(
          new TickData(NOW, "SPY", BigDecimal.valueOf(100.0), 1000, new int[0]),
          new TickData(NOW.plusSeconds(86400), "SPY", BigDecimal.valueOf(100.0), 1000, new int[0]),
          new TickData(NOW.plusSeconds(2 * 86400), "SPY", BigDecimal.valueOf(100.0), 1000, new int[0]));

      double vol = service.computeAnnualizedVol(ticks);

      assertEquals(0.0, vol, 1e-9);
    }

    @Test
    void givenRisingPrices_whenCompute_thenPositiveVol() {
      List<TickData> ticks = List.of(
          new TickData(NOW, "SPY", BigDecimal.valueOf(100.0), 1000, new int[0]),
          new TickData(NOW.plusSeconds(86400), "SPY", BigDecimal.valueOf(105.0), 1000, new int[0]),
          new TickData(NOW.plusSeconds(2 * 86400), "SPY", BigDecimal.valueOf(110.0), 1000, new int[0]));

      double vol = service.computeAnnualizedVol(ticks);

      assertTrue(vol > 0.0);
    }

    @Test
    void givenSingleTick_whenCompute_thenZeroVol() {
      List<TickData> ticks = List.of(
          new TickData(NOW, "SPY", BigDecimal.valueOf(100.0), 1000, new int[0]));

      double vol = service.computeAnnualizedVol(ticks);

      assertEquals(0.0, vol, 1e-9);
    }
  }
}
