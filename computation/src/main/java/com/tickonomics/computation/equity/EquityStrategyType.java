package com.tickonomics.computation.equity;

public enum EquityStrategyType {
  PRICE_MOMENTUM("EA.01", "Eq. 266-280"),
  EARNINGS_SURPRISE("EA.02", "Eq. 281-288"),
  MEAN_REVERSION("EA.03", "Eq. 294-300"),
  CLUSTER_MEAN_REVERSION("EA.04", "Eq. 293"),
  PAIRS_COINTEGRATION("EA.05", "Eq. 301-310"),
  VALUE_BP("EA.06", "Eq. 289-292"),
  MA_CROSSOVER("EA.07", "Eq. 311-320"),
  SUPPORT_RESISTANCE("EA.08", "Eq. 331-340"),
  BB_BREAKOUT("EA.09", "Eq. 341-350"),
  ACCUM_DIST("EA.10", "Eq. 351-360"),
  RSI_OSCILLATOR("EA.11", "Sec 3.15"),
  MACD_DIVERGENCE("EA.12", "Sec 3.11"),
  BOLLINGER_WIDTH("EA.13", "Eq. 350"),
  VOLUME_MOMENTUM("EA.14", "Sec 3.5"),
  STOCHASTIC_CROSS("EA.15", "Sec 3.15"),
  CHAIKIN_VOL("EA.16", "Sec 3.18"),
  MFI_INVERSION("EA.17", "Sec 3.19"),
  TRIX_REVERSAL("EA.18", "Sec 3.20"),
  COPPOCK_CURVE("EA.19", "Sec 3.21"),
  KELTNER_CHANNEL("EA.20", "Sec 3.22"),
  DONCHIAN_CHANNEL("EA.21", "Sec 3.23"),
  AROON_OSCILLATOR("EA.22", "Sec 3.24"),
  PARABOLIC_SAR("EA.23", "Sec 3.25"),
  ZIGZAG_FILTER("EA.24", "Sec 3.26"),
  ICHIMOKU_CLOUD("EA.25", "Sec 3.27");

  private final String strategyId;
  private final String formulaRef;

  EquityStrategyType(String strategyId, String formulaRef) {
    this.strategyId = strategyId;
    this.formulaRef = formulaRef;
  }

  public String strategyId() {
    return strategyId;
  }

  public String formulaRef() {
    return formulaRef;
  }
}
