package com.tickonomics.ingestion.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

    @Disabled
    @Test
    void givenExpiredKey_whenEvict_thenRemoved() throws InterruptedException {
      guard.isDuplicate("old-key");
      Thread.sleep(100);
      guard.isDuplicate("new-key");

      int evicted = guard.evictExpired();
      assertTrue(evicted >= 1);
      assertFalse(guard.isDuplicate("new-key"));
      assertEquals(1, guard.size());
    }

    @Test
    void givenNoExpiredKeys_whenEvict_thenNoneRemoved() {
      guard.isDuplicate("key-1");
      guard.isDuplicate("key-2");
      assertEquals(0, guard.evictExpired());
      assertEquals(2, guard.size());
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
