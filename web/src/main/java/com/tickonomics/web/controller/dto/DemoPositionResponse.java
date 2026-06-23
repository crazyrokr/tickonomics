package com.tickonomics.web.controller.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Typed open-position row; mirrors the {@code DemoPosition} contract schema. */
public record DemoPositionResponse(
    Long id,
    String symbol,
    String direction,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal currentPrice,
    BigDecimal unrealizedPnl,
    BigDecimal stopLossPrice,
    BigDecimal takeProfitPrice,
    Instant openedAt) {
}
