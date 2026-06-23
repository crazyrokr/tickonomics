package com.tickonomics.web.controller.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Typed paper-trade row; mirrors the {@code DemoTrade} contract schema. */
public record DemoTradeResponse(
    Long id,
    String symbol,
    String direction,
    BigDecimal quantity,
    BigDecimal fillPrice,
    BigDecimal commission,
    BigDecimal slippage,
    BigDecimal realizedPnl,
    Instant executedAt,
    String tradeType) {
}
