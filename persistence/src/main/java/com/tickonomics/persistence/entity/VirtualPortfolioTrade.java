package com.tickonomics.persistence.entity;

import java.time.Instant;
import java.util.Set;

public record VirtualPortfolioTrade(
    Long id,
    Instant executedAt,
    String symbol,
    String direction,
    double quantity,
    double fillPrice,
    double commission,
    double slippage,
    Double realizedPnl,
    Long positionId,
    Long signalId,
    String tradeType) {

  private static final Set<String> VALID_DIRECTIONS = Set.of("BUY", "SELL");

  public VirtualPortfolioTrade {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
    if (direction == null || !VALID_DIRECTIONS.contains(direction)) {
      throw new IllegalArgumentException("direction must be BUY or SELL");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (fillPrice <= 0) {
      throw new IllegalArgumentException("fillPrice must be positive");
    }
    if (commission < 0) {
      throw new IllegalArgumentException("commission must not be negative");
    }
    if (slippage < 0) {
      throw new IllegalArgumentException("slippage must not be negative");
    }
  }
}
