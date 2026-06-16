package com.tickonomics.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;

public record VirtualPortfolioPosition(
    Long id,
    Instant openedAt,
    String symbol,
    String direction,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal currentPrice,
    BigDecimal unrealizedPnl,
    BigDecimal stopLossPrice,
    BigDecimal takeProfitPrice,
    Long signalId,
    Instant closedAt) {

  public VirtualPortfolioPosition {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
    if (direction == null || direction.isBlank()) {
      throw new IllegalArgumentException("direction must not be blank");
    }
    if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("entryPrice must be positive");
    }
  }
}
