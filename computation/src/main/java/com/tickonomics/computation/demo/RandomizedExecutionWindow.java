package com.tickonomics.computation.demo;

import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Spreads virtual trade execution across a randomized sub-minute window to mitigate human-biased
 * round-mark price impact, complementing the ingestion-side {@code DeRoundingFilter}. Deterministic
 * given the injected {@link Random}, so unit tests assert exact shifts out of the danger zone.
 */
@Component
public class RandomizedExecutionWindow {

  static final int DEFAULT_ROUND_MARK_INTERVAL_SECONDS = 10;
  static final int DEFAULT_MARGIN_SECONDS = 1;

  private final Random defaultRandom = new Random();

  public long delaySeconds(int windowSeconds, boolean avoidRoundMarks) {
    return delaySeconds(defaultRandom, windowSeconds, avoidRoundMarks,
        DEFAULT_ROUND_MARK_INTERVAL_SECONDS, DEFAULT_MARGIN_SECONDS);
  }

  /**
   * @param random                source of randomness (injectable for deterministic tests)
   * @param windowSeconds         inclusive upper bound on the delay; {@code <= 0} yields no delay
   * @param avoidRoundMarks       when true, delays landing within {@code margin} of a decadal
   *                              round mark are shifted just outside the danger zone
   * @param roundMarkIntervalSecs spacing between round marks (e.g. 10s)
   * @param marginSecs            half-width of the danger zone around each round mark
   */
  public long delaySeconds(Random random, int windowSeconds, boolean avoidRoundMarks,
      int roundMarkIntervalSecs, int marginSecs) {
    if (windowSeconds <= 0 || random == null) {
      return 0L;
    }
    long delay = random.nextInt(windowSeconds + 1);
    if (!avoidRoundMarks || roundMarkIntervalSecs <= 0 || marginSecs <= 0) {
      return delay;
    }
    return shiftOutOfDangerZone(delay, windowSeconds, roundMarkIntervalSecs, marginSecs);
  }

  public long delaySeconds(Random random, int windowSeconds, boolean avoidRoundMarks) {
    return delaySeconds(random, windowSeconds, avoidRoundMarks,
        DEFAULT_ROUND_MARK_INTERVAL_SECONDS, DEFAULT_MARGIN_SECONDS);
  }

  long shiftOutOfDangerZone(long delay, int windowSeconds, int roundMarkIntervalSecs,
      int marginSecs) {
    long nearest = Math.round((double) delay / roundMarkIntervalSecs) * roundMarkIntervalSecs;
    if (Math.abs(delay - nearest) > marginSecs) {
      return delay;
    }
    long candidate = nearest + marginSecs + 1;
    if (candidate <= windowSeconds) {
      return candidate;
    }
    return Math.max(0, nearest - marginSecs - 1);
  }
}
