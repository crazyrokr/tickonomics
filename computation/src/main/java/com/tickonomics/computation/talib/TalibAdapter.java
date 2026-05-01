package com.tickonomics.computation.talib;

import com.tictactec.ta.lib.MAType;
import com.tictactec.ta.lib.functions.Bbands;
import com.tictactec.ta.lib.functions.Beta;
import com.tictactec.ta.lib.functions.Correl;
import com.tictactec.ta.lib.functions.LinearRegSlope;
import com.tictactec.ta.lib.functions.Roc;
import com.tictactec.ta.lib.functions.Rsi;
import com.tictactec.ta.lib.functions.Sma;
import com.tictactec.ta.lib.functions.StdDev;
import com.tictactec.ta.lib.results.BandsResult;
import com.tictactec.ta.lib.results.RealResult;

import java.util.Arrays;

public class TalibAdapter {

  public TalibAdapter() {
    TalibNativeLoader.ensureLoaded();
  }

  public double[] computeSma(double[] data, int period) {
    requireNonEmpty(data);
    RealResult result = (RealResult) Sma.execute(0, data.length - 1, data, period);
    return extractValid(result);
  }

  public double[] computeStdDev(double[] data, int period) {
    requireNonEmpty(data);
    RealResult result = (RealResult) StdDev.execute(0, data.length - 1, data, period, 1.0);
    return extractValid(result);
  }

  public double[] computeCorrel(double[] x, double[] y, int period) {
    requireSameLength(x, y);
    RealResult result = (RealResult) Correl.execute(0, x.length - 1, x, y, period);
    return extractValid(result);
  }

  public double[] computeBeta(double[] market, double[] benchmark, int period) {
    requireSameLength(market, benchmark);
    RealResult result = (RealResult) Beta.execute(0, market.length - 1, market, benchmark, period);
    return extractValid(result);
  }

  public double[] computeLinearRegSlope(double[] data, int period) {
    requireNonEmpty(data);
    RealResult result = (RealResult) LinearRegSlope.execute(0, data.length - 1, data, period);
    return extractValid(result);
  }

  public BBandsResult computeBollingerBands(double[] data, int period, double devUp, double devDown) {
    requireNonEmpty(data);
    BandsResult result = (BandsResult) Bbands.execute(
        0,
        data.length - 1,
        data,
        period,
        devUp,
        devDown,
        MAType.TA_MAType_SMA.idx());
    return new BBandsResult(
        result.outRealUpperBand(),
        result.outRealMiddleBand(),
        result.outRealLowerBand(),
        result.outBegIdx(),
        result.outNBElement());
  }

  public double[] computeRoc(double[] data, int period) {
    requireNonEmpty(data);
    RealResult result = (RealResult) Roc.execute(0, data.length - 1, data, period);
    return extractValid(result);
  }

  public double[] computeRsi(double[] data, int period) {
    requireNonEmpty(data);
    RealResult result = (RealResult) Rsi.execute(0, data.length - 1, data, period);
    return extractValid(result);
  }

  private static double[] extractValid(RealResult result) {
    return Arrays.copyOf(result.outReal(), result.outNBElement());
  }

  private static void requireNonEmpty(double[] data) {
    if (data == null || data.length == 0) {
      throw new IllegalArgumentException("Input data must not be null or empty");
    }
  }

  private static void requireSameLength(double[] x, double[] y) {
    requireNonEmpty(x);
    requireNonEmpty(y);
    if (x.length != y.length) {
      throw new IllegalArgumentException("Input arrays must have the same length: " + x.length + " != " + y.length);
    }
  }
}
