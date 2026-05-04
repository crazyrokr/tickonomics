// Types generated from api-contracts/openapi.yaml
// Extended with dashboard-specific endpoints from docs/plan_v5/07-analytics-dashboard.md

// ── OpenAPI Schema Types ──────────────────────────────────────────────────

export type SignalDirection = "LONG" | "SHORT" | "NEUTRAL";

export interface StrategyStatus {
  id: string;
  name: string;
  category: string;
  formulaRefs: string[];
  active: boolean;
}

export interface AlphaSignal {
  timestamp: string;
  strategyId: string;
  symbol: string;
  direction: SignalDirection;
  strength?: number;
  confidence?: number;
  expectedMove?: number;
  metrics?: Record<string, number>;
}

export interface EvtMetric {
  shapeXi?: number;
  scaleBeta?: number;
  thresholdU?: number;
  tailVar999?: number;
}

export interface IrfVector {
  horizon: number[];
  responsePath: number[];
  confidenceHigh: number[];
  confidenceLow: number[];
}

export interface EvtTailResponse {
  irfVector: IrfVector;
}

export interface MacroShockResponse {
  irfVector: IrfVector;
}

export interface IntersubjectivePathStep {
  ruleName?: string;
  ruleVersion?: string;
  inputHash?: string;
  outputValue?: number;
  irScore?: number;
}

export interface IntersubjectivePath {
  signalId?: string;
  path?: IntersubjectivePathStep[];
}

// ── Dashboard API Types ───────────────────────────────────────────────────

export type IliStatus = "VALID" | "DEGRADED_COMPONENT_STALE" | "DISLOCATED";

export type SignalStatusCode =
  | "ACTIONABLE"
  | "SPECULATIVE_STALE_MACRO"
  | "COST_EXCEEDS_EXPECTED_MOVE"
  | "COOLDOWN"
  | "INSUFFICIENT_DATA";

export type VolatilityRegime = "LOW_VOL" | "NORMAL" | "HIGH_VOL";

export interface IliValue {
  value: number;
  status: IliStatus;
  activeWeights: Record<string, number>;
  excludedComponents: string[];
  timestamp: string;
}

export interface IliHistoryPoint {
  timestamp: string;
  value: number;
  status: IliStatus;
  anomalyScore?: number;
}

export interface SignalMarker {
  id: string;
  timestamp: string;
  statusCode: SignalStatusCode;
  direction: SignalDirection;
  symbol: string;
  iliValue: number;
}

export interface LiquidityStressIndex {
  value: number;
  trend: "ACCELERATING" | "DECELERATING" | "STABLE";
  timestamp: string;
}

export interface RepoEquityBeta {
  symbol: string;
  beta: number;
  lastUpdate: string;
}

export interface RrpDrainVelocity {
  velocity: number;
  dayOverDayChange: number;
  trend: "ACCELERATING" | "DECELERATING" | "STABLE";
  history: Array<{ timestamp: string; velocity: number }>;
}

export interface VolatilityRegimeData {
  regime: VolatilityRegime;
  upperBand: number;
  lowerBand: number;
  currentPrice: number;
  timestamp: string;
}

export interface CorrelationEntry {
  source: string;
  target: string;
  correlation: number;
  pValue: number;
  sampleSize: number;
  aicLagOrder: number;
}

export interface SystemicRiskHeatmap {
  triPartyGcfSpread: number;
  sofrPctlRange: number;
  tgcrBgcrSpread: number;
  tgaBalanceChange: number;
  timestamp: string;
}

export interface DataSourceHealth {
  name: string;
  healthy: boolean;
  lastSync: string;
  latencyMs: number;
}

export interface ProxyDivergence {
  tbillSofrCorrelation5d: number;
  divergenceScore: number;
  dislocated: boolean;
  timestamp: string;
}

export interface HealthResponse {
  sources: DataSourceHealth[];
  proxyDivergence: ProxyDivergence;
  timescaleDb: {
    connected: boolean;
    compressionStatus: string;
    aggregateLagSeconds: number;
  };
  circuitBreakers: Record<string, "CLOSED" | "OPEN" | "HALF_OPEN">;
}

export interface ConfigEntry {
  key: string;
  value: unknown;
}

export interface ConfigHistoryEntry {
  id: string;
  timestamp: string;
  author: string;
  auditReason: string;
  diff: string;
}

// ── WebSocket Message Types ───────────────────────────────────────────────

export interface PriceUpdate {
  symbol: string;
  price: number;
  timestamp: string;
}

export interface SignalUpdate {
  signal: SignalMarker;
}

export type WebSocketMessage = PriceUpdate | SignalUpdate;
