package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.config.QueryLimits;
import com.tickonomics.persistence.entity.ZscoreSeries;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class ZscoreSeriesRepository {

  private static final Logger log = LoggerFactory.getLogger(ZscoreSeriesRepository.class);

  private static final String SELECT_SQL =
      "SELECT time, component, raw_value, z_score, lookback_days FROM zscore_series WHERE component = :component "
          + "AND time BETWEEN :from AND :to ORDER BY time";

  private final NamedParameterJdbcTemplate jdbc;
  private final QueryLimits queryLimits;

  private final RowMapper<ZscoreSeries> rowMapper = (rs, rowNum) -> new ZscoreSeries(
      rs.getTimestamp("time").toInstant(),
      rs.getString("component"),
      RowMapperUtils.getNullableDouble(rs, "raw_value"),
      RowMapperUtils.getNullableDouble(rs, "z_score"),
      rs.getInt("lookback_days"));

  public ZscoreSeriesRepository(NamedParameterJdbcTemplate jdbc, QueryLimits queryLimits) {
    this.jdbc = jdbc;
    this.queryLimits = queryLimits;
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
        entries
            .stream()
            .map(this::toParams)
            .toArray(SqlParameterSource[]::new));
  }

  public List<ZscoreSeries> findByComponentAndTimeBetween(String component, Instant from, Instant to) {
    return findByComponentAndTimeBetween(component, from, to, queryLimits.defaultLimit(), 0);
  }

  public List<ZscoreSeries> findByComponentAndTimeBetween(
      String component, Instant from, Instant to, int limit, Integer offset) {
    var params = new MapSqlParameterSource()
        .addValue("component", component)
        .addValue("from", from)
        .addValue("to", to);
    return BoundedRangeQuery.execute(
        jdbc, SELECT_SQL, params, rowMapper, limit, offset, log, "ZscoreSeries.findByComponentAndTimeBetween");
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
