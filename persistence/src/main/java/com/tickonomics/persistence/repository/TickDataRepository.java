package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.config.QueryLimits;
import com.tickonomics.persistence.entity.IdempotentRow;
import com.tickonomics.persistence.entity.TickData;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class TickDataRepository {

  private static final Logger log = LoggerFactory.getLogger(TickDataRepository.class);

  private static final String INSERT_SQL =
      "INSERT INTO tick_data (time, symbol, price, price_num, volume, conditions) "
          + "VALUES (:time, :symbol, :price, :price, :volume, :conditions)";

  private static final String IDEMPOTENT_INSERT_SQL =
      "INSERT INTO tick_data (time, symbol, price, price_num, volume, conditions, idempotency_key) "
          + "VALUES (:time, :symbol, :price, :price, :volume, :conditions, :idempotencyKey) "
          + "ON CONFLICT (time, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING";

  private final NamedParameterJdbcTemplate jdbc;
  private final QueryLimits queryLimits;

  private final RowMapper<TickData> rowMapper = (rs, rowNum) -> new TickData(
      rs.getTimestamp("time").toInstant(),
      rs.getString("symbol"),
      rs.getBigDecimal("price_num"),
      rs.getLong("volume"),
      (int[]) rs.getArray("conditions").getArray());

  public TickDataRepository(NamedParameterJdbcTemplate jdbc, QueryLimits queryLimits) {
    this.jdbc = jdbc;
    this.queryLimits = queryLimits;
  }

  public void save(TickData tick) {
    jdbc.update(INSERT_SQL, toParams(tick));
  }

  public void saveAll(List<TickData> ticks) {
    jdbc.batchUpdate(
        INSERT_SQL,
        ticks
            .stream()
            .map(this::toParams)
            .toArray(SqlParameterSource[]::new));
  }

  /**
   * Batch insert with durable idempotency-key deduplication. Returns the per-row JDBC affected-row
   * counts ({@code 0} where a duplicate key was suppressed by {@code ON CONFLICT DO NOTHING}).
   */
  public int[] saveAllIdempotent(List<IdempotentRow<TickData>> rows) {
    if (rows == null || rows.isEmpty()) {
      return new int[0];
    }
    return jdbc.batchUpdate(
        IDEMPOTENT_INSERT_SQL,
        rows
            .stream()
            .map(this::toIdempotentParams)
            .toArray(SqlParameterSource[]::new));
  }

  public List<TickData> findBySymbolAndTimeBetween(String symbol, Instant from, Instant to) {
    return findBySymbolAndTimeBetween(symbol, from, to, queryLimits.defaultLimit(), 0);
  }

  public List<TickData> findBySymbolAndTimeBetween(
      String symbol, Instant from, Instant to, int limit, Integer offset) {
    var params = new MapSqlParameterSource()
        .addValue("symbol", symbol)
        .addValue("from", from)
        .addValue("to", to);
    String sql = "SELECT time, symbol, price_num, volume, conditions FROM tick_data WHERE symbol = :symbol "
        + "AND time BETWEEN :from AND :to ORDER BY time";
    return BoundedRangeQuery.execute(jdbc, sql, params, rowMapper, limit, offset, log, "TickData.findBySymbolAndTimeBetween");
  }

  public List<TickData> findLatestBySymbol(String symbol, int limit) {
    return jdbc.query(
        "SELECT time, symbol, price_num, volume, conditions FROM tick_data WHERE symbol = :symbol ORDER BY time DESC "
            + "LIMIT :limit",
        Map.of("symbol", symbol, "limit", limit),
        rowMapper);
  }

  private MapSqlParameterSource toParams(TickData tick) {
    return new MapSqlParameterSource()
        .addValue("time", tick.time())
        .addValue("symbol", tick.symbol())
        .addValue("price", tick.price())
        .addValue("volume", tick.volume())
        .addValue("conditions", tick.conditions() != null ? tick.conditions() : new int[]{});
  }

  private MapSqlParameterSource toIdempotentParams(IdempotentRow<TickData> row) {
    return toParams(row.row()).addValue("idempotencyKey", row.idempotencyKey());
  }
}
