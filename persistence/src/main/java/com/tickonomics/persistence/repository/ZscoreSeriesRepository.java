package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.ZscoreSeries;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

@Repository
public class ZscoreSeriesRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public ZscoreSeriesRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(ZscoreSeries entry) {
    jdbc.update(
        "INSERT INTO zscore_series (time, component, raw_value, z_score, lookback_days) VALUES (:time, :component, "
            + ":rawValue, :zScore, :lookbackDays)",
        toParams(entry));
  }

  public void saveAll(List<ZscoreSeries> entries) {
    jdbc.batchUpdate(
        "INSERT INTO zscore_series (time, component, raw_value, z_score, lookback_days) VALUES (:time, :component, "
            + ":rawValue, :zScore, :lookbackDays)",
        SqlParameterSourceUtils.createBatch(entries
            .stream()
            .map(this::toParams)
            .toArray(MapSqlParameterSource[]::new)));
  }

  public List<ZscoreSeries> findByComponentAndTimeBetween(String component, Instant from, Instant to) {
    return jdbc.query(
        "SELECT time, component, raw_value, z_score, lookback_days FROM zscore_series WHERE component = :component "
            + "AND time BETWEEN :from AND :to ORDER BY time",
        Map.of("component", component, "from", from, "to", to),
        (rs, rowNum) -> new ZscoreSeries(
            rs
                .getTimestamp("time")
                .toInstant(),
            rs.getString("component"),
            rs.getDouble("raw_value"),
            rs.getDouble("z_score"),
            rs.getInt("lookback_days")));
  }

  private MapSqlParameterSource toParams(ZscoreSeries entry) {
    return new MapSqlParameterSource()
        .addValue("time", entry.time())
        .addValue("component", entry.component())
        .addValue("rawValue", entry.rawValue())
        .addValue("zScore", entry.zScore())
        .addValue("lookbackDays", entry.lookbackDays());
  }
}
