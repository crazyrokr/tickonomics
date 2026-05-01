package com.tickonomics.computation.kpi;

import java.util.Arrays;
import org.springframework.stereotype.Service;

@Service
public class IliCalculator {

  private static final double DEFAULT_W_RRP = 0.4;
  private static final double DEFAULT_W_SPREAD = 0.35;
  private static final double DEFAULT_W_VOL = 0.25;

  public IliResult calculate(ZscoreResult zRrp, ZscoreResult zSpread, ZscoreResult zVol) {
    return calculate(zRrp, zSpread, zVol, null);
  }

  public IliResult calculate(ZscoreResult zRrp, ZscoreResult zSpread, ZscoreResult zVol, double[] weights) {
    if (weights == null) {
      weights = new double[]{
          DEFAULT_W_RRP,
          DEFAULT_W_SPREAD,
          DEFAULT_W_VOL
      };
    }

    double wRrp = weights[0];
    double wSpread = weights[1];
    double wVol = weights[2];

    boolean rrpValid = zRrp.valid();
    boolean spreadValid = zSpread.valid();
    boolean volValid = zVol.valid();

    if (!rrpValid || !spreadValid || !volValid) {
      RedistributionResult redistributed = redistributeWeights(
          new double[]{
              wRrp,
              wSpread,
              wVol
          },
          new boolean[]{
              rrpValid,
              spreadValid,
              volValid
          });
      wRrp = redistributed.weights()[0];
      wSpread = redistributed.weights()[1];
      wVol = redistributed.weights()[2];
      weights = redistributed.weights();
    }

    double zRrpVal = rrpValid ? zRrp.zScore() : 0.0;
    double zSpreadVal = spreadValid ? zSpread.zScore() : 0.0;
    double zVolVal = volValid ? zVol.zScore() : 0.0;

    double ili = wRrp * zRrpVal + wSpread * zSpreadVal - wVol * zVolVal;

    String status = determineStatus(rrpValid, spreadValid, volValid);

    return new IliResult(ili, zRrpVal, zSpreadVal, zVolVal, status, weights, null, null);
  }

  public IliResult withProxyDivergence(IliResult ili, boolean divergent, double divergenceScore) {
    String status = divergent ? IliResult.STATUS_DISLOCATED : ili.dataStatus();
    return new IliResult(
        ili.iliValue(),
        ili.zRrp(),
        ili.zSpread(),
        ili.zVol(),
        status,
        ili.activeWeights(),
        divergent ? "DIVERGENT" : "NORMAL",
        divergent ? divergenceScore : (ili.proxyDivergenceScore() != null ? ili.proxyDivergenceScore() : 0.0));
  }

  String determineStatus(boolean rrpValid, boolean spreadValid, boolean volValid) {
    if (rrpValid && spreadValid && volValid) {
      return IliResult.STATUS_VALID;
    }
    return IliResult.STATUS_DEGRADED;
  }

  RedistributionResult redistributeWeights(double[] weights, boolean[] valid) {
    double totalInvalid = 0;
    for (int i = 0; i < valid.length; i++) {
      if (!valid[i]) {
        totalInvalid += weights[i];
      }
    }

    if (totalInvalid == 0) {
      return new RedistributionResult(weights);
    }

    double validSum = 0;
    for (int i = 0; i < valid.length; i++) {
      if (valid[i]) {
        validSum += weights[i];
      }
    }

    if (validSum == 0) {
      return new RedistributionResult(weights);
    }

    double[] redistributed = new double[weights.length];
    for (int i = 0; i < weights.length; i++) {
      if (valid[i]) {
        redistributed[i] = weights[i] + (weights[i] / validSum) * totalInvalid;
      } else {
        redistributed[i] = 0.0;
      }
    }

    return new RedistributionResult(redistributed);
  }

  record RedistributionResult(double[] weights) {
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof RedistributionResult that)) {
        return false;
      }
      return Arrays.equals(weights, that.weights);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(weights);
    }
  }
}
