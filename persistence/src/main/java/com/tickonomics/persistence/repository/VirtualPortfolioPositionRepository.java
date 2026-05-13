package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.VirtualPortfolioPosition;
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
        "INSERT INTO virtual_portfolio_positions (opened_at, symbol, direction, quantity, entry_price, "
            + "current_price, unrealized_pnl, stop_loss_price, take_profit_price, signal_id, closed_at) "
            + "VALUES (:openedAt, :symbol, :direction, :quantity, :entryPrice, :currentPrice, "
            + ":unrealizedPnl, :stopLossPrice, :takeProfitPrice, :signalId, :closedAt)",
        toParams(position),
        keyHolder,
        new String[]{"id"});
    return keyHolder.getKey().longValue();
  }

  public List<VirtualPortfolioPosition> findOpenPositions() {
    return jdbc.query(
        "SELECT id, opened_at, symbol, direction, quantity, entry_price, current_price, "
            + "unrealized_pnl, stop_loss_price, take_profit_price, signal_id, closed_at "
            + "FROM virtual_portfolio_positions WHERE closed_at IS NULL ORDER BY opened_at",
        Map.of(),
        rowMapper());
  }

  public Optional<VirtualPortfolioPosition> findOpenBySymbol(String symbol) {
    List<VirtualPortfolioPosition> results = jdbc.query(
        "SELECT id, opened_at, symbol, direction, quantity, entry_price, current_price, "
            + "unrealized_pnl, stop_loss_price, take_profit_price, signal_id, closed_at "
            + "FROM virtual_portfolio_positions WHERE symbol = :symbol AND closed_at IS NULL",
        Map.of("symbol", symbol),
        rowMapper());
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  public void updateMarkToMarket(long id, double currentPrice, double unrealizedPnl) {
    jdbc.update(
        "UPDATE virtual_portfolio_positions SET current_price = :currentPrice, "
            + "unrealized_pnl = :unrealizedPnl WHERE id = :id",
        Map.of("id", id, "currentPrice", currentPrice, "unrealizedPnl", unrealizedPnl));
  }

  public void close(long id, Instant closedAt, double exitPrice) {
    jdbc.update(
        "UPDATE virtual_portfolio_positions SET closed_at = :closedAt, current_price = :exitPrice "
            + "WHERE id = :id",
        Map.of("id", id, "closedAt", closedAt, "exitPrice", exitPrice));
  }

  private VirtualPortfolioPosition mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new VirtualPortfolioPosition(
        rs.getLong("id"),
        rs.getTimestamp("opened_at").toInstant(),
        rs.getString("symbol"),
        rs.getString("direction"),
        rs.getDouble("quantity"),
        rs.getDouble("entry_price"),
        rs.getObject("current_price") != null ? rs.getDouble("current_price") : null,
        rs.getObject("unrealized_pnl") != null ? rs.getDouble("unrealized_pnl") : null,
        rs.getObject("stop_loss_price") != null ? rs.getDouble("stop_loss_price") : null,
        rs.getObject("take_profit_price") != null ? rs.getDouble("take_profit_price") : null,
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
