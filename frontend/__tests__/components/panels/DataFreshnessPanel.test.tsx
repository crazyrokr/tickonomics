import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DataFreshnessPanel } from "@/components/panels/DataFreshnessPanel";
import type { HealthResponse } from "@/types/api";

const mockHealth: HealthResponse = {
  sources: [
    { name: "FRED", healthy: true, lastSync: "2026-05-30T12:00:00Z", latencyMs: 120 },
    { name: "NY Fed", healthy: true, lastSync: "2026-05-30T11:59:00Z", latencyMs: 85 },
    { name: "OpenBB", healthy: false, lastSync: "2026-05-30T11:00:00Z", latencyMs: 5000 },
  ],
  proxyDivergence: {
    tbillSofrCorrelation5d: 0.95,
    divergenceScore: 0.3,
    dislocated: false,
    timestamp: "2026-05-30T12:00:00Z",
  },
  timescaleDb: { connected: true, compressionStatus: "active", aggregateLagSeconds: 5 },
  circuitBreakers: {},
};

describe("DataFreshnessPanel", () => {
  it("shows loading skeleton", () => {
    render(<DataFreshnessPanel isLoading />);
    expect(screen.queryByText("Data Freshness")).not.toBeInTheDocument();
  });

  it("renders all data sources", () => {
    render(<DataFreshnessPanel data={mockHealth} />);
    expect(screen.getByText("FRED")).toBeInTheDocument();
    expect(screen.getByText("NY Fed")).toBeInTheDocument();
    expect(screen.getByText("OpenBB")).toBeInTheDocument();
  });

  it("shows green dot for healthy sources", () => {
    render(<DataFreshnessPanel data={mockHealth} />);
    expect(screen.getByTestId("source-dot-FRED").className).toContain("bg-ili-green");
  });

  it("shows red dot for unhealthy sources", () => {
    render(<DataFreshnessPanel data={mockHealth} />);
    expect(screen.getByTestId("source-dot-OpenBB").className).toContain("bg-ili-red");
  });

  it("renders proxy divergence section", () => {
    render(<DataFreshnessPanel data={mockHealth} />);
    expect(screen.getByTestId("proxy-divergence")).toBeInTheDocument();
    expect(screen.getByText(/0\.950/)).toBeInTheDocument();
    expect(screen.getByText(/0\.300/)).toBeInTheDocument();
  });

  it("does not show DISLOCATED badge when not dislocated", () => {
    render(<DataFreshnessPanel data={mockHealth} />);
    expect(screen.queryByText("DISLOCATED")).not.toBeInTheDocument();
  });

  it("shows DISLOCATED badge when proxy is dislocated", () => {
    const dislocated = {
      ...mockHealth,
      proxyDivergence: { ...mockHealth.proxyDivergence, dislocated: true },
    };
    render(<DataFreshnessPanel data={dislocated} />);
    expect(screen.getByText("DISLOCATED")).toBeInTheDocument();
  });
});
