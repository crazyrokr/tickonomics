package com.tickonomics.web.controller.dto;

import java.math.BigDecimal;

/** Typed close-position result; mirrors the {@code DemoCloseResult} contract schema. */
public record DemoCloseResultResponse(
    Long tradeId,
    Long positionId,
    BigDecimal realizedPnl,
    BigDecimal fillPrice) {
}
