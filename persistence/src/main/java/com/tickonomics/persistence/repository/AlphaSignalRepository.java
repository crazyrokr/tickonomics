package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.AlphaSignalRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

@Repository
public class AlphaSignalRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private final RowMapper<AlphaSignalRecord> rowMapper = (rs, rowNum) -> new AlphaSignalRecord(
      rs.getTimestamp("time").toInstant(),
      UUID.fromString(rs.getString("strategy_id")),
      rs.getString("symbol"),
      rs.getString("direction"),
      rs.getDouble("strength"),
      rs.getDouble("confidence"),
      rs.getObject("expected_move") != null ? rs.getDouble("expected_move") : null,
      rs.getString("metadata"));

  public AlphaSignalRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(AlphaSignalRecord signal) {
    jdbc.update(
        "INSERT INTO alpha_signals (time, strategy_id, symbol, direction, strength, confidence, "
            + "expected_move, metadata) "
            + "VALUES (:time, :strategyId, :symbol, :direction, :strength, :confidence, "
            + ":expectedMove, :metadata::jsonb)",
        toParams(signal));
  }

  public void saveAll(List<AlphaSignalRecord> signals) {
    jdbc.batchUpdate(
        "INSERT INTO alpha_signals (time, strategy_id, symbol, direction, strength, confidence, "
            + "expected_move, metadata) "
            + "VALUES (:time, :strategyId, :symbol, :direction, :strength, :confidence, "
            + ":expectedMove, :metadata::jsonb)",
        SqlParameterSourceUtils.createBatch(signals
            .stream()
            .map(this::toParams)
            .toList()));
  }

  public List<AlphaSignalRecord> findByStrategyIdAndTimeBetween(
      UUID strategyId, Instant from, Instant to) {
    return jdbc.query(
        "SELECT time, strategy_id, symbol, direction, strength, confidence, expected_move, metadata "
            + "FROM alpha_signals WHERE strategy_id = :strategyId AND time BETWEEN :from AND :to "
            + "ORDER BY time",
        Map.of("strategyId", strategyId, "from", from, "to", to),
        rowMapper);
  }

  public List<AlphaSignalRecord> findBySymbolAndTimeBetween(
      String symbol, Instant from, Instant to) {
    return jdbc.query(
        "SELECT time, strategy_id, symbol, direction, strength, confidence, expected_move, metadata "
            + "FROM alpha_signals WHERE symbol = :symbol AND time BETWEEN :from AND :to "
            + "ORDER BY time",
        Map.of("symbol", symbol, "from", from, "to", to),
        rowMapper);
  }

  public List<AlphaSignalRecord> findLatestByStrategyId(UUID strategyId, int limit) {
    return jdbc.query(
        "SELECT time, strategy_id, symbol, direction, strength, confidence, expected_move, metadata "
            + "FROM alpha_signals WHERE strategy_id = :strategyId ORDER BY time DESC LIMIT :limit",
        Map.of("strategyId", strategyId, "limit", limit),
        rowMapper);
  }

  private MapSqlParameterSource toParams(AlphaSignalRecord signal) {
    return new MapSqlParameterSource()
        .addValue("time", signal.time())
        .addValue("strategyId", signal.strategyId())
        .addValue("symbol", signal.symbol())
        .addValue("direction", signal.direction())
        .addValue("strength", signal.strength())
        .addValue("confidence", signal.confidence())
        .addValue("expectedMove", signal.expectedMove())
        .addValue("metadata", signal.metadata());
  }
}
