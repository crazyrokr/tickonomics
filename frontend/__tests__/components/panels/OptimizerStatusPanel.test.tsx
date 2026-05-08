import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { OptimizerStatusPanel } from "@/components/panels/OptimizerStatusPanel";

describe("OptimizerStatusPanel", () => {
  const currentRun = {
    method: "Bayesian" as const,
    lastCalibration: "2026-05-30T12:00:00Z",
    fitnessScore: 1.45,
    convergenceStatus: "CONVERGED" as const,
    weightHistory: [
      { date: "2026-05-28", weights: { rrp: 0.3, spread: 0.4, vol: 0.3 } },
      { date: "2026-05-30", weights: { rrp: 0.35, spread: 0.35, vol: 0.3 } },
    ],
  };

  const comparisonRun = {
    method: "Firefly" as const,
    lastCalibration: "2026-05-29T10:00:00Z",
    fitnessScore: 1.32,
    convergenceStatus: "IN_PROGRESS" as const,
    weightHistory: [
      { date: "2026-05-29", weights: { rrp: 0.4, spread: 0.3, vol: 0.3 } },
    ],
  };

  it("shows loading skeleton", () => {
    render(<OptimizerStatusPanel current={undefined} isLoading />);
    expect(screen.queryByText("Optimizer Status")).not.toBeInTheDocument();
  });

  it("renders primary optimizer card", () => {
    render(<OptimizerStatusPanel current={currentRun} />);
    expect(screen.getByTestId("optimizer-primary")).toBeInTheDocument();
    expect(screen.getByText("Bayesian")).toBeInTheDocument();
    expect(screen.getByText("1.450")).toBeInTheDocument();
  });

  it("shows convergence status", () => {
    render(<OptimizerStatusPanel current={currentRun} />);
    expect(screen.getByTestId("convergence-primary")).toHaveTextContent("Converged");
  });

  it("renders weight evolution", () => {
    render(<OptimizerStatusPanel current={currentRun} />);
    expect(screen.getByText(/rrp: 0\.35/)).toBeInTheDocument();
  });

  it("renders A/B comparison when provided", () => {
    render(<OptimizerStatusPanel current={currentRun} comparison={comparisonRun} />);
    expect(screen.getByTestId("optimizer-comparison")).toBeInTheDocument();
    expect(screen.getByText("Firefly")).toBeInTheDocument();
    expect(screen.getByTestId("convergence-comparison")).toHaveTextContent("In Progress");
  });
});
