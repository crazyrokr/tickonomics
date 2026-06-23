package com.tickonomics.web.controller.dto;

/** Typed leverage-rotation evaluation response. */
public record LeverageRotationResponse(
    String signal,
    double benchmarkPrice,
    double sma200,
    double deviation,
    int closedTrades) {
}
