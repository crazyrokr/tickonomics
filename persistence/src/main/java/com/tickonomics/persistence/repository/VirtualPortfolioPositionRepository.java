package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class VirtualPortfolioPositionRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public VirtualPortfolioPositionRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long save(VirtualPortfolioPosition position) {
    var keyHolder = new GeneratedKeyHolder();
    jdbc.update(
        "INSERT INTO virtual_portfolio_positions (opened_at, symbol, direction, "
            + "quantity, quantity_num, entry_price, entry_price_num, "
            + "current_price, current_price_num, unrealized_pnl, unrealized_pnl_num, "
            + "stop_loss_price, stop_loss_price_num, take_profit_price, take_profit_price_num, "
            + "signal_id, closed_at) "
            + "VALUES (:openedAt, :symbol, :direction, "
            + ":quantity, :quantity, :entryPrice, :entryPrice, "
            + ":currentPrice, :currentPrice, :unrealizedPnl, :unrealizedPnl, "
            + ":stopLossPrice, :stopLossPrice, :takeProfitPrice, :takeProfitPrice, "
            + ":signalId, :closedAt)",
        toParams(position),
        keyHolder,
        new String[]{"id"});
    return KeyHolderUtils.extractGeneratedLong(keyHolder);
  }

  public List<VirtualPortfolioPosition> findOpenPositions() {
    return jdbc.query(
        "SELECT id, opened_at, symbol, direction, "
            + "quantity_num, entry_price_num, current_price_num, "
            + "unrealized_pnl_num, stop_loss_price_num, take_profit_price_num, "
            + "signal_id, closed_at "
            + "FROM virtual_portfolio_positions WHERE closed_at IS NULL ORDER BY opened_at",
        Map.of(),
        rowMapper());
  }

  public Optional<VirtualPortfolioPosition> findOpenBySymbol(String symbol) {
    List<VirtualPortfolioPosition> results = jdbc.query(
        "SELECT id, opened_at, symbol, direction, "
            + "quantity_num, entry_price_num, current_price_num, "
            + "unrealized_pnl_num, stop_loss_price_num, take_profit_price_num, "
            + "signal_id, closed_at "
            + "FROM virtual_portfolio_positions WHERE symbol = :symbol AND closed_at IS NULL",
        Map.of("symbol", symbol),
        rowMapper());
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  public void updateMarkToMarket(long id, BigDecimal currentPrice, BigDecimal unrealizedPnl) {
    jdbc.update(
        "UPDATE virtual_portfolio_positions SET current_price = :currentPrice, "
            + "current_price_num = :currentPrice, "
            + "unrealized_pnl = :unrealizedPnl, "
            + "unrealized_pnl_num = :unrealizedPnl "
            + "WHERE id = :id",
        Map.of("id", id, "currentPrice", currentPrice, "unrealizedPnl", unrealizedPnl));
  }

  public void close(long id, Instant closedAt, BigDecimal exitPrice) {
    jdbc.update(
        "UPDATE virtual_portfolio_positions SET closed_at = :closedAt, "
            + "current_price = :exitPrice, current_price_num = :exitPrice "
            + "WHERE id = :id",
        Map.of("id", id, "closedAt", closedAt, "exitPrice", exitPrice));
  }

  private VirtualPortfolioPosition mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new VirtualPortfolioPosition(
        rs.getLong("id"),
        rs.getTimestamp("opened_at").toInstant(),
        rs.getString("symbol"),
        rs.getString("direction"),
        rs.getBigDecimal("quantity_num"),
        rs.getBigDecimal("entry_price_num"),
        rs.getObject("current_price_num") != null ? rs.getBigDecimal("current_price_num") : null,
        rs.getObject("unrealized_pnl_num") != null ? rs.getBigDecimal("unrealized_pnl_num") : null,
        rs.getObject("stop_loss_price_num") != null ? rs.getBigDecimal("stop_loss_price_num") : null,
        rs.getObject("take_profit_price_num") != null ? rs.getBigDecimal("take_profit_price_num") : null,
        rs.getObject("signal_id") != null ? rs.getLong("signal_id") : null,
        rs.getTimestamp("closed_at") != null ? rs.getTimestamp("closed_at").toInstant() : null);
  }

  private org.springframework.jdbc.core.RowMapper<VirtualPortfolioPosition> rowMapper() {
    return (rs, rowNum) -> mapRow(rs, rowNum);
  }

  private MapSqlParameterSource toParams(VirtualPortfolioPosition position) {
    return new MapSqlParameterSource()
        .addValue("openedAt", position.openedAt() != null ? position.openedAt() : Instant.now())
        .addValue("symbol", position.symbol())
        .addValue("direction", position.direction())
        .addValue("quantity", position.quantity())
        .addValue("entryPrice", position.entryPrice())
        .addValue("currentPrice", position.currentPrice())
        .addValue("unrealizedPnl", position.unrealizedPnl())
        .addValue("stopLossPrice", position.stopLossPrice())
        .addValue("takeProfitPrice", position.takeProfitPrice())
        .addValue("signalId", position.signalId())
        .addValue("closedAt", position.closedAt());
  }
}
