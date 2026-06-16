package com.tickonomics.computation.forecast;

import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.entity.VolatilityForecast;
import com.tickonomics.persistence.repository.TickDataRepository;
import com.tickonomics.persistence.repository.VolatilityForecastRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ForecastPersistenceService {

  private static final Logger log = LoggerFactory.getLogger(ForecastPersistenceService.class);
  static final int MIN_OBSERVATIONS = 50;

  private final VolatilityForecastRepository forecastRepository;
  private final TickDataRepository tickDataRepository;

  public ForecastPersistenceService(
      VolatilityForecastRepository forecastRepository,
      TickDataRepository tickDataRepository) {
    this.forecastRepository = forecastRepository;
    this.tickDataRepository = tickDataRepository;
  }

  @SuppressWarnings("unchecked")
  public void persistForecastResponse(String symbol, Map<String, Object> pythonResponse) {
    if (pythonResponse == null || pythonResponse.containsKey("error")) {
      log.warn("Skipping forecast persistence: error response {}", pythonResponse);
      return;
    }

    Object nObsObj = pythonResponse.get("n_observations");
    int nObservations = nObsObj instanceof Number ? ((Number) nObsObj).intValue() : 0;
    if (nObservations < MIN_OBSERVATIONS) {
      log.info("Skipping forecast persistence: n_observations={} (minimum {})", nObservations, MIN_OBSERVATIONS);
      return;
    }

    Object forecastObj = pythonResponse.get("forecast");
    if (!(forecastObj instanceof List<?> forecastList)) {
      log.warn("No forecast array in response");
      return;
    }

    String model = (String) pythonResponse.getOrDefault("model", "garch");
    Object maeObj = pythonResponse.get("mae_vs_baseline");
    Double maeVsBaseline = maeObj instanceof Number ? ((Number) maeObj).doubleValue() : null;
    String parameters = pythonResponse.get("parameters") != null
        ? pythonResponse.get("parameters").toString()
        : null;

    Instant now = Instant.now();
    int horizonDays = 0;
    for (Object volObj : forecastList) {
      horizonDays++;
      double forecastVol = volObj instanceof Number ? ((Number) volObj).doubleValue() : 0.0;

      VolatilityForecast forecast = new VolatilityForecast(
          now, symbol, model, horizonDays, forecastVol,
          null, maeVsBaseline, nObservations, parameters, null, now);

      forecastRepository.save(forecast);
    }

    log.info("Persisted {} forecast steps for symbol={}, model={}", horizonDays, symbol, model);
  }

  public void backfillRealizedVolatility(String symbol) {
    Instant cutoff = Instant.now().minusSeconds(86400);
    List<VolatilityForecast> forecasts = forecastRepository.findBySymbolAndTimeBetween(
        symbol, Instant.EPOCH, cutoff);

    int updated = 0;
    for (VolatilityForecast f : forecasts) {
      if (f.realizedVol() != null) {
        continue;
      }

      Instant forecastEndTime = f.time().plusSeconds((long) f.horizonDays() * 86400);
      if (forecastEndTime.isAfter(Instant.now())) {
        continue;
      }

      Instant rangeStart = f.time().minusSeconds((long) f.horizonDays() * 86400);
      List<TickData> ticks = tickDataRepository.findBySymbolAndTimeBetween(
          symbol, rangeStart, forecastEndTime);

      if (ticks.size() < 2) {
        continue;
      }

      double realizedVol = computeAnnualizedVol(ticks);
      forecastRepository.updateRealizedVol(f.time(), symbol, f.horizonDays(), realizedVol);
      updated++;
    }

    log.info("Back-filled realized_vol for {} forecasts of {}", updated, symbol);
  }

  double computeAnnualizedVol(List<TickData> ticks) {
    if (ticks.size() < 2) {
      return 0.0;
    }

    double[] returns = new double[ticks.size() - 1];
    for (int i = 1; i < ticks.size(); i++) {
      double prevPrice = ticks.get(i - 1).price();
      double currPrice = ticks.get(i).price();
      returns[i - 1] = Math.log(currPrice / prevPrice);
    }

    double mean = 0.0;
    for (double r : returns) {
      mean += r;
    }
    mean /= returns.length;

    double variance = 0.0;
    for (double r : returns) {
      variance += (r - mean) * (r - mean);
    }
    variance /= (returns.length - 1);

    return Math.sqrt(variance) * Math.sqrt(252);
  }
}
