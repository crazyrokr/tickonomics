import { describe, it, expect, vi, beforeAll, afterEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useDashboardData } from "@/hooks/useDashboardData";

type FetchFixture = Record<string, unknown>;

const FIXTURES: FetchFixture = {
  "/api/v1/kpi/ili": {
    value: 0.74,
    status: "VALID",
    activeWeights: { rrp: 0.4 },
    excludedComponents: [],
    timestamp: "2026-06-13T10:00:00Z",
  },
  "/api/v1/kpi/ili/history": [
    { timestamp: "2026-06-13T09:00:00Z", value: 0.7, status: "VALID" },
  ],
  "/api/v1/kpi/liquidity-stress": {
    value: 0.3,
    trend: "STABLE",
    timestamp: "2026-06-13T10:00:00Z",
  },
  "/api/v1/kpi/repo-equity-beta": [{ symbol: "SPY", beta: 1.2, lastUpdate: "2026-06-13T10:00:00Z" }],
  "/api/v1/kpi/rrp-drain": {
    velocity: -1.5,
    dayOverDayChange: -0.1,
    trend: "DECELERATING",
    history: [],
  },
  "/api/v1/kpi/volatility-regime": {
    regime: "NORMAL",
    upperBand: 4500,
    lowerBand: 4400,
    currentPrice: 4450,
    timestamp: "2026-06-13T10:00:00Z",
  },
  "/api/v1/kpi/correlation-matrix": [],
  "/api/v1/kpi/systemic-risk-heatmap": {
    triPartyGcfSpread: 0.1,
    sofrPctlRange: 0.05,
    tgcrBgcrSpread: 0.02,
    tgaBalanceChange: -5,
    timestamp: "2026-06-13T10:00:00Z",
  },
  "/api/v1/health": {
    sources: [],
    proxyDivergence: {
      tbillSofrCorrelation5d: 0.9,
      divergenceScore: 0.1,
      dislocated: false,
      timestamp: "2026-06-13T10:00:00Z",
    },
    timescaleDb: { connected: true, compressionStatus: "active", aggregateLagSeconds: 0 },
    circuitBreakers: {},
  },
  "/api/v1/config": [{ key: "threshold", value: 0.5 }],
  "/api/v1/config/history": [],
  "/api/v1/signals": [],
};

function mockFetch(url: string) {
  const path = new URL(url).pathname;
  const body = FIXTURES[path] ?? [];
  return Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve(body),
  });
}

class MockWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;
  readyState = MockWebSocket.CLOSED;
  onopen: ((ev: unknown) => void) | null = null;
  onclose: ((ev: unknown) => void) | null = null;
  onerror: ((ev: unknown) => void) | null = null;
  onmessage: ((ev: unknown) => void) | null = null;
  constructor(public readonly url: string) {}
  close() {}
  send() {}
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useDashboardData", () => {
  beforeAll(() => {
    globalThis.WebSocket = MockWebSocket as unknown as typeof WebSocket;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("aggregates every backed data source into a single payload", async () => {
    globalThis.fetch = vi.fn((url: string) => mockFetch(url)) as unknown as typeof fetch;

    const { result } = renderHook(() => useDashboardData(), { wrapper });

    await waitFor(() => {
      expect(result.current.ili.data?.value).toBe(0.74);
    });

    expect(result.current.volatilityRegime.data?.regime).toBe("NORMAL");
    expect(result.current.repoEquityBeta.data).toHaveLength(1);
    expect(result.current.health.data?.timescaleDb.connected).toBe(true);
    expect(result.current.config.data).toHaveLength(1);
    expect(result.current.signals.signals).toEqual([]);
  });

  it("exposes loading flags while queries are still pending", () => {
    globalThis.fetch = vi.fn(() => new Promise(() => {})) as unknown as typeof fetch;

    const { result } = renderHook(() => useDashboardData(), { wrapper });

    expect(result.current.ili.isLoading).toBe(true);
  });
});
