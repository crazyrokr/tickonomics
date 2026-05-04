import { describe, it, expect } from "vitest";
import type {
  AlphaSignal,
  IliHistoryPoint,
  RegimeResponse,
  ParticipationResponse,
  SignalStatusCode,
} from "@/lib/types";

describe("types", () => {
  it("given valid AlphaSignal fields, when constructing object, then types align", () => {
    const signal: AlphaSignal = {
      timestamp: new Date().toISOString(),
      strategyId: "uuid-1",
      symbol: "SPY",
      direction: "LONG",
      strength: 0.8,
      confidence: 0.7,
      expected_move: 1.2,
      metrics: {},
    };

    expect(signal.direction).toBe("LONG");
    expect(signal.symbol).toBe("SPY");
  });

  it("given all SignalStatusCode values, when used as union type, then each compiles", () => {
    const statuses: SignalStatusCode[] = [
      "ACTIONABLE",
      "SPECULATIVE_STALE_MACRO",
      "COST_EXCEEDS_EXPECTED_MOVE",
      "COOLDOWN",
      "INSUFFICIENT_DATA",
    ];

    expect(statuses).toHaveLength(5);
  });

  it("given IliHistoryPoint with DEGRADED status, when accessing fields, then active_weights optional", () => {
    const point: IliHistoryPoint = {
      time: "2026-05-30",
      value: 0.45,
      data_status: "DEGRADED_COMPONENT_STALE",
      is_suspect_anomaly: false,
      anomaly_score: 0.03,
      active_weights: { rrp: 0.6, spread: 0.4 },
    };

    expect(point.active_weights).toBeDefined();
    expect(point.data_status).toBe("DEGRADED_COMPONENT_STALE");
  });

  it("given RegimeResponse, when accessing regime_status, then returns valid union member", () => {
    const regime: RegimeResponse = { regime_status: "NORMAL" };
    expect(regime.regime_status).toBe("NORMAL");
  });

  it("given ParticipationResponse with SUPPRESSED status, when reason is provided, then fields accessible", () => {
    const participation: ParticipationResponse = {
      participation_status: "SUPPRESSED",
      reason: "Regime: HIGH_VOL",
    };

    expect(participation.participation_status).toBe("SUPPRESSED");
    expect(participation.reason).toBe("Regime: HIGH_VOL");
  });
});
