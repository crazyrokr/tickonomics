import { describe, it, expect, vi, beforeAll } from "vitest";
import { fetchApi } from "@/lib/api";
import { mockSignals } from "@/lib/mock-data";

describe("fetchApi", () => {
  beforeAll(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://localhost:8080");
  });

  it("returns mock fallback on network error", async () => {
    const result = await fetchApi("/api/v1/quant/signals/active");
    expect(result).toEqual(mockSignals);
  });

  it("returns mock fallback on HTTP error", async () => {
    const result = await fetchApi("/api/v1/kpi/ili/history?limit=90");
    expect(Array.isArray(result)).toBe(true);
    expect(result).toHaveLength(90);
  });

  it("throws when no fallback exists for path", async () => {
    await expect(
      fetchApi("/api/v1/nonexistent")
    ).rejects.toThrow("No mock fallback for /api/v1/nonexistent");
  });
});
