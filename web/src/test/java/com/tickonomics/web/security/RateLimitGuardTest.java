package com.tickonomics.web.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RateLimitGuardTest {

  /** Mutable clock so refill timing is deterministic without sleeping. */
  private static final class MutableClock implements java.util.function.LongSupplier {
    long nanos = 0L;

    @Override
    public long getAsLong() {
      return nanos;
    }
  }

  @Nested
  class TryAcquire {

    @Test
    void givenWithinCapacity_whenTryAcquire_thenAllAllowedThenDenied() {
      // Given a bucket of capacity 2
      var clock = new MutableClock();
      var guard = new RateLimitGuard(2, 1, clock);

      // When acquiring three times
      // Then the first two are allowed and the third is denied
      assertTrue(guard.tryAcquire("kill-switch"));
      assertTrue(guard.tryAcquire("kill-switch"));
      assertFalse(guard.tryAcquire("kill-switch"));
    }

    @Test
    void givenDifferentKeys_whenTryAcquire_thenIndependentBuckets() {
      var clock = new MutableClock();
      var guard = new RateLimitGuard(1, 1, clock);

      // When acquiring from two distinct keys
      // Then each gets its own single-token budget
      assertTrue(guard.tryAcquire("kill-switch"));
      assertTrue(guard.tryAcquire("close-position"));
      assertFalse(guard.tryAcquire("kill-switch"));
      assertFalse(guard.tryAcquire("close-position"));
    }

    @Test
    void givenRefillElapsed_whenTryAcquire_thenTokenRestored() {
      // Given capacity 2, 1 token per 0.5s (refill 1s / capacity 2)
      var clock = new MutableClock();
      var guard = new RateLimitGuard(2, 1, clock);

      // When the bucket is drained
      assertTrue(guard.tryAcquire("close-position"));
      assertTrue(guard.tryAcquire("close-position"));
      assertFalse(guard.tryAcquire("close-position"));

      // And 0.5s elapses (one token refilled)
      clock.nanos += 500_000_000L;

      // Then a single further acquire is allowed, then denied again
      assertTrue(guard.tryAcquire("close-position"));
      assertFalse(guard.tryAcquire("close-position"));
    }
  }
}
