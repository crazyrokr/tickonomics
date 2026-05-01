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

  public RateSnapshotRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(RateSnapshot snapshot) {
    jdbc.update(
        "INSERT INTO rate_snapshots (time, rate_type, value, source) VALUES (:time, :rateType, :value, :source)",
        toParams(snapshot));
  }

  public void saveAll(List<RateSnapshot> snapshots) {
    jdbc.batchUpdate(
        "INSERT INTO rate_snapshots (time, rate_type, value, source) VALUES (:time, :rateType, :value, :source)",
        SqlParameterSourceUtils.createBatch(snapshots
            .stream()
            .map(this::toParams)
            .toArray(MapSqlParameterSource[]::new)));
  }

  public List<RateSnapshot> findByRateTypeAndTimeBetween(String rateType, Instant from, Instant to) {
    return jdbc.query(
        "SELECT time, rate_type, value, source FROM rate_snapshots WHERE rate_type = :rateType AND time BETWEEN :from"
            + " AND :to ORDER BY time",
        Map.of("rateType", rateType, "from", from, "to", to),
        (rs, rowNum) -> new RateSnapshot(
            rs
                .getTimestamp("time")
                .toInstant(), rs.getString("rate_type"), rs.getDouble("value"), rs.getString("source")));
  }

  public List<RateSnapshot> findLatestByRateType(String rateType, int limit) {
    return jdbc.query(
        "SELECT time, rate_type, value, source FROM rate_snapshots WHERE rate_type = :rateType ORDER BY time DESC "
            + "LIMIT :limit",
        Map.of("rateType", rateType, "limit", limit),
        (rs, rowNum) -> new RateSnapshot(
            rs
                .getTimestamp("time")
                .toInstant(), rs.getString("rate_type"), rs.getDouble("value"), rs.getString("source")));
  }

  private MapSqlParameterSource toParams(RateSnapshot snapshot) {
    return new MapSqlParameterSource()
        .addValue("time", snapshot.time())
        .addValue("rateType", snapshot.rateType())
        .addValue("value", snapshot.value())
        .addValue("source", snapshot.source());
  }
}
