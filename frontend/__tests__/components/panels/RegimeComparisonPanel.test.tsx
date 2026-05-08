import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RegimeComparisonPanel } from "@/components/panels/RegimeComparisonPanel";

describe("RegimeComparisonPanel", () => {
  const consensusModels = [
    { name: "GARCH", regime: "NORMAL", confidence: 0.92 },
    { name: "CNN-LSTM", regime: "NORMAL", confidence: 0.87 },
    { name: "QED", regime: "NORMAL", confidence: 0.78 },
    { name: "RAHF", regime: "NORMAL", confidence: 0.85 },
  ];

  const divergentModels = [
    { name: "GARCH", regime: "NORMAL", confidence: 0.92 },
    { name: "CNN-LSTM", regime: "HIGH_VOL", confidence: 0.65 },
    { name: "QED", regime: "NORMAL", confidence: 0.78 },
    { name: "RAHF", regime: "NORMAL", confidence: 0.85 },
  ];

  it("shows loading skeleton", () => {
    render(<RegimeComparisonPanel models={[]} isLoading />);
    expect(screen.queryByText("Regime Comparison")).not.toBeInTheDocument();
  });

  it("renders all model rows", () => {
    render(<RegimeComparisonPanel models={consensusModels} />);
    expect(screen.getByTestId("model-GARCH")).toBeInTheDocument();
    expect(screen.getByTestId("model-CNN-LSTM")).toBeInTheDocument();
    expect(screen.getByTestId("model-QED")).toBeInTheDocument();
    expect(screen.getByTestId("model-RAHF")).toBeInTheDocument();
  });

  it("shows consensus when models agree", () => {
    render(<RegimeComparisonPanel models={consensusModels} />);
    expect(screen.getByTestId("consensus-indicator")).toHaveTextContent("Consensus: NORMAL");
  });

  it("shows divergent when models disagree", () => {
    render(<RegimeComparisonPanel models={divergentModels} />);
    expect(screen.getByTestId("consensus-indicator")).toHaveTextContent("Divergent: NORMAL");
    expect(screen.getByTestId("divergence-marker")).toHaveTextContent("diverges");
  });

  it("shows confidence percentages", () => {
    render(<RegimeComparisonPanel models={consensusModels} />);
    expect(screen.getByText("(92%)")).toBeInTheDocument();
    expect(screen.getByText("(87%)")).toBeInTheDocument();
  });
});
