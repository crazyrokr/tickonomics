import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RrpDrainTrend } from "@/components/kpi/RrpDrainTrend";
import type { RrpDrainVelocity } from "@/types/api";

const baseData: RrpDrainVelocity = {
  velocity: 25.3,
  dayOverDayChange: 1.2,
  trend: "ACCELERATING",
  history: [
    { timestamp: "2026-05-28T00:00:00Z", velocity: 20 },
    { timestamp: "2026-05-29T00:00:00Z", velocity: 24 },
    { timestamp: "2026-05-30T00:00:00Z", velocity: 25.3 },
  ],
};

describe("RrpDrainTrend", () => {
  it("shows loading skeleton", () => {
    render(<RrpDrainTrend isLoading />);
    expect(screen.queryByText("RRP Drain Velocity")).not.toBeInTheDocument();
  });

  it("renders velocity value in billions", () => {
    render(<RrpDrainTrend data={baseData} />);
    expect(screen.getByText("25.3B")).toBeInTheDocument();
  });

  it("renders accelerating trend arrow", () => {
    render(<RrpDrainTrend data={baseData} />);
    expect(screen.getByText("↑")).toBeInTheDocument();
  });

  it("renders decelerating trend arrow", () => {
    const data = { ...baseData, trend: "DECELERATING" as const };
    render(<RrpDrainTrend data={data} />);
    expect(screen.getByText("↓")).toBeInTheDocument();
  });

  it("renders stable trend arrow", () => {
    const data = { ...baseData, trend: "STABLE" as const };
    render(<RrpDrainTrend data={data} />);
    expect(screen.getByText("→")).toBeInTheDocument();
  });

  it("renders day-over-day change", () => {
    render(<RrpDrainTrend data={baseData} />);
    expect(screen.getByText("+1.20B day/day")).toBeInTheDocument();
  });

  it("renders sparkline from history", () => {
    const { container } = render(<RrpDrainTrend data={baseData} />);
    expect(container.querySelector("polyline")).toBeInTheDocument();
  });
});
