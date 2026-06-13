import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

const stubQuery = { data: undefined, isLoading: false, isError: false };

vi.mock("@/hooks/useDashboardData", () => ({
  useDashboardData: () => ({
    ili: stubQuery,
    iliHistory: stubQuery,
    liquidityStress: stubQuery,
    repoEquityBeta: stubQuery,
    rrpDrain: stubQuery,
    volatilityRegime: stubQuery,
    correlationMatrix: stubQuery,
    systemicRiskHeatmap: stubQuery,
    health: stubQuery,
    config: stubQuery,
    configHistory: stubQuery,
    signals: { signals: [], isLoading: false, error: null },
  }),
}));

vi.mock("lightweight-charts", () => ({
  createChart: vi.fn(() => ({
    addSeries: vi.fn(() => ({ setData: vi.fn() })),
    priceScale: vi.fn(() => ({ applyOptions: vi.fn() })),
    remove: vi.fn(),
  })),
  CandlestickSeries: "CandlestickSeries",
  LineSeries: "LineSeries",
  createSeriesMarkers: vi.fn(() => ({ setMarkers: vi.fn() })),
}));

import { DashboardClient } from "@/components/dashboard/DashboardClient";

function renderWithProvider() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <DashboardClient />
    </QueryClientProvider>,
  );
}

describe("DashboardClient", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the tab bar with the Overview section active by default", () => {
    renderWithProvider();
    expect(screen.getByTestId("section-tabs")).toBeInTheDocument();
    expect(screen.getByTestId("tab-overview")).toHaveAttribute("aria-selected", "true");
    expect(screen.getByTestId("section-overview")).toBeInTheDocument();
    expect(screen.getByTestId("price-ili-chart")).toBeInTheDocument();
  });

  it("switches to the Charts section and unmounts Overview when the Charts tab is clicked", () => {
    renderWithProvider();
    fireEvent.click(screen.getByTestId("tab-charts"));
    expect(screen.getByTestId("tab-charts")).toHaveAttribute("aria-selected", "true");
    expect(screen.getByTestId("section-charts")).toBeInTheDocument();
    expect(screen.queryByTestId("section-overview")).not.toBeInTheDocument();
    expect(screen.queryByTestId("price-ili-chart")).not.toBeInTheDocument();
  });

  it("reveals the Config section when the Config tab is clicked", () => {
    renderWithProvider();
    fireEvent.click(screen.getByTestId("tab-config"));
    expect(screen.getByTestId("section-config")).toBeInTheDocument();
    expect(screen.getByTestId("config-readonly")).toBeInTheDocument();
  });

  it("mounts the Risk and Trading sections on their respective tabs", () => {
    renderWithProvider();
    fireEvent.click(screen.getByTestId("tab-risk"));
    expect(screen.getByTestId("section-risk")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("tab-trading"));
    expect(screen.getByTestId("section-trading")).toBeInTheDocument();
  });
});
