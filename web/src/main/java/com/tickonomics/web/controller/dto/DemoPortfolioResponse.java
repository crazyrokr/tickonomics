package com.tickonomics.web.controller.dto;

import java.math.BigDecimal;

/** Typed {@code /api/v1/demo/portfolio} response; mirrors the {@code DemoPortfolio} contract schema. */
public record DemoPortfolioResponse(
    BigDecimal balance,
    BigDecimal initialBalance,
    BigDecimal realizedPnl,
    BigDecimal unrealizedPnl,
    BigDecimal totalPnl,
    int openPositions,
    int totalTrades,
    double winRate,
    boolean enabled) {
}
