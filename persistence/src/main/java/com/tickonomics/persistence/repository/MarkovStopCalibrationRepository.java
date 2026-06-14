package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.MarkovStopCalibration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MarkovStopCalibrationRepository {

  private static final String LATEST_BY_SYMBOL_SQL =
      "SELECT id, symbol, optimal_stop_loss, optimal_take_profit, signal_drift, decay_intensity, "
          + "converged, iterations, calibrated_at FROM markov_stop_calibrations "
          + "WHERE symbol = :symbol ORDER BY calibrated_at DESC LIMIT 1";

  private final NamedParameterJdbcTemplate jdbc;

  public MarkovStopCalibrationRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<MarkovStopCalibration> findLatestBySymbol(String symbol) {
    return jdbc.queryForStream(
        LATEST_BY_SYMBOL_SQL,
        Map.of("symbol", symbol),
        (rs, rowNum) -> new MarkovStopCalibration(
            rs.getLong("id"),
            rs.getString("symbol"),
            rs.getDouble("optimal_stop_loss"),
            rs.getDouble("optimal_take_profit"),
            (Double) rs.getObject("signal_drift"),
            (Double) rs.getObject("decay_intensity"),
            rs.getBoolean("converged"),
            (Integer) rs.getObject("iterations"),
            rs.getTimestamp("calibrated_at").toInstant()))
        .findFirst();
  }

  public long save(MarkovStopCalibration calibration) {
    Instant calibratedAt = calibration.calibratedAt() != null
        ? calibration.calibratedAt() : Instant.now();
    return jdbc.queryForObject(
        "INSERT INTO markov_stop_calibrations "
            + "(symbol, optimal_stop_loss, optimal_take_profit, signal_drift, decay_intensity, "
            + "converged, iterations, calibrated_at) VALUES "
            + "(:symbol, :stopLoss, :takeProfit, :signalDrift, :decayIntensity, "
            + ":converged, :iterations, :calibratedAt) RETURNING id",
        Map.of(
            "symbol", calibration.symbol(),
            "stopLoss", calibration.optimalStopLoss(),
            "takeProfit", calibration.optimalTakeProfit(),
            "signalDrift", calibration.signalDrift(),
            "decayIntensity", calibration.decayIntensity(),
            "converged", calibration.converged(),
            "iterations", calibration.iterations(),
            "calibratedAt", calibratedAt),
        Long.class);
  }
}
