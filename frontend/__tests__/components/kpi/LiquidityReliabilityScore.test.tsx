import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { LiquidityReliabilityScore } from "@/components/kpi/LiquidityReliabilityScore";

describe("LiquidityReliabilityScore", () => {
  const defaultProps = {
    score: 75.3,
    pliHigh: false,
    history: [60, 65, 70, 73, 75],
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<LiquidityReliabilityScore {...defaultProps} isLoading />);

    expect(screen.queryByText("Liquidity Reliability")).not.toBeInTheDocument();
    expect(screen.queryByTestId("lrs-score")).not.toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows the score value and heading", () => {
    render(<LiquidityReliabilityScore {...defaultProps} />);

    expect(screen.getByText("Liquidity Reliability")).toBeInTheDocument();
    expect(screen.getByTestId("lrs-score")).toHaveTextContent("75.3");
  });

  it("Given valid props, When rendered, Then shows the progress bar fill", () => {
    render(<LiquidityReliabilityScore {...defaultProps} />);

    expect(screen.getByTestId("lrs-bar-fill")).toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows Stressed and Reliable range labels", () => {
    render(<LiquidityReliabilityScore {...defaultProps} />);

    expect(screen.getByText("Stressed")).toBeInTheDocument();
    expect(screen.getByText("Reliable")).toBeInTheDocument();
  });

  it("Given pliHigh is true, When rendered, Then shows the PLI High badge", () => {
    render(<LiquidityReliabilityScore {...defaultProps} pliHigh />);

    expect(screen.getByTestId("pli-high-badge")).toHaveTextContent("PLI High");
  });

  it("Given pliHigh is false, When rendered, Then does not show the PLI High badge", () => {
    render(<LiquidityReliabilityScore {...defaultProps} />);

    expect(screen.queryByTestId("pli-high-badge")).not.toBeInTheDocument();
  });

  it("Given a low score, When rendered, Then displays the correct numeric value", () => {
    render(<LiquidityReliabilityScore {...defaultProps} score={15.0} />);

    expect(screen.getByTestId("lrs-score")).toHaveTextContent("15.0");
  });

  it("Given empty history, When rendered, Then still shows the score without crashing", () => {
    render(<LiquidityReliabilityScore {...defaultProps} history={[]} />);

    expect(screen.getByTestId("lrs-score")).toHaveTextContent("75.3");
  });
});
