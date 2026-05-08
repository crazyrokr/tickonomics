import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SignalExplainabilityPanel } from "@/components/panels/SignalExplainabilityPanel";

describe("SignalExplainabilityPanel", () => {
  const mockExplanation = {
    signalId: "sig-001",
    zScores: { momentum: 2.34, volatility: -1.12, liquidity: 0.56 },
    percentileRanks: { momentum: 98.2, volatility: 12.5, liquidity: 67.3 },
    activeFilters: ["momentum_threshold", "volatility_cap"],
    iliFormula: "ILI = w1*momentum + w2*volatility + w3*liquidity",
  };

  it("shows loading skeleton when isLoading is true", () => {
    // Given the panel is in a loading state
    render(<SignalExplainabilityPanel explanation={undefined} isLoading />);

    // Then the explainability content should not be rendered
    expect(screen.queryByTestId("signal-explainability")).not.toBeInTheDocument();
    // And the skeleton container should be visible
    const skeleton = document.querySelector(".animate-pulse");
    expect(skeleton).toBeInTheDocument();
  });

  it("renders the ILI formula", () => {
    // Given valid signal explanation data
    render(<SignalExplainabilityPanel explanation={mockExplanation} />);

    // Then the formula element should display the ILI formula string
    const formula = screen.getByTestId("ili-formula");
    expect(formula).toHaveTextContent(
      "ILI = w1*momentum + w2*volatility + w3*liquidity"
    );
  });

  it("renders z-scores with corresponding percentile ranks", () => {
    // Given explanation data with three z-score entries
    render(<SignalExplainabilityPanel explanation={mockExplanation} />);

    // Then each z-score entry should be rendered with its value and percentile
    const momentumRow = screen.getByTestId("zscore-momentum");
    expect(momentumRow).toHaveTextContent("2.340");
    expect(momentumRow).toHaveTextContent("98.2%");

    const volatilityRow = screen.getByTestId("zscore-volatility");
    expect(volatilityRow).toHaveTextContent("-1.120");
    expect(volatilityRow).toHaveTextContent("12.5%");

    const liquidityRow = screen.getByTestId("zscore-liquidity");
    expect(liquidityRow).toHaveTextContent("0.560");
    expect(liquidityRow).toHaveTextContent("67.3%");
  });

  it("renders active filters as tag elements", () => {
    // Given explanation data with two active filters
    render(<SignalExplainabilityPanel explanation={mockExplanation} />);

    // Then each filter should be rendered
    expect(screen.getByTestId("filter-0")).toHaveTextContent("momentum_threshold");
    expect(screen.getByTestId("filter-1")).toHaveTextContent("volatility_cap");
    // And the "Active Filters" label should be present
    expect(screen.getByText("Active Filters")).toBeInTheDocument();
  });

  it("does not render filters section when activeFilters is empty", () => {
    // Given explanation data with no active filters
    const noFilterExplanation = {
      ...mockExplanation,
      activeFilters: [],
    };
    render(<SignalExplainabilityPanel explanation={noFilterExplanation} />);

    // Then the Active Filters label should not be present
    expect(screen.queryByText("Active Filters")).not.toBeInTheDocument();
    // And the z-scores and formula should still render
    expect(screen.getByTestId("ili-formula")).toBeInTheDocument();
    expect(screen.getByTestId("zscore-momentum")).toBeInTheDocument();
  });
});
