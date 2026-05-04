export interface AlphaSignal {
  timestamp: string;
  strategyId: string;
  symbol: string;
  direction: "LONG" | "SHORT" | "NEUTRAL";
  strength: number;
  confidence: number;
  expected_move: number;
  metrics: Record<string, number>;
  status?: SignalStatusCode;
}

export type SignalStatusCode =
  | "ACTIONABLE"
  | "SPECULATIVE_STALE_MACRO"
  | "COST_EXCEEDS_EXPECTED_MOVE"
  | "COOLDOWN"
  | "INSUFFICIENT_DATA";

export interface StrategyStatus {
  id: string;
  name: string;
  category: string;
  formula_refs: string[];
  active: boolean;
}

export interface IliHistoryPoint {
  time: string;
  value: number;
  data_status: "VALID" | "DEGRADED_COMPONENT_STALE" | "DISLOCATED";
  is_suspect_anomaly: boolean;
  anomaly_score: number;
  active_weights?: Record<string, number>;
}

export type RegimeStatus =
  | "LOW_VOL"
  | "NORMAL"
  | "HIGH_VOL"
  | "EXOGENOUS_SHOCK";

export interface RegimeResponse {
  regime_status: RegimeStatus;
}

export type ParticipationStatus = "ADMISSIBLE" | "SUPPRESSED";

export interface ParticipationResponse {
  participation_status: ParticipationStatus;
  reason: string | null;
}

export interface DemoPortfolio {
  pnl: number;
  win_rate: number;
  signal_count: number;
  period_days: number;
}

export interface HealthResponse {
  status: "UP" | "DOWN";
}
