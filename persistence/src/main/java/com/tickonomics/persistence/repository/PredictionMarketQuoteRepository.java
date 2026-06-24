package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.IdempotentRow;
import com.tickonomics.persistence.entity.PredictionMarketQuote;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class PredictionMarketQuoteRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String SELECT_COLUMNS =
      "time, market_id, question, outcome_yes_price, volume, liquidity, source";

  private static final String INSERT_SQL =
      "INSERT INTO prediction_market_quotes (time, market_id, question, outcome_yes_price, volume, liquidity, source) "
          + "VALUES (:time, :marketId, :question, :outcomeYesPrice, :volume, :liquidity, :source)";

  private static final String IDEMPOTENT_INSERT_SQL =
      "INSERT INTO prediction_market_quotes (time, market_id, question, outcome_yes_price, volume, liquidity, source, idempotency_key) "
          + "VALUES (:time, :marketId, :question, :outcomeYesPrice, :volume, :liquidity, :source, :idempotencyKey) "
          + "ON CONFLICT (time, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING";

  public PredictionMarketQuoteRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(PredictionMarketQuote quote) {
    jdbc.update(INSERT_SQL, toParams(quote));
  }

  public void saveAll(List<PredictionMarketQuote> quotes) {
    jdbc.batchUpdate(
        INSERT_SQL,
        quotes.stream().map(this::toParams).toArray(SqlParameterSource[]::new));
  }

  /**
   * Batch insert with durable idempotency-key deduplication. Returns the per-row JDBC affected-row
   * counts ({@code 0} where a duplicate key was suppressed by {@code ON CONFLICT DO NOTHING}).
   */
  public int[] saveAllIdempotent(List<IdempotentRow<PredictionMarketQuote>> rows) {
    if (rows == null || rows.isEmpty()) {
      return new int[0];
    }
    return jdbc.batchUpdate(
        IDEMPOTENT_INSERT_SQL,
        rows.stream().map(this::toIdempotentParams).toArray(SqlParameterSource[]::new));
  }

  public List<PredictionMarketQuote> findRecent(Instant from, int limit) {
    return jdbc.query(
        "SELECT " + SELECT_COLUMNS + " FROM prediction_market_quotes "
            + "WHERE time >= :from ORDER BY time DESC LIMIT :limit",
        Map.of("from", from, "limit", limit),
        (rs, rowNum) -> new PredictionMarketQuote(
            rs.getTimestamp("time").toInstant(),
            rs.getString("market_id"),
            rs.getString("question"),
            rs.getDouble("outcome_yes_price"),
            rs.getDouble("volume"),
            rs.getDouble("liquidity"),
            rs.getString("source")));
  }

  private MapSqlParameterSource toParams(PredictionMarketQuote quote) {
    return new MapSqlParameterSource()
        .addValue("time", quote.time())
        .addValue("marketId", quote.marketId())
        .addValue("question", quote.question())
        .addValue("outcomeYesPrice", quote.outcomeYesPrice())
        .addValue("volume", quote.volume())
        .addValue("liquidity", quote.liquidity())
        .addValue("source", quote.source());
  }

  private MapSqlParameterSource toIdempotentParams(IdempotentRow<PredictionMarketQuote> row) {
    return toParams(row.row()).addValue("idempotencyKey", row.idempotencyKey());
  }
}
