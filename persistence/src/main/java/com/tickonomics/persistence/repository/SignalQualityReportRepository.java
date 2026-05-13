package com.tickonomics.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SignalQualityReportRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public SignalQualityReportRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long save(LocalDate reportDate, int totalSignals, int actionableSignals,
      Double hitRate1d, Double hitRate5d, Double hitRate10d, Double hitRate20d,
      Double falsePositiveRate, Double avgReturnPerSignal, Double portfolioPnl,
      Double portfolioSharpe, Double vsSpyReturn, String verificationProgress) {
    var keyHolder = new GeneratedKeyHolder();
    var params = new MapSqlParameterSource()
        .addValue("reportDate", reportDate)
        .addValue("totalSignals", totalSignals)
        .addValue("actionableSignals", actionableSignals)
        .addValue("hitRate1d", hitRate1d)
        .addValue("hitRate5d", hitRate5d)
        .addValue("hitRate10d", hitRate10d)
        .addValue("hitRate20d", hitRate20d)
        .addValue("falsePositiveRate", falsePositiveRate)
        .addValue("avgReturnPerSignal", avgReturnPerSignal)
        .addValue("portfolioPnl", portfolioPnl)
        .addValue("portfolioSharpe", portfolioSharpe)
        .addValue("vsSpyReturn", vsSpyReturn)
        .addValue("verificationProgress", verificationProgress);
    jdbc.update(
        "INSERT INTO signal_quality_reports (report_date, total_signals, actionable_signals, "
            + "hit_rate_1d, hit_rate_5d, hit_rate_10d, hit_rate_20d, false_positive_rate, "
            + "avg_return_per_signal, portfolio_pnl, portfolio_sharpe, vs_spy_return, "
            + "verification_progress) "
            + "VALUES (:reportDate, :totalSignals, :actionableSignals, :hitRate1d, :hitRate5d, "
            + ":hitRate10d, :hitRate20d, :falsePositiveRate, :avgReturnPerSignal, :portfolioPnl, "
            + ":portfolioSharpe, :vsSpyReturn, :verificationProgress::jsonb)",
        params,
        keyHolder,
        new String[]{"id"});
    return keyHolder.getKey().longValue();
  }

  public Optional<Map<String, Object>> findLatest() {
    List<Map<String, Object>> results = jdbc.queryForList(
        "SELECT id, report_date, total_signals, actionable_signals, hit_rate_1d, hit_rate_5d, "
            + "hit_rate_10d, hit_rate_20d, false_positive_rate, avg_return_per_signal, "
            + "portfolio_pnl, portfolio_sharpe, vs_spy_return, verification_progress "
            + "FROM signal_quality_reports ORDER BY report_date DESC LIMIT 1",
        Map.of());
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  public List<Map<String, Object>> findByDateBetween(LocalDate from, LocalDate to) {
    return jdbc.queryForList(
        "SELECT id, report_date, total_signals, actionable_signals, hit_rate_1d, hit_rate_5d, "
            + "hit_rate_10d, hit_rate_20d, false_positive_rate, avg_return_per_signal, "
            + "portfolio_pnl, portfolio_sharpe, vs_spy_return, verification_progress "
            + "FROM signal_quality_reports WHERE report_date BETWEEN :from AND :to "
            + "ORDER BY report_date DESC",
        Map.of("from", from, "to", to));
  }
}
