package com.tickonomics.web.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory, dependency-free token-bucket rate limiter. Each {@code key} (e.g. an endpoint name)
 * gets an independent bucket sized by {@code security.rate-limit.capacity} that refills that many
 * tokens per {@code security.rate-limit.refill-seconds}.
 *
 * <p>Sufficient for a single-host research sandbox. For a multi-instance deployment this would need
 * a shared store (Redis); see the P2 ADR.
 */
@Component
public class RateLimitGuard {

  private final int capacity;
  private final long nanosPerToken;
  private final LongSupplier nanoClock;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  @Autowired
  public RateLimitGuard(
      @Value("${security.rate-limit.capacity:5}") int capacity,
      @Value("${security.rate-limit.refill-seconds:60}") long refillSeconds) {
    this(capacity, refillSeconds, System::nanoTime);
  }

  /** Test seam: injects the clock so refill behaviour is deterministic. */
  RateLimitGuard(int capacity, long refillSeconds, LongSupplier nanoClock) {
    this.capacity = Math.max(1, capacity);
    long refillNanos = Math.max(1, Duration.ofSeconds(Math.max(1, refillSeconds)).toNanos());
    this.nanosPerToken = refillNanos / this.capacity;
    this.nanoClock = nanoClock;
  }

  /**
   * Attempts to consume one token for {@code key}. Returns {@code true} if allowed, {@code false}
   * if the bucket is exhausted.
   */
  public boolean tryAcquire(String key) {
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, nanosPerToken, nanoClock));
    return bucket.tryConsume();
  }

  /** Visible for testing. */
  int capacity() {
    return capacity;
  }

  private static final class Bucket {
    private final int capacity;
    private final long nanosPerToken;
    private final LongSupplier nanoClock;
    private double tokens;
    private long lastRefillNanos;

    Bucket(int capacity, long nanosPerToken, LongSupplier nanoClock) {
      this.capacity = capacity;
      this.nanosPerToken = nanosPerToken;
      this.nanoClock = nanoClock;
      this.tokens = capacity;
      this.lastRefillNanos = nanoClock.getAsLong();
    }

    synchronized boolean tryConsume() {
      long now = nanoClock.getAsLong();
      double refilled = (double) (now - lastRefillNanos) / nanosPerToken;
      tokens = Math.min(capacity, tokens + refilled);
      lastRefillNanos = now;
      if (tokens >= 1.0) {
        tokens -= 1.0;
        return true;
      }
      return false;
    }
  }
}

