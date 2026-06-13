package com.tickonomics.persistence.entity;

import java.time.Instant;

public record VolatilityForecast(
    Instant time,
    String symbol,
    String model,
    int horizonDays,
    double forecastVol,
    Double realizedVol,
    Double maeVsBaseline,
    Integer nObservations,
    String parameters,
    String gitSha,
    Instant createdAt) {
  public VolatilityForecast {
    if (time == null) {
      throw new NullPointerException("time must not be null");
    }
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
  }
}
