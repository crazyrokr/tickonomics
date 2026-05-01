package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.CorrelationOutput;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CorrelationOutputRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public CorrelationOutputRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(CorrelationOutput output) {
    jdbc.update(
        "INSERT INTO correlation_outputs (time, symbol, metric, correlation, p_value, sample_size, lag_order, "
            + "direction) "
            + "VALUES (:time, :symbol, :metric, :correlation, :pValue, :sampleSize, :lagOrder, :direction)",
        toParams(output));
  }

  public List<CorrelationOutput> findBySymbolAndMetricAndTimeBetween(
      String symbol,
      String metric,
      Instant from,
      Instant to) {
    return jdbc.query(
        "SELECT time, symbol, metric, correlation, p_value, sample_size, lag_order, direction "
            + "FROM correlation_outputs WHERE symbol = :symbol AND metric = :metric AND time BETWEEN :from AND :to "
            + "ORDER BY time",
        Map.of("symbol", symbol, "metric", metric, "from", from, "to", to),
        (rs, rowNum) -> new CorrelationOutput(
            rs
                .getTimestamp("time")
                .toInstant(),
            rs.getString("symbol"),
            rs.getString("metric"),
            rs.getDouble("correlation"),
            rs.getDouble("p_value"),
            rs.getInt("sample_size"),
            rs.getInt("lag_order"),
            rs.getString("direction")));
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
