package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.config.QueryLimits;
import com.tickonomics.persistence.entity.CorrelationOutput;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CorrelationOutputRepository {

  private static final Logger log = LoggerFactory.getLogger(CorrelationOutputRepository.class);

  private static final String SELECT_SQL =
      "SELECT time, symbol, metric, correlation, p_value, sample_size, lag_order, direction "
          + "FROM correlation_outputs WHERE symbol = :symbol AND metric = :metric "
          + "AND time BETWEEN :from AND :to ORDER BY time";

  private final NamedParameterJdbcTemplate jdbc;
  private final QueryLimits queryLimits;

  private final RowMapper<CorrelationOutput> rowMapper = (rs, rowNum) -> new CorrelationOutput(
      rs.getTimestamp("time").toInstant(),
      rs.getString("symbol"),
      rs.getString("metric"),
      RowMapperUtils.getNullableDouble(rs, "correlation"),
      RowMapperUtils.getNullableDouble(rs, "p_value"),
      RowMapperUtils.getNullableInt(rs, "sample_size"),
      RowMapperUtils.getNullableInt(rs, "lag_order"),
      rs.getString("direction"));

  public CorrelationOutputRepository(NamedParameterJdbcTemplate jdbc, QueryLimits queryLimits) {
    this.jdbc = jdbc;
    this.queryLimits = queryLimits;
  }

  public void save(CorrelationOutput output) {
    jdbc.update(
        "INSERT INTO correlation_outputs (time, symbol, metric, correlation, p_value, sample_size, lag_order, "
            + "direction) "
            + "VALUES (:time, :symbol, :metric, :correlation, :pValue, :sampleSize, :lagOrder, :direction)",
        toParams(output));
  }

  public List<CorrelationOutput> findBySymbolAndMetricAndTimeBetween(
      String symbol, String metric, Instant from, Instant to) {
    return findBySymbolAndMetricAndTimeBetween(symbol, metric, from, to, queryLimits.defaultLimit(), 0);
  }

  public List<CorrelationOutput> findBySymbolAndMetricAndTimeBetween(
      String symbol, String metric, Instant from, Instant to, int limit, Integer offset) {
    var params = new MapSqlParameterSource()
        .addValue("symbol", symbol)
        .addValue("metric", metric)
        .addValue("from", from)
        .addValue("to", to);
    return BoundedRangeQuery.execute(
        jdbc, SELECT_SQL, params, rowMapper, limit, offset, log, "CorrelationOutput.findBySymbolAndMetricAndTimeBetween");
  }

  private MapSqlParameterSource toParams(CorrelationOutput output) {
    return new MapSqlParameterSource()
        .addValue("time", output.time())
        .addValue("symbol", output.symbol())
        .addValue("metric", output.metric())
        .addValue("correlation", output.correlation())
        .addValue("pValue", output.pValue())
        .addValue("sampleSize", output.sampleSize())
        .addValue("lagOrder", output.lagOrder())
        .addValue("direction", output.direction());
  }
}
