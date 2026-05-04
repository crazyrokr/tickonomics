import type {
  AlphaSignal,
  DemoPortfolio,
  IliHistoryPoint,
  ParticipationResponse,
  RegimeResponse,
  StrategyStatus,
} from "./types";
import {
  mockIliHistory,
  mockSignals,
  mockStrategies,
  mockRegime,
  mockParticipation,
  mockPortfolio,
} from "./mock-data";

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const TIMEOUT_MS = 5000;

const mockFallbacks: Record<string, unknown> = {
  "/api/v1/quant/signals/active": mockSignals,
  "/api/v1/quant/strategies/active": mockStrategies,
  "/api/v1/kpi/ili/history?limit=90": mockIliHistory,
  "/api/v1/regime/garch": mockRegime,
  "/api/v1/participation/status": mockParticipation,
  "/api/v1/demo/portfolio": mockPortfolio,
};

export async function fetchApi<T>(path: string): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);

  try {
    const res = await fetch(`${API_BASE}${path}`, {
      signal: controller.signal,
      headers: { Accept: "application/json" },
    });

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }

    return (await res.json()) as T;
  } catch {
    const fallback = mockFallbacks[path];
    if (fallback) {
      return fallback as T;
    }
    throw new Error(`No mock fallback for ${path}`);
  } finally {
    clearTimeout(timer);
  }
}

export type { AlphaSignal, DemoPortfolio, IliHistoryPoint, ParticipationResponse, RegimeResponse, StrategyStatus };
