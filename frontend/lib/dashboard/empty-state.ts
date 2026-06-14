// Safe empty-state defaults for dashboard components whose backing API endpoints
// are not yet implemented (Track 1 controllers). Every default renders the
// component's own "no data" state (empty message or skeleton) without crashing.
// Array props receive [] so the component shows its empty message; object-gated
// optional props receive undefined so the component renders its loading skeleton
// and auto-resolves once the backend endpoint ships. No fabricated values.

export const emptyBacktestHeatmap = {
  type: "time" as "time" | "parameter",
  data: [],
  paramALabel: "",
  paramBLabel: "",
  isLoading: false,
};

export const emptyD3IliHeatmap = {
  data: [],
  rowLabels: [],
  colLabels: [],
  isLoading: false,
};

export const emptyGammaProfile = {
  data: [],
  flipZonePrice: null,
  enabled: false,
  isLoading: false,
};

export const emptyPotentialWell = {
  data: [],
  currentPrice: 0,
  minima: [],
  isLoading: false,
};

export const emptySentimentHeatmap = {
  data: [],
  isLoading: false,
};

export const emptyTailRiskQQPlot = {
  data: [],
  heavyTailRegime: false,
  isLoading: false,
};

export const emptyToxicityHeatmap = {
  data: [],
  isLoading: false,
};

export const emptyVolatilityCluster = {
  data: [],
  currentRegime: "NORMAL" as "LOW_VOL" | "NORMAL" | "HIGH_VOL",
  isLoading: false,
};

export const emptyVolatilityPersistenceACF = {
  rawReturns: [],
  absoluteReturns: [],
  isLoading: false,
};

export const emptyConvergencePlot = {
  cumulativeMean: [],
  cumulativeVariance: [],
  sampleSize: 0,
  stable: false,
  isLoading: false,
};

export const emptyAnomalyScore = {
  mse: undefined,
  threshold: 0,
  suspectAnomaly: false,
  history: [],
  isLoading: false,
};

export const emptyLiquidityReliability = {
  score: 0,
  pliHigh: false,
  history: [],
  isLoading: false,
};

export const emptyDynamicStops = {
  positions: [],
  isLoading: false,
};

export const emptyExecutionComparison = {
  passivePnl: 0,
  aggressivePnl: 0,
  priceEfficiency: 0,
  isLoading: false,
};

export const emptyGreeks = {
  greeks: {
    repoDelta: 0,
    rateDelta: 0,
    rateGamma: 0,
    spreadDelta: 0,
    volga: 0,
  },
  dv01: 0,
  convexity: 0,
  isLoading: false,
};

export const emptyLeverageRegime = {
  regime: "LEVERAGE_OFF" as "LEVERAGE_ON" | "LEVERAGE_OFF",
  sp500Position: 0,
  ma200: 0,
  data: [],
  isLoading: false,
};

export const emptyMacroEnvironment = {
  indices: undefined,
  isLoading: false,
};

export const emptyMarketEfficiencyGap = {
  gap: 0,
  atDriven: false,
  breakdown: [],
  isLoading: false,
};

export const emptyModelTournament = {
  models: [],
  shapData: [],
  isLoading: false,
};

export const emptyMonetaryPolicy = {
  balanceSheet: { expanding: false, trend: 0 },
  policyRates: { fedFunds: 0, iorb: 0, sofrSpread: 0 },
  yieldCurveShape: "",
  isLoading: false,
};

export const emptyOptimizerStatus = {
  current: undefined,
  isLoading: false,
};

export const emptyPairsVerification = {
  pair: "",
  distance: 0,
  iliSignal: "",
  pairsSignal: "",
  formationEnd: null,
  data: [],
  isLoading: false,
};

export const emptyParticipation = {
  statuses: [],
  isLoading: false,
};

export const emptyPerformanceDecomposition = {
  data: undefined,
  isLoading: false,
};

export const emptyPerformanceDuality = {
  iliReturn: 0,
  benchmarkReturn: 0,
  confidenceScore: 0,
  isLoading: false,
};

export const emptyRegimeComparison = {
  models: [],
  isLoading: false,
};

export const emptySignalExplainability = {
  explanation: undefined,
  isLoading: false,
};

export const emptySignalInformativeness = {
  data: [],
  isLoading: false,
};

export const emptyTraderType = {
  data: [],
  isLoading: false,
};
