import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SystemHealthPanel } from "@/components/panels/SystemHealthPanel";
import type { HealthResponse } from "@/types/api";

const mockHealth: HealthResponse = {
  sources: [],
  proxyDivergence: {
    tbillSofrCorrelation5d: 0.95,
    divergenceScore: 0.3,
    dislocated: false,
    timestamp: "2026-05-30T12:00:00Z",
  },
  timescaleDb: { connected: true, compressionStatus: "active", aggregateLagSeconds: 5 },
  circuitBreakers: { polygonWs: "CLOSED", openbb: "OPEN", analyticsWorker: "HALF_OPEN" },
};

describe("SystemHealthPanel", () => {
  it("shows loading skeleton", () => {
    render(<SystemHealthPanel isLoading />);
    expect(screen.queryByText("System Health")).not.toBeInTheDocument();
  });

  it("shows TimescaleDB connected status", () => {
    render(<SystemHealthPanel data={mockHealth} />);
    expect(screen.getByText("Connected")).toBeInTheDocument();
    expect(screen.getByText(/Compression: active/)).toBeInTheDocument();
    expect(screen.getByText(/Lag: 5s/)).toBeInTheDocument();
  });

  it("shows disconnected status when TimescaleDB is down", () => {
    const down = {
      ...mockHealth,
      timescaleDb: { ...mockHealth.timescaleDb, connected: false },
    };
    render(<SystemHealthPanel data={down} />);
    expect(screen.getByText("Disconnected")).toBeInTheDocument();
  });

  it("renders all circuit breakers", () => {
    render(<SystemHealthPanel data={mockHealth} />);
    expect(screen.getByText("polygonWs")).toBeInTheDocument();
    expect(screen.getByText("openbb")).toBeInTheDocument();
    expect(screen.getByText("analyticsWorker")).toBeInTheDocument();
  });

  it("shows correct circuit breaker states", () => {
    render(<SystemHealthPanel data={mockHealth} />);
    expect(screen.getByTestId("cb-polygonWs")).toHaveTextContent("Closed");
    expect(screen.getByTestId("cb-openbb")).toHaveTextContent("Open");
    expect(screen.getByTestId("cb-analyticsWorker")).toHaveTextContent("Half-Open");
  });

  it("applies correct color classes to circuit breaker states", () => {
    render(<SystemHealthPanel data={mockHealth} />);
    expect(screen.getByTestId("cb-polygonWs").className).toContain("bg-ili-green");
    expect(screen.getByTestId("cb-openbb").className).toContain("bg-ili-red");
    expect(screen.getByTestId("cb-analyticsWorker").className).toContain("bg-ili-amber");
  });
});
