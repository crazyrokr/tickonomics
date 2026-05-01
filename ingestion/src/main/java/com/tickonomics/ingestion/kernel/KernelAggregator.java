package com.tickonomics.ingestion.kernel;

import java.util.List;

public class KernelAggregator {

  public double computeRealizedVariance(List<Double> prices, KernelFunction kernel, int maxLag) {
    int n = prices.size();
    if (n < 2) {
      return 0.0;
    }

    double[] logReturns = new double[n - 1];
    for (int i = 1; i < n; i++) {
      logReturns[i - 1] = Math.log(prices.get(i) / prices.get(i - 1));
    }

    return computeKernelRV(logReturns, kernel, maxLag);
  }

  double computeKernelRV(double[] returns, KernelFunction kernel, int H) {
    int n = returns.length;
    if (n == 0) {
      return 0.0;
    }

    double gamma0 = computeAutocovariance(returns, 0);
    double rv = gamma0;

    for (int j = 1; j <= Math.min(H, n - 1); j++) {
      double gammaJ = computeAutocovariance(returns, j);
      double weight = kernel.apply((double) j / (H + 1));
      rv += 2.0 * weight * gammaJ;
    }

    return rv;
  }

  double computeAutocovariance(double[] data, int lag) {
    int n = data.length;
    double mean = 0.0;
    for (double v : data)
      mean += v;
    mean /= n;

    double sum = 0.0;
    for (int i = lag; i < n; i++) {
      sum += (data[i] - mean) * (data[i - lag] - mean);
    }
    return sum / n;
  }

  @FunctionalInterface
  public interface KernelFunction {
    double apply(double x);
  }

  public static final KernelFunction TUKEY_HANNING = x -> 0.5 * (1.0 + Math.cos(Math.PI * x));

  public static final KernelFunction PARZEN = x -> {
    double absX = Math.abs(x);
    if (absX <= 0.5) {
      return 1.0 - 6.0 * absX * absX + 6.0 * absX * absX * absX;
    } else if (absX <= 1.0) {
      return 2.0 * Math.pow(1.0 - absX, 3);
    }
    return 0.0;
  };
}
