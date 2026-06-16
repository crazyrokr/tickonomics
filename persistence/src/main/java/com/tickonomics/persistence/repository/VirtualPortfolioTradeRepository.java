package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class VirtualPortfolioTradeRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public VirtualPortfolioTradeRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long save(VirtualPortfolioTrade trade) {
    var keyHolder = new GeneratedKeyHolder();
    jdbc.update(
        "INSERT INTO virtual_portfolio_trades (executed_at, symbol, direction, "
            + "quantity, quantity_num, fill_price, fill_price_num, "
            + "commission, commission_num, slippage, slippage_num, "
            + "realized_pnl, realized_pnl_num, "
            + "position_id, signal_id, trade_type) "
            + "VALUES (:executedAt, :symbol, :direction, "
            + ":quantity, :quantity, :fillPrice, :fillPrice, "
            + ":commission, :commission, :slippage, :slippage, "
            + ":realizedPnl, :realizedPnl, "
            + ":positionId, :signalId, :tradeType)",
        toParams(trade),
        keyHolder,
        new String[]{"id"});
    return keyHolder.getKey().longValue();
  }

  public List<VirtualPortfolioTrade> findByPositionId(long positionId) {
    return jdbc.query(
        "SELECT id, executed_at, symbol, direction, "
            + "quantity_num, fill_price_num, commission_num, slippage_num, "
            + "realized_pnl_num, position_id, signal_id, trade_type "
            + "FROM virtual_portfolio_trades WHERE position_id = :positionId ORDER BY executed_at",
        Map.of("positionId", positionId),
        rowMapper());
  }

  public List<VirtualPortfolioTrade> findLatest(int limit, int offset) {
    return jdbc.query(
        "SELECT id, executed_at, symbol, direction, "
            + "quantity_num, fill_price_num, commission_num, slippage_num, "
            + "realized_pnl_num, position_id, signal_id, trade_type "
            + "FROM virtual_portfolio_trades ORDER BY executed_at DESC LIMIT :limit OFFSET :offset",
        Map.of("limit", limit, "offset", offset),
        rowMapper());
  }

  public int countByTradeType(String tradeType) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM virtual_portfolio_trades WHERE trade_type = :tradeType",
        Map.of("tradeType", tradeType),
        Integer.class);
    return count != null ? count : 0;
  }

  private org.springframework.jdbc.core.RowMapper<VirtualPortfolioTrade> rowMapper() {
    return (rs, rowNum) -> new VirtualPortfolioTrade(
        rs.getLong("id"),
        rs.getTimestamp("executed_at").toInstant(),
        rs.getString("symbol"),
        rs.getString("direction"),
        rs.getBigDecimal("quantity_num"),
        rs.getBigDecimal("fill_price_num"),
        rs.getBigDecimal("commission_num"),
        rs.getBigDecimal("slippage_num"),
        rs.getObject("realized_pnl_num") != null ? rs.getBigDecimal("realized_pnl_num") : null,
        rs.getObject("position_id") != null ? rs.getLong("position_id") : null,
        rs.getObject("signal_id") != null ? rs.getLong("signal_id") : null,
        rs.getString("trade_type"));
  }

  private MapSqlParameterSource toParams(VirtualPortfolioTrade trade) {
    return new MapSqlParameterSource()
        .addValue("executedAt", trade.executedAt() != null ? trade.executedAt() : Instant.now())
        .addValue("symbol", trade.symbol())
        .addValue("direction", trade.direction())
        .addValue("quantity", trade.quantity())
        .addValue("fillPrice", trade.fillPrice())
        .addValue("commission", trade.commission())
        .addValue("slippage", trade.slippage())
        .addValue("realizedPnl", trade.realizedPnl())
        .addValue("positionId", trade.positionId())
        .addValue("signalId", trade.signalId())
        .addValue("tradeType", trade.tradeType() != null ? trade.tradeType() : "PAPER");
  }
}
