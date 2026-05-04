import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { LiquidityStressGauge } from "@/components/kpi/LiquidityStressGauge";
import type { LiquidityStressIndex } from "@/types/api";

describe("LiquidityStressGauge", () => {
  it("shows loading skeleton", () => {
    render(<LiquidityStressGauge isLoading />);
    expect(screen.queryByText("Liquidity Stress Index")).not.toBeInTheDocument();
  });

  it("renders negative stress value (stable)", () => {
    const data: LiquidityStressIndex = {
      value: -0.5,
      trend: "STABLE",
      timestamp: "2026-05-30T12:00:00Z",
    };
    render(<LiquidityStressGauge data={data} />);
    expect(screen.getByText("-0.500")).toBeInTheDocument();
    expect(screen.getByText("→")).toBeInTheDocument();
  });

  it("renders accelerating stress trend", () => {
    const data: LiquidityStressIndex = {
      value: 0.8,
      trend: "ACCELERATING",
      timestamp: "2026-05-30T12:00:00Z",
    };
    render(<LiquidityStressGauge data={data} />);
    expect(screen.getByText("↑")).toBeInTheDocument();
  });

  it("renders decelerating stress trend", () => {
    const data: LiquidityStressIndex = {
      value: 0.2,
      trend: "DECELERATING",
      timestamp: "2026-05-30T12:00:00Z",
    };
    render(<LiquidityStressGauge data={data} />);
    expect(screen.getByText("↓")).toBeInTheDocument();
  });

  it("renders gauge fill bar", () => {
    const data: LiquidityStressIndex = {
      value: 0.5,
      trend: "STABLE",
      timestamp: "2026-05-30T12:00:00Z",
    };
    render(<LiquidityStressGauge data={data} />);
    const fill = screen.getByTestId("stress-gauge-fill");
    expect(fill).toBeInTheDocument();
  });
});
