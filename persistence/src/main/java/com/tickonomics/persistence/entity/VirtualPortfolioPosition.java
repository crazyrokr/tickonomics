package com.tickonomics.persistence.entity;

import java.time.Instant;

public record VirtualPortfolioPosition(
    Long id,
    Instant openedAt,
    String symbol,
    String direction,
    double quantity,
    double entryPrice,
    Double currentPrice,
    Double unrealizedPnl,
    Double stopLossPrice,
    Double takeProfitPrice,
    Long signalId,
    Instant closedAt) {

  public VirtualPortfolioPosition {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
    if (direction == null || direction.isBlank()) {
      throw new IllegalArgumentException("direction must not be blank");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (entryPrice <= 0) {
      throw new IllegalArgumentException("entryPrice must be positive");
    }
  }
}
