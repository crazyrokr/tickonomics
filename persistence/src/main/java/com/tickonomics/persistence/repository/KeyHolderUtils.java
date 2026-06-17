package com.tickonomics.persistence.repository;

import java.math.BigInteger;
import org.springframework.jdbc.support.KeyHolder;

/**
 * Safe extraction of generated keys from {@link KeyHolder}.
 * {@code KeyHolder.getKey()} returns {@code null} when no key was generated
 * (e.g. the INSERT did not produce a row, or the driver did not return one).
 * This utility makes the failure explicit instead of a silent NPE.
 */
final class KeyHolderUtils {

  private KeyHolderUtils() {
    throw new UnsupportedOperationException("utility class");
  }

  static long extractGeneratedLong(KeyHolder keyHolder) {
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException(
          "expected a generated key but KeyHolder.getKey() returned null — "
              + "the INSERT may not have produced a row or the JDBC driver "
              + "did not return generated keys");
    }
    if (key instanceof Long l) {
      return l;
    }
    if (key instanceof BigInteger bi) {
      return bi.longValue();
    }
    return key.longValue();
  }
}
