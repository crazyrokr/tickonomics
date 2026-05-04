import { describe, it, expect } from "vitest";
import type {
  IliStatus,
  SignalStatusCode,
  VolatilityRegime,
  IliValue,
  SignalMarker,
  HealthResponse,
  AlphaSignal,
  SignalDirection,
} from "@/types/api";

describe("API types", () => {
  it("IliStatus accepts all valid status values", () => {
    const statuses: IliStatus[] = [
      "VALID",
      "DEGRADED_COMPONENT_STALE",
      "DISLOCATED",
    ];
    expect(statuses).toHaveLength(3);
  });

  it("SignalStatusCode accepts all valid signal codes", () => {
    const codes: SignalStatusCode[] = [
      "ACTIONABLE",
      "SPECULATIVE_STALE_MACRO",
      "COST_EXCEEDS_EXPECTED_MOVE",
      "COOLDOWN",
      "INSUFFICIENT_DATA",
    ];
    expect(codes).toHaveLength(5);
  });

  it("VolatilityRegime accepts all valid regime values", () => {
    const regimes: VolatilityRegime[] = ["LOW_VOL", "NORMAL", "HIGH_VOL"];
    expect(regimes).toHaveLength(3);
  });

  it("SignalDirection accepts LONG, SHORT, NEUTRAL", () => {
    const directions: SignalDirection[] = ["LONG", "SHORT", "NEUTRAL"];
    expect(directions).toHaveLength(3);
  });

  it("IliValue shape is assignable", () => {
    const ili: IliValue = {
      value: 0.75,
      status: "VALID",
      activeWeights: { rrp: 0.4, spread: 0.35, vol: 0.25 },
      excludedComponents: [],
      timestamp: "2026-05-30T12:00:00Z",
    };
    expect(ili.status).toBe("VALID");
    expect(ili.activeWeights.rrp).toBe(0.4);
  });

  it("SignalMarker shape is assignable", () => {
    const marker: SignalMarker = {
      id: "sig-1",
      timestamp: "2026-05-30T12:00:00Z",
      statusCode: "ACTIONABLE",
      direction: "LONG",
      symbol: "SPY",
      iliValue: 0.8,
    };
    expect(marker.statusCode).toBe("ACTIONABLE");
  });

  it("AlphaSignal requires timestamp, strategyId, symbol, direction", () => {
    const signal: AlphaSignal = {
      timestamp: "2026-05-30T12:00:00Z",
      strategyId: "strat-1",
      symbol: "SPY",
      direction: "LONG",
    };
    expect(signal.direction).toBe("LONG");
    expect(signal.strength).toBeUndefined();
  });

  it("HealthResponse shape is assignable", () => {
    const health: HealthResponse = {
      sources: [
        { name: "FRED", healthy: true, lastSync: "2026-05-30T12:00:00Z", latencyMs: 120 },
      ],
      proxyDivergence: {
        tbillSofrCorrelation5d: 0.95,
        divergenceScore: 0.3,
        dislocated: false,
        timestamp: "2026-05-30T12:00:00Z",
      },
      timescaleDb: {
        connected: true,
        compressionStatus: "active",
        aggregateLagSeconds: 5,
      },
      circuitBreakers: { polygonWs: "CLOSED", openbb: "CLOSED" },
    };
    expect(health.sources[0].name).toBe("FRED");
    expect(health.circuitBreakers.polygonWs).toBe("CLOSED");
  });
});
