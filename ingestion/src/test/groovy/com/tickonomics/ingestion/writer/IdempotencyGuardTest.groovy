package com.tickonomics.ingestion.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IdempotencyGuardTest {

  private IdempotencyGuard guard;

  @BeforeEach
  void setUp() {
    guard = new IdempotencyGuard(1000);
  }

  @Nested
  class IsDuplicate {
    @Test
    void givenNewKey_whenCheck_thenNotDuplicate() {
      assertFalse(guard.isDuplicate("key-1"));
    }

    @Test
    void givenSeenKey_whenCheck_thenDuplicate() {
      guard.isDuplicate("key-1");
      assertTrue(guard.isDuplicate("key-1"));
    }

    @Test
    void givenNullKey_whenCheck_thenNotDuplicate() {
      assertFalse(guard.isDuplicate(null));
    }

    @Test
    void givenBlankKey_whenCheck_thenNotDuplicate() {
      assertFalse(guard.isDuplicate("  "));
    }

    @Test
    void givenDifferentKeys_whenCheck_thenNotDuplicate() {
      guard.isDuplicate("key-1");
      assertFalse(guard.isDuplicate("key-2"));
    }
  }

  @Nested
  class EvictExpired {

    @Test
    void givenExpiredKey_whenEvict_thenRemoved() {
      guard = new IdempotencyGuard(100);
      guard.isDuplicate("old-key");
      guard.seenKeys.put("old-key", System.currentTimeMillis() - 200);

      int evicted = guard.evictExpired();
      assertEquals(1, evicted);
      assertFalse(guard.isDuplicate("old-key"));
    }

    @Test
    void givenNoExpiredKeys_whenEvict_thenNoneRemoved() {
      guard.isDuplicate("key-1");
      guard.isDuplicate("key-2");
      assertEquals(0, guard.evictExpired());
      assertEquals(2, guard.size());
    }

    @Test
    void givenMixedKeys_whenEvict_thenOnlyExpiredRemoved() {
      guard = new IdempotencyGuard(100);
      guard.isDuplicate("recent-key");
      guard.seenKeys.put("expired-key", System.currentTimeMillis() - 200);

      int evicted = guard.evictExpired();
      assertEquals(1, evicted);
      assertEquals(1, guard.size());
      assertTrue(guard.isDuplicate("recent-key"));
    }
  }

  @Nested
  class BuildKey {
    @Test
    void givenInputs_whenBuild_thenConsistentFormat() {
      var time = Instant.parse("2026-01-01T00:00:00Z");
      String key = guard.buildKey("FRED", "SOFR", time);
      assertEquals("FRED:SOFR:2026-01-01T00:00:00Z", key);
    }
  }
}
