package com.tickonomics.computation.talib;

import java.util.Arrays;

public record BBandsResult(double[] upper, double[] middle, double[] lower, int begIdx, int nbElement) {

  public BBandsResult {
    if (upper == null || middle == null || lower == null) {
      throw new NullPointerException("Band arrays must not be null");
    }
    if (nbElement < 0) {
      throw new IllegalArgumentException("nbElement must be non-negative: " + nbElement);
    }
  }

  public double[] validUpper() {
    return Arrays.copyOf(upper, nbElement);
  }

  public double[] validMiddle() {
    return Arrays.copyOf(middle, nbElement);
  }

  public double[] validLower() {
    return Arrays.copyOf(lower, nbElement);
  }
}
