import { describe, it, expect, vi, afterEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useConfig, useConfigHistory, useUpdateConfig } from "@/hooks/useConfig";

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
}

function wrapperFor(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function jsonResponse(body: unknown) {
  return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(body) });
}

function configGetCount(mock: ReturnType<typeof vi.fn>) {
  return mock.mock.calls.filter(
    (c) =>
      typeof c[0] === "string" &&
      (c[0] as string).endsWith("/api/v1/config") &&
      (c[1] as RequestInit | undefined)?.method !== "PUT",
  ).length;
}

describe("useConfig / useUpdateConfig", () => {
  afterEach(() => vi.restoreAllMocks());

  it("PUTs entries on update and invalidates the config query so it refetches", async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (init?.method === "PUT") return jsonResponse(undefined);
      if (url.endsWith("/api/v1/config")) return jsonResponse([{ key: "threshold", value: 0.6 }]);
      if (url.endsWith("/api/v1/config/history"))
        return jsonResponse([{ id: "h1", timestamp: "", author: "", auditReason: "calibration", diff: "" }]);
      return jsonResponse([]);
    });
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const client = makeClient();
    const config = renderHook(() => useConfig(), { wrapper: wrapperFor(client) });
    renderHook(() => useConfigHistory(), { wrapper: wrapperFor(client) });
    const update = renderHook(() => useUpdateConfig(), { wrapper: wrapperFor(client) });

    await waitFor(() => expect(config.result.current.data).toHaveLength(1));

    const beforeUpdate = configGetCount(fetchMock);

    await update.result.current.mutateAsync([{ key: "threshold", value: 0.6 }]);

    const putCall = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === "PUT");
    expect(putCall?.[0]).toContain("/api/v1/config");

    await waitFor(() => {
      expect(configGetCount(fetchMock)).toBeGreaterThan(beforeUpdate);
    });
  });
});
