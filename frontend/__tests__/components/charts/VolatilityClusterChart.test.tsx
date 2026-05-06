import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { VolatilityClusterChart } from "@/components/charts/VolatilityClusterChart";

describe("VolatilityClusterChart", () => {
  const data = [
    { timestamp: "2026-05-28", regime: "LOW_VOL" as const },
    { timestamp: "2026-05-29", regime: "LOW_VOL" as const },
    { timestamp: "2026-05-30", regime: "NORMAL" as const },
    { timestamp: "2026-05-31", regime: "HIGH_VOL" as const },
  ];

  it("shows loading skeleton", () => {
    render(<VolatilityClusterChart data={[]} currentRegime="NORMAL" isLoading />);
    expect(screen.queryByText("Volatility Clusters")).not.toBeInTheDocument();
  });

  it("renders current regime badge", () => {
    render(<VolatilityClusterChart data={data} currentRegime="HIGH_VOL" />);
    expect(screen.getByTestId("current-regime-badge")).toHaveTextContent("High Volatility");
  });

  it("renders cluster bar", () => {
    render(<VolatilityClusterChart data={data} currentRegime="NORMAL" />);
    expect(screen.getByTestId("cluster-bar")).toBeInTheDocument();
  });

  it("shows duration percentages", () => {
    render(<VolatilityClusterChart data={data} currentRegime="NORMAL" />);
    expect(screen.getByText(/Low Volatility: 50%/)).toBeInTheDocument();
    expect(screen.getByText(/Normal: 25%/)).toBeInTheDocument();
    expect(screen.getByText(/High Volatility: 25%/)).toBeInTheDocument();
  });

  it("handles empty data", () => {
    render(<VolatilityClusterChart data={[]} currentRegime="NORMAL" />);
    expect(screen.getByText(/Normal: 0%/)).toBeInTheDocument();
  });
});
