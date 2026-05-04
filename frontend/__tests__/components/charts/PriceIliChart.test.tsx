import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { PriceIliChart } from "@/components/charts/PriceIliChart";
import { SIGNAL_MARKER_COLORS, ILI_STATUS_COLORS } from "@/components/charts/PriceIliChart";

vi.mock("lightweight-charts", () => ({
  createChart: vi.fn(() => ({
    addSeries: vi.fn(() => ({
      setData: vi.fn(),
    })),
    priceScale: vi.fn(() => ({
      applyOptions: vi.fn(),
    })),
    remove: vi.fn(),
  })),
  CandlestickSeries: "CandlestickSeries",
  LineSeries: "LineSeries",
  createSeriesMarkers: vi.fn(() => ({
    setMarkers: vi.fn(),
  })),
}));

describe("PriceIliChart", () => {
  const priceData = [
    { time: "2026-05-30T10:00:00Z", open: 100, high: 105, low: 98, close: 103 },
    { time: "2026-05-30T10:01:00Z", open: 103, high: 107, low: 102, close: 106 },
  ];

  const iliData = [
    { timestamp: "2026-05-30T10:00:00Z", value: 0.5, status: "VALID" as const },
    { timestamp: "2026-05-30T10:01:00Z", value: 0.8, status: "DISLOCATED" as const },
  ];

  it("renders chart container", () => {
    render(<PriceIliChart priceData={priceData} iliData={iliData} />);
    expect(screen.getByTestId("price-ili-chart")).toBeInTheDocument();
  });

  it("renders with empty data without crashing", () => {
    render(<PriceIliChart priceData={[]} iliData={[]} />);
    expect(screen.getByTestId("price-ili-chart")).toBeInTheDocument();
  });
});

describe("SIGNAL_MARKER_COLORS", () => {
  it("maps all signal status codes to hex colors", () => {
    const codes = [
      "ACTIONABLE",
      "SPECULATIVE_STALE_MACRO",
      "COST_EXCEEDS_EXPECTED_MOVE",
      "COOLDOWN",
      "INSUFFICIENT_DATA",
    ] as const;
    for (const code of codes) {
      expect(SIGNAL_MARKER_COLORS[code]).toBeDefined();
      expect(SIGNAL_MARKER_COLORS[code]).toMatch(/^#[0-9a-f]{6}$/);
    }
  });
});

describe("ILI_STATUS_COLORS", () => {
  it("maps all ILI statuses to colors", () => {
    expect(ILI_STATUS_COLORS.VALID).toBe("#22c55e");
    expect(ILI_STATUS_COLORS.DEGRADED_COMPONENT_STALE).toBe("#f59e0b");
    expect(ILI_STATUS_COLORS.DISLOCATED).toBe("#ef4444");
  });
});
