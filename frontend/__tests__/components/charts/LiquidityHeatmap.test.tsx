import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { LiquidityHeatmap } from "@/components/charts/LiquidityHeatmap";
import type { SystemicRiskHeatmap } from "@/types/api";

vi.mock("@finos/perspective", () => ({
  worker: vi.fn(() => Promise.resolve({
    table: vi.fn(() => "mock-table"),
  })),
}));

vi.mock("@finos/perspective-viewer", () => ({}));
vi.mock("@finos/perspective-viewer-d3fc", () => ({}));

const mockHeatmap: SystemicRiskHeatmap[] = [
  {
    triPartyGcfSpread: 0.15,
    sofrPctlRange: 0.08,
    tgcrBgcrSpread: 0.03,
    tgaBalanceChange: -2.5,
    timestamp: "2026-05-30T12:00:00Z",
  },
];

describe("LiquidityHeatmap", () => {
  it("shows loading skeleton", () => {
    render(<LiquidityHeatmap data={[]} isLoading />);
    expect(screen.queryByText("Systemic Risk Heatmap")).not.toBeInTheDocument();
  });

  it("renders 4-axis quadrant with D3 mode for small datasets", () => {
    render(<LiquidityHeatmap data={mockHeatmap} />);
    expect(screen.getByText("Systemic Risk Heatmap")).toBeInTheDocument();
    expect(screen.getByTestId("d3-heatmap")).toBeInTheDocument();
    expect(screen.getByText("Tri-Party / GCF Spread")).toBeInTheDocument();
    expect(screen.getByText("SOFR 99th-25th Pctl")).toBeInTheDocument();
    expect(screen.getByText("TGCR / BGCR Spread")).toBeInTheDocument();
    expect(screen.getByText("TGA Balance Change")).toBeInTheDocument();
  });

  it("shows empty state when no data", () => {
    render(<LiquidityHeatmap data={[]} />);
    expect(screen.getByText("No heatmap data available")).toBeInTheDocument();
  });

  it("renders axis values", () => {
    render(<LiquidityHeatmap data={mockHeatmap} />);
    expect(screen.getByText("0.1500")).toBeInTheDocument();
    expect(screen.getByText("-2.5000")).toBeInTheDocument();
  });
});
