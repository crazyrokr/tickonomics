package com.tickonomics.computation.demo;

import com.tickonomics.persistence.entity.MarkovStopCalibration;
import com.tickonomics.persistence.repository.MarkovStopCalibrationRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Java consumer of the {@code markov_stop_calibrations} hypertable (populated by the Python
 * {@code markov_stop_service}). Bridges calibrated state-dependent stops into {@link PaperTradingEngine};
 * when no calibration exists the engine falls back to the configured fixed stops.
 */
@Component
public class MarkovStopHandler {

  private final MarkovStopCalibrationRepository repository;

  public MarkovStopHandler(MarkovStopCalibrationRepository repository) {
    this.repository = repository;
  }

  public Optional<CalibratedStops> stopsFor(String symbol) {
    return repository.findLatestBySymbol(symbol).map(MarkovStopHandler::toStops);
  }

  private static CalibratedStops toStops(MarkovStopCalibration calibration) {
    return new CalibratedStops(
        calibration.symbol(),
        calibration.optimalStopLoss(),
        calibration.optimalTakeProfit(),
        calibration.converged());
  }

  public record CalibratedStops(
      String symbol,
      double stopLossPct,
      double takeProfitPct,
      boolean converged) {}
}
