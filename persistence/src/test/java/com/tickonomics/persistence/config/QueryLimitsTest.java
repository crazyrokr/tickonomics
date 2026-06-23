package com.tickonomics.persistence.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QueryLimitsTest {

  @Nested
  class DefaultLimit {

    @Test
    void givenPositiveLimit_whenConstructed_thenPreserved() {
      // Given a configured positive limit
      // When bound to QueryLimits
      QueryLimits limits = new QueryLimits(5000);
      // Then it is preserved
      assertEquals(5000, limits.defaultLimit());
    }

    @Test
    void givenZeroLimit_whenConstructed_thenFallsBackToDefault() {
      // Given an invalid (zero) limit
      // When bound
      QueryLimits limits = new QueryLimits(0);
      // Then the fallback default is applied so queries are never unbounded
      assertEquals(10_000, limits.defaultLimit());
    }

    @Test
    void givenNegativeLimit_whenConstructed_thenFallsBackToDefault() {
      QueryLimits limits = new QueryLimits(-1);
      assertEquals(10_000, limits.defaultLimit());
    }
  }
}
