package com.tickonomics.computation.equity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquityStrategyRegistryTest {

  private EquityStrategyRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new EquityStrategyRegistry();
  }

  @Nested
  class AllStrategies {

    @Test
    void givenRegistry_whenAll_then25Strategies() {
      Collection<BaseEquityStrategy> strategies = registry.all();

      assertEquals(25, strategies.size());
    }

    @Test
    void givenAllStrategies_whenCategory_thenEquity() {
      for (BaseEquityStrategy strategy : registry.all()) {
        assertEquals("EQUITY", strategy.category());
      }
    }

    @Test
    void givenAllStrategies_whenIsActive_thenTrue() {
      for (BaseEquityStrategy strategy : registry.all()) {
        assertTrue(strategy.isActive());
      }
    }
  }

  @Nested
  class GetByName {

    @Test
    void givenKnownName_whenGet_thenPresent() {
      assertTrue(registry.get("RSI_OSCILLATOR").isPresent());
      assertTrue(registry.get("MACD_DIVERGENCE").isPresent());
      assertTrue(registry.get("BOLLINGER_WIDTH").isPresent());
      assertTrue(registry.get("ICHIMOKU_CLOUD").isPresent());
    }

    @Test
    void givenUnknownName_whenGet_thenEmpty() {
      assertTrue(registry.get("NONEXISTENT_STRATEGY").isEmpty());
    }

    @Test
    void givenNullName_whenGet_thenEmpty() {
      assertTrue(registry.get(null).isEmpty());
    }
  }

  @Nested
  class RegisterCustom {

    @Test
    void givenCustomStrategy_whenRegister_thenRetrievable() {
      BaseEquityStrategy custom = new RSIOscillatorStrategy();

      registry.register(custom);

      assertTrue(registry.get("RSI_OSCILLATOR").isPresent());
    }
  }
}
