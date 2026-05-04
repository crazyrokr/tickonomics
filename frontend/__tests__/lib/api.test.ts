import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

describe("api client", () => {
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

  it("makes GET request to correct URL", async () => {
    const mockResponse = [{ id: "1", name: "test", active: true }];
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResponse),
    });

    const { getActiveStrategies } = await importApi();
    const result = await getActiveStrategies();

    expect(fetch).toHaveBeenCalledTimes(1);
    const calledUrl = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
    expect(calledUrl).toContain("/api/v1/quant/strategies/active");
    expect(result).toEqual(mockResponse);
  });

  it("includes Authorization header when auth_token cookie exists", async () => {
    document.cookie = "auth_token=test-jwt; Path=/";
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    });

    const { getTailParameters } = await importApi();
    await getTailParameters();

    const callOpts = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as RequestInit;
    const headers = callOpts.headers as Record<string, string>;
    expect(headers["Authorization"]).toBe("Bearer test-jwt");

    document.cookie = "auth_token=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT";
  });

  it("throws ApiError with status 401 on unauthorized response", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      statusText: "Unauthorized",
      json: () => Promise.resolve(null),
    });

    const originalLocation = window.location;
    const mockHref = vi.fn();
    Object.defineProperty(window, "location", {
      value: { href: "", assign: mockHref },
      writable: true,
    });

    const { ApiError, getActiveStrategies } = await importApi();
    await expect(getActiveStrategies()).rejects.toThrow();
    await expect(getActiveStrategies()).rejects.toBeInstanceOf(ApiError);

    Object.defineProperty(window, "location", {
      value: originalLocation,
      writable: true,
    });
  });

  it("throws ApiError with status 403 on forbidden response", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 403,
      statusText: "Forbidden",
    });

    const { ApiError, getHealth } = await importApi();
    try {
      await getHealth();
      expect.unreachable("Should have thrown");
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      expect((err as InstanceType<typeof ApiError>).status).toBe(403);
    }
  });

  it("sends PUT request with JSON body for updateConfig", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(undefined),
    });

    const entries = [{ key: "threshold", value: 0.5 }];
    const { updateConfig } = await importApi();
    await updateConfig(entries);

    const callOpts = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as RequestInit;
    expect(callOpts.method).toBe("PUT");
    expect(JSON.parse(callOpts.body as string)).toEqual(entries);
  });
});
