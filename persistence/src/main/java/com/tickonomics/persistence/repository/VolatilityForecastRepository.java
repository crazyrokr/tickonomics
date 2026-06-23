package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.config.QueryLimits;
import com.tickonomics.persistence.entity.VolatilityForecast;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VolatilityForecastRepository {

  private static final Logger log = LoggerFactory.getLogger(VolatilityForecastRepository.class);

  private final NamedParameterJdbcTemplate jdbc;
  private final QueryLimits queryLimits;

  public VolatilityForecastRepository(NamedParameterJdbcTemplate jdbc, QueryLimits queryLimits) {
    this.jdbc = jdbc;
    this.queryLimits = queryLimits;
  }

  public void save(VolatilityForecast forecast) {
    jdbc.update(
        "INSERT INTO volatility_forecasts (time, symbol, model, horizon_days, forecast_vol, "
            + "realized_vol, mae_vs_baseline, n_observations, parameters, git_sha, created_at) "
            + "VALUES (:time, :symbol, :model, :horizonDays, :forecastVol, "
            + ":realizedVol, :maeVsBaseline, :nObservations, :parameters::jsonb, :gitSha, :createdAt)",
        toParams(forecast));
  }

  public List<VolatilityForecast> findBySymbolAndTimeBetween(String symbol, Instant from, Instant to) {
    return findBySymbolAndTimeBetween(symbol, from, to, queryLimits.defaultLimit(), 0);
  }

  public List<VolatilityForecast> findBySymbolAndTimeBetween(
      String symbol, Instant from, Instant to, int limit, Integer offset) {
    var params = new MapSqlParameterSource()
        .addValue("symbol", symbol)
        .addValue("from", from)
        .addValue("to", to);
    String sql = "SELECT time, symbol, model, horizon_days, forecast_vol, realized_vol, mae_vs_baseline, "
        + "n_observations, parameters, git_sha, created_at "
        + "FROM volatility_forecasts WHERE symbol = :symbol AND time BETWEEN :from AND :to "
        + "ORDER BY time, horizon_days";
    return BoundedRangeQuery.execute(jdbc, sql, params, this::mapRow, limit, offset, log, "VolatilityForecast.findBySymbolAndTimeBetween");
  }

  public List<VolatilityForecast> findLatestBySymbol(String symbol, int limit) {
    return jdbc.query(
        "SELECT time, symbol, model, horizon_days, forecast_vol, realized_vol, mae_vs_baseline, "
            + "n_observations, parameters, git_sha, created_at "
            + "FROM volatility_forecasts WHERE symbol = :symbol "
            + "ORDER BY time DESC, horizon_days LIMIT :limit",
        Map.of("symbol", symbol, "limit", limit),
        this::mapRow);
  }

  public void updateRealizedVol(Instant time, String symbol, int horizonDays, double realizedVol) {
    jdbc.update(
        "UPDATE volatility_forecasts SET realized_vol = :realizedVol "
            + "WHERE time = :time AND symbol = :symbol AND horizon_days = :horizonDays "
            + "AND realized_vol IS NULL",
        Map.of("time", time, "symbol", symbol, "horizonDays", horizonDays, "realizedVol", realizedVol));
  }

  private VolatilityForecast mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new VolatilityForecast(
        rs.getTimestamp("time").toInstant(),
        rs.getString("symbol"),
        rs.getString("model"),
        rs.getInt("horizon_days"),
        rs.getDouble("forecast_vol"),
        rs.getObject("realized_vol") != null ? rs.getDouble("realized_vol") : null,
        rs.getObject("mae_vs_baseline") != null ? rs.getDouble("mae_vs_baseline") : null,
        rs.getObject("n_observations") != null ? rs.getInt("n_observations") : null,
        rs.getString("parameters"),
        rs.getString("git_sha"),
        rs.getTimestamp("created_at").toInstant());
  }

  private MapSqlParameterSource toParams(VolatilityForecast f) {
    return new MapSqlParameterSource()
        .addValue("time", f.time())
        .addValue("symbol", f.symbol())
        .addValue("model", f.model())
        .addValue("horizonDays", f.horizonDays())
        .addValue("forecastVol", f.forecastVol())
        .addValue("realizedVol", f.realizedVol())
        .addValue("maeVsBaseline", f.maeVsBaseline())
        .addValue("nObservations", f.nObservations())
        .addValue("parameters", f.parameters())
        .addValue("gitSha", f.gitSha())
        .addValue("createdAt", f.createdAt());
  }
}
