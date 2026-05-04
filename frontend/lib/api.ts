import type {
  StrategyStatus,
  AlphaSignal,
  EvtMetric,
  EvtTailResponse,
  MacroShockResponse,
  IntersubjectivePath,
  IliValue,
  IliHistoryPoint,
  SignalMarker,
  LiquidityStressIndex,
  RepoEquityBeta,
  RrpDrainVelocity,
  VolatilityRegimeData,
  CorrelationEntry,
  SystemicRiskHeatmap,
  HealthResponse,
  ConfigEntry,
  ConfigHistoryEntry,
} from "@/types/api";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function getAuthHeaders(): HeadersInit {
  const headers: HeadersInit = {};
  const token = getCookieValue("auth_token");
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return headers;
}

function getCookieValue(name: string): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const url = `${API_BASE_URL}${path}`;
  const headers = {
    "Content-Type": "application/json",
    ...getAuthHeaders(),
    ...options.headers,
  };

  const response = await fetch(url, {
    ...options,
    headers,
    signal: options.signal ?? AbortSignal.timeout(10000),
  });

  if (response.status === 401) {
    if (typeof window !== "undefined") {
      window.location.href = "/login";
    }
    throw new ApiError(401, "Unauthorized");
  }

  if (response.status === 403) {
    throw new ApiError(403, "Forbidden");
  }

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText);
  }

  return response.json() as Promise<T>;
}

// ── Quant endpoints (from OpenAPI spec) ─────────────────────────────────

export async function getActiveStrategies(): Promise<StrategyStatus[]> {
  return request<StrategyStatus[]>("/api/v1/quant/strategies/active");
}

export async function getActiveSignals(
  category?: string,
): Promise<AlphaSignal[]> {
  const query = category ? `?category=${encodeURIComponent(category)}` : "";
  return request<AlphaSignal[]>(`/api/v1/quant/signals/active${query}`);
}

export async function getTailParameters(): Promise<EvtMetric> {
  return request<EvtMetric>("/api/v1/quant/risk/tail-parameters");
}

export async function getEvtTail(): Promise<EvtTailResponse> {
  return request<EvtTailResponse>("/api/v1/quant/risk/evt-tail");
}

export async function getMacroShock(): Promise<MacroShockResponse> {
  return request<MacroShockResponse>("/api/v1/quant/macro/shock-response");
}

export async function getIntersubjectivePath(
  id: string,
): Promise<IntersubjectivePath> {
  return request<IntersubjectivePath>(
    `/api/v1/quant/audit/intersubjective-reproducibility/${id}`,
  );
}

// ── KPI endpoints ───────────────────────────────────────────────────────

export async function getIli(): Promise<IliValue> {
  return request<IliValue>("/api/v1/kpi/ili");
}

export async function getIliHistory(): Promise<IliHistoryPoint[]> {
  return request<IliHistoryPoint[]>("/api/v1/kpi/ili/history");
}

export async function getSignals(): Promise<SignalMarker[]> {
  return request<SignalMarker[]>("/api/v1/signals");
}

export async function getLiquidityStress(): Promise<LiquidityStressIndex> {
  return request<LiquidityStressIndex>("/api/v1/kpi/liquidity-stress");
}

export async function getRepoEquityBeta(): Promise<RepoEquityBeta[]> {
  return request<RepoEquityBeta[]>("/api/v1/kpi/repo-equity-beta");
}

export async function getRrpDrain(): Promise<RrpDrainVelocity> {
  return request<RrpDrainVelocity>("/api/v1/kpi/rrp-drain");
}

export async function getVolatilityRegime(): Promise<VolatilityRegimeData> {
  return request<VolatilityRegimeData>("/api/v1/kpi/volatility-regime");
}

export async function getCorrelationMatrix(): Promise<CorrelationEntry[]> {
  return request<CorrelationEntry[]>("/api/v1/kpi/correlation-matrix");
}

export async function getSystemicRiskHeatmap(): Promise<SystemicRiskHeatmap> {
  return request<SystemicRiskHeatmap>("/api/v1/kpi/systemic-risk-heatmap");
}

// ── Health endpoint ─────────────────────────────────────────────────────

export async function getHealth(): Promise<HealthResponse> {
  return request<HealthResponse>("/api/v1/health");
}

// ── Config endpoints ────────────────────────────────────────────────────

export async function getConfig(): Promise<ConfigEntry[]> {
  return request<ConfigEntry[]>("/api/v1/config");
}

export async function updateConfig(
  entries: ConfigEntry[],
): Promise<void> {
  return request<void>("/api/v1/config", {
    method: "PUT",
    body: JSON.stringify(entries),
  });
}

export async function getConfigHistory(): Promise<ConfigHistoryEntry[]> {
  return request<ConfigHistoryEntry[]>("/api/v1/config/history");
}

export { ApiError, API_BASE_URL };
