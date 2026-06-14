import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import {
  useDemoPortfolio,
  useDemoTrades,
  useDemoSignalQuality,
  useKillSwitchStatus,
} from "@/hooks/useDemoData";

const FIXTURES: Record<string, unknown> = {
  "/api/v1/demo/portfolio": {
    balance: 105_000,
    initialBalance: 100_000,
    realizedPnl: 4_000,
    unrealizedPnl: 1_000,
    totalPnl: 5_000,
    openPositions: 2,
    totalTrades: 10,
    winRate: 0.6,
    enabled: true,
  },
  "/api/v1/demo/trades": [
    {
      id: 1,
      symbol: "SPY",
      direction: "BUY",
      quantity: 10,
      fillPrice: 500,
      commission: 1,
      slippage: 0.5,
      realizedPnl: null,
      executedAt: "2026-06-13T10:00:00Z",
      tradeType: "PAPER",
    },
  ],
  "/api/v1/demo/signal-quality": {
    reportDate: "2026-06-13",
    hitRate5d: 0.62,
    falsePositiveRate: 0.12,
  },
  "/api/v1/demo/kill-switch/status": { active: false },
};

describe("useDemoData hooks", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    globalThis.fetch = vi.fn().mockImplementation(async (url: string) => {
      const path = url.replace(/^https?:\/\/[^/]+/, "").split("?")[0];
      const payload = FIXTURES[path] ?? {};
      return {
        ok: true,
        status: 200,
        json: () => Promise.resolve(payload),
      } as Response;
    });
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  function createWrapper() {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    return function Wrapper({ children }: { children: ReactNode }) {
      return (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      );
    };
  }

  it("returns the demo portfolio", async () => {
    const { result } = renderHook(() => useDemoPortfolio(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.balance).toBe(105_000);
  });

  it("returns demo trades", async () => {
    const { result } = renderHook(() => useDemoTrades(50, 0), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.length).toBe(1);
    expect(result.current.data?.[0].symbol).toBe("SPY");
  });

  it("returns the signal-quality report", async () => {
    const { result } = renderHook(() => useDemoSignalQuality(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.hitRate5d).toBe(0.62);
  });

  it("returns the kill-switch status", async () => {
    const { result } = renderHook(() => useKillSwitchStatus(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.active).toBe(false);
  });
});
