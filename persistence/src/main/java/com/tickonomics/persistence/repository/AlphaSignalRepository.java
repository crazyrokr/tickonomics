package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.config.QueryLimits;
import com.tickonomics.persistence.entity.AlphaSignalRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class AlphaSignalRepository {

  private static final Logger log = LoggerFactory.getLogger(AlphaSignalRepository.class);

  private static final String SELECT_SQL =
      "SELECT time, strategy_id, symbol, direction, strength, confidence, expected_move, metadata "
          + "FROM alpha_signals";

  private final NamedParameterJdbcTemplate jdbc;
  private final QueryLimits queryLimits;

  private final RowMapper<AlphaSignalRecord> rowMapper = (rs, rowNum) -> new AlphaSignalRecord(
      rs.getTimestamp("time").toInstant(),
      UUID.fromString(rs.getString("strategy_id")),
      rs.getString("symbol"),
      rs.getString("direction"),
      rs.getDouble("strength"),
      rs.getDouble("confidence"),
      rs.getObject("expected_move") != null ? rs.getDouble("expected_move") : null,
      rs.getString("metadata"));

  public AlphaSignalRepository(NamedParameterJdbcTemplate jdbc, QueryLimits queryLimits) {
    this.jdbc = jdbc;
    this.queryLimits = queryLimits;
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
        signals
            .stream()
            .map(this::toParams)
            .toArray(SqlParameterSource[]::new));
  }

  public List<AlphaSignalRecord> findByStrategyIdAndTimeBetween(
      UUID strategyId, Instant from, Instant to) {
    return findByStrategyIdAndTimeBetween(strategyId, from, to, queryLimits.defaultLimit(), 0);
  }

  public List<AlphaSignalRecord> findByStrategyIdAndTimeBetween(
      UUID strategyId, Instant from, Instant to, int limit, Integer offset) {
    var params = new MapSqlParameterSource()
        .addValue("strategyId", strategyId)
        .addValue("from", from)
        .addValue("to", to);
    String sql = SELECT_SQL + " WHERE strategy_id = :strategyId AND time BETWEEN :from AND :to ORDER BY time";
    return BoundedRangeQuery.execute(jdbc, sql, params, rowMapper, limit, offset, log, "AlphaSignal.findByStrategyIdAndTimeBetween");
  }

  public List<AlphaSignalRecord> findBySymbolAndTimeBetween(
      String symbol, Instant from, Instant to) {
    return findBySymbolAndTimeBetween(symbol, from, to, queryLimits.defaultLimit(), 0);
  }

  public List<AlphaSignalRecord> findBySymbolAndTimeBetween(
      String symbol, Instant from, Instant to, int limit, Integer offset) {
    var params = new MapSqlParameterSource()
        .addValue("symbol", symbol)
        .addValue("from", from)
        .addValue("to", to);
    String sql = SELECT_SQL + " WHERE symbol = :symbol AND time BETWEEN :from AND :to ORDER BY time";
    return BoundedRangeQuery.execute(jdbc, sql, params, rowMapper, limit, offset, log, "AlphaSignal.findBySymbolAndTimeBetween");
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
