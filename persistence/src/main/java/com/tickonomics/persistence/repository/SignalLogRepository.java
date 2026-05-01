package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.SignalLog;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SignalLogRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public SignalLogRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long save(SignalLog signal) {
    var keyHolder = new GeneratedKeyHolder();
    jdbc.update(
        "INSERT INTO signal_log (created_at, symbol, direction, status, ili_percentile, ili_value, expected_move, "
            + "estimated_cost, signal_metadata) "
            + "VALUES (:createdAt, :symbol, :direction, :status, :iliPercentile, :iliValue, :expectedMove, "
            + ":estimatedCost, :signalMetadata::jsonb)",
        toParams(signal),
        keyHolder,
        new String[]{"id"});
    return keyHolder
        .getKey()
        .longValue();
  }

  public List<SignalLog> findBySymbolAndTimeBetween(String symbol, Instant from, Instant to) {
    return jdbc.query(
        "SELECT created_at, symbol, direction, status, ili_percentile, ili_value, expected_move, estimated_cost, "
            + "signal_metadata "
            + "FROM signal_log WHERE symbol = :symbol AND created_at BETWEEN :from AND :to ORDER BY created_at DESC",
        Map.of("symbol", symbol, "from", from, "to", to),
        (rs, rowNum) -> new SignalLog(
            rs
                .getTimestamp("created_at")
                .toInstant(),
            rs.getString("symbol"),
            rs.getString("direction"),
            rs.getString("status"),
            rs.getDouble("ili_percentile"),
            rs.getDouble("ili_value"),
            rs.getDouble("expected_move"),
            rs.getDouble("estimated_cost"),
            rs.getString("signal_metadata")));
  }

  public List<SignalLog> findLatestByStatus(String status, int limit) {
    return jdbc.query(
        "SELECT created_at, symbol, direction, status, ili_percentile, ili_value, expected_move, estimated_cost, "
            + "signal_metadata "
            + "FROM signal_log WHERE status = :status ORDER BY created_at DESC LIMIT :limit",
        Map.of("status", status, "limit", limit),
        (rs, rowNum) -> new SignalLog(
            rs
                .getTimestamp("created_at")
                .toInstant(),
            rs.getString("symbol"),
            rs.getString("direction"),
            rs.getString("status"),
            rs.getDouble("ili_percentile"),
            rs.getDouble("ili_value"),
            rs.getDouble("expected_move"),
            rs.getDouble("estimated_cost"),
            rs.getString("signal_metadata")));
  }

  private MapSqlParameterSource toParams(SignalLog signal) {
    return new MapSqlParameterSource()
        .addValue("createdAt", signal.createdAt() != null ? signal.createdAt() : Instant.now())
        .addValue("symbol", signal.symbol())
        .addValue("direction", signal.direction())
        .addValue("status", signal.status())
        .addValue("iliPercentile", signal.iliPercentile())
        .addValue("iliValue", signal.iliValue())
        .addValue("expectedMove", signal.expectedMove())
        .addValue("estimatedCost", signal.estimatedCost())
        .addValue("signalMetadata", signal.signalMetadata());
  }
}
