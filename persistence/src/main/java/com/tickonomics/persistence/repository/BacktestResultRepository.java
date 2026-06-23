package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.config.QueryLimits;
import com.tickonomics.persistence.entity.BacktestResultRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class BacktestResultRepository {

  private static final Logger log = LoggerFactory.getLogger(BacktestResultRepository.class);

  /**
   * Search strategy name inside {@code strategy_config} JSONB. The column holds
   * {@code {"strategy":"<name>"}} (see BacktestEngine), so {@code ->>'strategy'} extracts the value
   * for an indexable {@code ILIKE} instead of scanning the casted JSON text with a leading wildcard.
   * A GIN trigram index backs this (see V40 migration).
   */
  private static final String NAME_PREDICATE = "strategy_config->>'strategy' ILIKE :namePattern";

  private static final String SELECT_SQL =
      "SELECT id, run_at, strategy_config, date_range, sharpe_ratio, max_drawdown, win_rate, "
          + "profit_factor, equity_curve, git_sha, dataset_hash, model_hyperparams, rds_score, "
          + "parameter_slice_metadata, adjusted_p_values FROM backtest_results";

  private final NamedParameterJdbcTemplate jdbc;
  private final QueryLimits queryLimits;

  private final RowMapper<BacktestResultRecord> rowMapper = (rs, rowNum) -> new BacktestResultRecord(
      rs.getLong("id"),
      rs.getTimestamp("run_at").toInstant(),
      rs.getString("strategy_config"),
      rs.getString("date_range"),
      rs.getObject("sharpe_ratio") != null ? rs.getDouble("sharpe_ratio") : null,
      rs.getObject("max_drawdown") != null ? rs.getDouble("max_drawdown") : null,
      rs.getObject("win_rate") != null ? rs.getDouble("win_rate") : null,
      rs.getObject("profit_factor") != null ? rs.getDouble("profit_factor") : null,
      rs.getString("equity_curve"),
      rs.getString("git_sha"),
      rs.getString("dataset_hash"),
      rs.getString("model_hyperparams"),
      rs.getObject("rds_score") != null ? rs.getInt("rds_score") : null,
      rs.getString("parameter_slice_metadata"),
      rs.getString("adjusted_p_values"));

  public BacktestResultRepository(NamedParameterJdbcTemplate jdbc, QueryLimits queryLimits) {
    this.jdbc = jdbc;
    this.queryLimits = queryLimits;
  }

  public long save(BacktestResultRecord record) {
    var keyHolder = new GeneratedKeyHolder();
    jdbc.update(
        "INSERT INTO backtest_results (run_at, strategy_config, date_range, sharpe_ratio, "
            + "max_drawdown, win_rate, profit_factor, equity_curve, git_sha, dataset_hash, "
            + "model_hyperparams, rds_score, parameter_slice_metadata, adjusted_p_values) "
            + "VALUES (:runAt, :strategyConfig::jsonb, :dateRange, :sharpeRatio, :maxDrawdown, "
            + ":winRate, :profitFactor, :equityCurve::jsonb, :gitSha, :datasetHash, "
            + ":modelHyperparams::jsonb, :rdsScore, :parameterSliceMetadata::jsonb, "
            + ":adjustedPValues::jsonb)",
        toParams(record),
        keyHolder,
        new String[]{"id"});
    return KeyHolderUtils.extractGeneratedLong(keyHolder);
  }

  public Optional<BacktestResultRecord> findById(long id) {
    List<BacktestResultRecord> results = jdbc.query(
        SELECT_SQL + " WHERE id = :id",
        Map.of("id", id),
        rowMapper);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  public List<BacktestResultRecord> findByStrategyNameAndTimeBetween(
      String strategyName, Instant from, Instant to) {
    return findByStrategyNameAndTimeBetween(strategyName, from, to, queryLimits.defaultLimit(), 0);
  }

  public List<BacktestResultRecord> findByStrategyNameAndTimeBetween(
      String strategyName, Instant from, Instant to, int limit, Integer offset) {
    var params = new MapSqlParameterSource()
        .addValue("namePattern", "%" + strategyName + "%")
        .addValue("from", from)
        .addValue("to", to);
    String sql = SELECT_SQL + " WHERE " + NAME_PREDICATE + " AND run_at BETWEEN :from AND :to ORDER BY run_at DESC";
    return BoundedRangeQuery.execute(
        jdbc, sql, params, rowMapper, limit, offset, log, "BacktestResult.findByStrategyNameAndTimeBetween");
  }

  /**
   * Explicit pagination over backtest runs by strategy name, with a full {@code total} row count.
   * Use this when a caller needs page metadata; prefer the bounded {@code findByStrategyNameAndTimeBetween}
   * overload for a simple capped slice.
   */
  public PaginatedResponse<BacktestResultRecord> findPageByStrategyNameAndTimeBetween(
      String strategyName, Instant from, Instant to, int limit, int offset) {
    var params = new MapSqlParameterSource()
        .addValue("namePattern", "%" + strategyName + "%")
        .addValue("from", from)
        .addValue("to", to)
        .addValue("limit", limit)
        .addValue("offset", Math.max(0, offset));
    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM backtest_results WHERE " + NAME_PREDICATE + " AND run_at BETWEEN :from AND :to",
        params,
        Long.class);
    List<BacktestResultRecord> items = jdbc.query(
        SELECT_SQL + " WHERE " + NAME_PREDICATE + " AND run_at BETWEEN :from AND :to "
            + "ORDER BY run_at DESC LIMIT :limit OFFSET :offset",
        params,
        rowMapper);
    return new PaginatedResponse<>(items, total != null ? total : 0L, limit, offset);
  }

  public List<BacktestResultRecord> findLatest(int limit) {
    return jdbc.query(
        SELECT_SQL + " ORDER BY run_at DESC LIMIT :limit",
        Map.of("limit", limit),
        rowMapper);
  }

  private MapSqlParameterSource toParams(BacktestResultRecord record) {
    return new MapSqlParameterSource()
        .addValue("runAt", record.runAt())
        .addValue("strategyConfig", record.strategyConfig())
        .addValue("dateRange", record.dateRange())
        .addValue("sharpeRatio", record.sharpeRatio())
        .addValue("maxDrawdown", record.maxDrawdown())
        .addValue("winRate", record.winRate())
        .addValue("profitFactor", record.profitFactor())
        .addValue("equityCurve", record.equityCurve())
        .addValue("gitSha", record.gitSha())
        .addValue("datasetHash", record.datasetHash())
        .addValue("modelHyperparams", record.modelHyperparams())
        .addValue("rdsScore", record.rdsScore())
        .addValue("parameterSliceMetadata", record.parameterSliceMetadata())
        .addValue("adjustedPValues", record.adjustedPValues());
  }
}
