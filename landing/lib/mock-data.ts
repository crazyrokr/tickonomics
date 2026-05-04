import type {
  AlphaSignal,
  DemoPortfolio,
  IliHistoryPoint,
  ParticipationResponse,
  RegimeResponse,
  StrategyStatus,
} from "./types";

const today = new Date();
function daysAgo(n: number): string {
  const d = new Date(today);
  d.setDate(d.getDate() - n);
  return d.toISOString().split("T")[0];
}

export const mockIliHistory: IliHistoryPoint[] = Array.from(
  { length: 90 },
  (_, i) => {
    const day = 89 - i;
    const base = 0.45 + 0.15 * Math.sin((day / 90) * Math.PI * 2);
    return {
      time: daysAgo(day),
      value: Math.round(base * 1000) / 1000,
      data_status: "VALID",
      is_suspect_anomaly: false,
      anomaly_score: 0.02 + Math.random() * 0.03,
    };
  }
);

export const mockSignals: AlphaSignal[] = [
  {
    timestamp: new Date().toISOString(),
    strategyId: "550e8400-e29b-41d4-a716-446655440001",
    symbol: "SPY",
    direction: "LONG",
    strength: 0.82,
    confidence: 0.74,
    expected_move: 1.35,
    metrics: { ili_percentile: 78, z_score_rrp: 1.2 },
    status: "ACTIONABLE",
  },
  {
    timestamp: new Date(Date.now() - 3600000).toISOString(),
    strategyId: "550e8400-e29b-41d4-a716-446655440002",
    symbol: "QQQ",
    direction: "SHORT",
    strength: 0.65,
    confidence: 0.58,
    expected_move: -0.92,
    metrics: { ili_percentile: 22, z_score_rrp: -0.8 },
    status: "SPECULATIVE_STALE_MACRO",
  },
  {
    timestamp: new Date(Date.now() - 7200000).toISOString(),
    strategyId: "550e8400-e29b-41d4-a716-446655440003",
    symbol: "IWM",
    direction: "NEUTRAL",
    strength: 0.31,
    confidence: 0.42,
    expected_move: 0.15,
    metrics: { ili_percentile: 48, z_score_rrp: 0.1 },
    status: "COOLDOWN",
  },
];

export const mockStrategies: StrategyStatus[] = [
  {
    id: "550e8400-e29b-41d4-a716-446655440010",
    name: "RSI Mean Reversion",
    category: "equity",
    formula_refs: ["eq-011"],
    active: true,
  },
  {
    id: "550e8400-e29b-41d4-a716-446655440011",
    name: "Bollinger Breakout",
    category: "equity",
    formula_refs: ["eq-003"],
    active: true,
  },
  {
    id: "550e8400-e29b-41d4-a716-446655440012",
    name: "Iron Condor",
    category: "options",
    formula_refs: ["opt-001"],
    active: true,
  },
];

export const mockRegime: RegimeResponse = {
  regime_status: "NORMAL",
};

export const mockParticipation: ParticipationResponse = {
  participation_status: "ADMISSIBLE",
  reason: null,
};

export const mockPortfolio: DemoPortfolio = {
  pnl: 12.4,
  win_rate: 0.68,
  signal_count: 47,
  period_days: 90,
};
