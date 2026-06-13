package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.RateSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

@Repository
public class RateSnapshotRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String SELECT_COLUMNS =
      "time, rate_type, value, source, anomaly_score, is_suspect_anomaly";

  public RateSnapshotRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(RateSnapshot snapshot) {
    jdbc.update(
        "INSERT INTO rate_snapshots (time, rate_type, value, source, anomaly_score, is_suspect_anomaly) "
            + "VALUES (:time, :rateType, :value, :source, :anomalyScore, :isSuspectAnomaly)",
        toParams(snapshot));
  }

  public void saveAll(List<RateSnapshot> snapshots) {
    jdbc.batchUpdate(
        "INSERT INTO rate_snapshots (time, rate_type, value, source, anomaly_score, is_suspect_anomaly) "
            + "VALUES (:time, :rateType, :value, :source, :anomalyScore, :isSuspectAnomaly)",
        SqlParameterSourceUtils.createBatch(snapshots
            .stream()
            .map(this::toParams)
            .toList()));
  }

  public List<RateSnapshot> findByRateTypeAndTimeBetween(String rateType, Instant from, Instant to) {
    return jdbc.query(
        "SELECT " + SELECT_COLUMNS + " "
            + "FROM rate_snapshots WHERE rate_type = :rateType AND time BETWEEN :from AND :to ORDER BY time",
        Map.of("rateType", rateType, "from", from, "to", to),
        this::mapRow);
  }

  public List<RateSnapshot> findLatestByRateType(String rateType, int limit) {
    return jdbc.query(
        "SELECT " + SELECT_COLUMNS + " "
            + "FROM rate_snapshots WHERE rate_type = :rateType ORDER BY time DESC LIMIT :limit",
        Map.of("rateType", rateType, "limit", limit),
        this::mapRow);
  }

  public List<RateSnapshot> findLatestN(int limit) {
    return jdbc.query(
        "SELECT " + SELECT_COLUMNS + " "
            + "FROM rate_snapshots ORDER BY time DESC LIMIT :limit",
        Map.of("limit", limit),
        this::mapRow);
  }

  public void updateAnomalyScore(Instant time, String rateType, double anomalyScore, boolean isSuspectAnomaly) {
    jdbc.update(
        "UPDATE rate_snapshots SET anomaly_score = :score, is_suspect_anomaly = :anomaly "
            + "WHERE time = :time AND rate_type = :rateType",
        Map.of("time", time, "rateType", rateType, "score", anomalyScore, "anomaly", isSuspectAnomaly));
  }

  private RateSnapshot mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new RateSnapshot(
        rs.getTimestamp("time").toInstant(),
        rs.getString("rate_type"),
        rs.getDouble("value"),
        rs.getString("source"),
        rs.getObject("anomaly_score") != null ? rs.getDouble("anomaly_score") : null,
        rs.getObject("is_suspect_anomaly") != null ? rs.getBoolean("is_suspect_anomaly") : null);
  }

  private MapSqlParameterSource toParams(RateSnapshot snapshot) {
    return new MapSqlParameterSource()
        .addValue("time", snapshot.time())
        .addValue("rateType", snapshot.rateType())
        .addValue("value", snapshot.value())
        .addValue("source", snapshot.source())
        .addValue("anomalyScore", snapshot.anomalyScore())
        .addValue("isSuspectAnomaly", snapshot.isSuspectAnomaly());
  }
}
