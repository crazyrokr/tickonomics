package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.config.QueryLimits;
import com.tickonomics.persistence.entity.IliHistory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IliHistoryRepository {

  private static final Logger log = LoggerFactory.getLogger(IliHistoryRepository.class);

  private static final String SELECT_COLUMNS =
      "time, ili_value, z_rrp, z_spread, z_vol, data_status, active_weights, "
          + "proxy_divergence_status, proxy_divergence_score, anomaly_score, is_suspect_anomaly";

  private static final String RANGE_SQL =
      "SELECT " + SELECT_COLUMNS + " FROM ili_history WHERE time BETWEEN :from AND :to ORDER BY time";

  private final NamedParameterJdbcTemplate jdbc;
  private final QueryLimits queryLimits;

  private final RowMapper<IliHistory> rowMapper = (rs, rowNum) -> new IliHistory(
      rs.getTimestamp("time").toInstant(),
      rs.getDouble("ili_value"),
      rs.getDouble("z_rrp"),
      rs.getDouble("z_spread"),
      rs.getDouble("z_vol"),
      rs.getString("data_status"),
      rs.getString("active_weights"),
      rs.getString("proxy_divergence_status"),
      RowMapperUtils.getNullableDouble(rs, "proxy_divergence_score"),
      RowMapperUtils.getNullableDouble(rs, "anomaly_score"),
      RowMapperUtils.getNullableBoolean(rs, "is_suspect_anomaly"));

  public IliHistoryRepository(NamedParameterJdbcTemplate jdbc, QueryLimits queryLimits) {
    this.jdbc = jdbc;
    this.queryLimits = queryLimits;
  }

  public void save(IliHistory entry) {
    jdbc.update(
        "INSERT INTO ili_history (time, ili_value, z_rrp, z_spread, z_vol, data_status, active_weights, "
            + "proxy_divergence_status, proxy_divergence_score, anomaly_score, is_suspect_anomaly) "
            + "VALUES (:time, :iliValue, :zRrp, :zSpread, :zVol, :dataStatus, :activeWeights::jsonb, "
            + ":proxyDivergenceStatus, :proxyDivergenceScore, :anomalyScore, :isSuspectAnomaly)",
        toParams(entry));
  }

  public List<IliHistory> findByTimeBetween(Instant from, Instant to) {
    return findByTimeBetween(from, to, queryLimits.defaultLimit(), 0);
  }

  public List<IliHistory> findByTimeBetween(Instant from, Instant to, int limit, Integer offset) {
    var params = new MapSqlParameterSource().addValue("from", from).addValue("to", to);
    return BoundedRangeQuery.execute(jdbc, RANGE_SQL, params, rowMapper, limit, offset, log, "IliHistory.findByTimeBetween");
  }

  public IliHistory findLatest() {
    return jdbc.queryForObject(
        "SELECT " + SELECT_COLUMNS + " FROM ili_history ORDER BY time DESC LIMIT 1",
        Map.of(),
        rowMapper);
  }

  public List<IliHistory> findLatestN(int limit) {
    return jdbc.query(
        "SELECT " + SELECT_COLUMNS + " FROM ili_history ORDER BY time DESC LIMIT :limit",
        Map.of("limit", limit),
        rowMapper);
  }

  public void updateAnomalyScore(Instant time, double anomalyScore, boolean isSuspectAnomaly) {
    jdbc.update(
        "UPDATE ili_history SET anomaly_score = :score, is_suspect_anomaly = :anomaly "
            + "WHERE time = :time",
        Map.of("time", time, "score", anomalyScore, "anomaly", isSuspectAnomaly));
  }

  private MapSqlParameterSource toParams(IliHistory entry) {
    return new MapSqlParameterSource()
        .addValue("time", entry.time())
        .addValue("iliValue", entry.iliValue())
        .addValue("zRrp", entry.zRrp())
        .addValue("zSpread", entry.zSpread())
        .addValue("zVol", entry.zVol())
        .addValue("dataStatus", entry.dataStatus())
        .addValue("activeWeights", entry.activeWeights())
        .addValue("proxyDivergenceStatus", entry.proxyDivergenceStatus())
        .addValue("proxyDivergenceScore", entry.proxyDivergenceScore())
        .addValue("anomalyScore", entry.anomalyScore())
        .addValue("isSuspectAnomaly", entry.isSuspectAnomaly());
  }
}
