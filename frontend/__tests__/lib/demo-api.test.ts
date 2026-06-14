import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

describe("demo api client", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  async function importApi() {
    vi.resetModules();
    return import("@/lib/api");
  }

  function mockFetch(payload: unknown) {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(payload),
    });
  }

  it("fetches the demo portfolio", async () => {
    const portfolio = { balance: 105_000, enabled: true };
    mockFetch(portfolio);

    const { getDemoPortfolio } = await importApi();
    const result = await getDemoPortfolio();

    expect(result).toEqual(portfolio);
    const url = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
    expect(url).toContain("/api/v1/demo/portfolio");
  });

  it("fetches demo trades with limit and offset query params", async () => {
    mockFetch([]);
    const { getDemoTrades } = await importApi();
    await getDemoTrades(10, 20);

    const url = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
    expect(url).toContain("limit=10");
    expect(url).toContain("offset=20");
  });

  it("activates the kill switch with a POST body", async () => {
    mockFetch({ active: true, liquidatedTrades: 2 });
    const { activateKillSwitch } = await importApi();
    const result = await activateKillSwitch({ SPY: 500 });

    expect(result).toEqual({ active: true, liquidatedTrades: 2 });
    const opts = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as RequestInit;
    expect(opts.method).toBe("POST");
    expect(opts.body).toBe(JSON.stringify({ SPY: 500 }));
  });

  it("evaluates leverage rotation via POST", async () => {
    mockFetch({ signal: "LEVERAGE_OFF", benchmarkPrice: 90, sma200: 100, deviation: -0.1, closedTrades: 1 });
    const { evaluateLeverageRotation } = await importApi();
    const result = await evaluateLeverageRotation();

    expect(result.signal).toBe("LEVERAGE_OFF");
    const opts = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as RequestInit;
    expect(opts.method).toBe("POST");
  });
});
