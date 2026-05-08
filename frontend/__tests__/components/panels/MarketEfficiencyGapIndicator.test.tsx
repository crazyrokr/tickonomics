import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MarketEfficiencyGapIndicator } from "@/components/panels/MarketEfficiencyGapIndicator";

const mockBreakdown = [
  { asset: "AAPL", atVolume: 120.5, fundamentalVolume: 80.3 },
  { asset: "MSFT", atVolume: 95.0, fundamentalVolume: 110.2 },
];

describe("MarketEfficiencyGapIndicator", () => {
  const defaultProps = {
    gap: 0.35,
    atDriven: false,
    breakdown: mockBreakdown,
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<MarketEfficiencyGapIndicator {...defaultProps} isLoading />);

    expect(screen.queryByText("Market Efficiency Gap")).not.toBeInTheDocument();
    expect(screen.queryByTestId("gap-value")).not.toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows heading and gap value", () => {
    render(<MarketEfficiencyGapIndicator {...defaultProps} />);

    expect(screen.getByText("Market Efficiency Gap")).toBeInTheDocument();
    expect(screen.getByTestId("gap-value")).toHaveTextContent("0.350");
  });

  it("Given valid props, When rendered, Then shows the gap progress bar", () => {
    render(<MarketEfficiencyGapIndicator {...defaultProps} />);

    expect(screen.getByTestId("gap-bar")).toBeInTheDocument();
  });

  it("Given atDriven is true, When rendered, Then shows the AT-Driven badge", () => {
    render(<MarketEfficiencyGapIndicator {...defaultProps} atDriven />);

    expect(screen.getByTestId("at-driven-badge")).toHaveTextContent("AT-Driven");
  });

  it("Given atDriven is false, When rendered, Then does not show the AT-Driven badge", () => {
    render(<MarketEfficiencyGapIndicator {...defaultProps} />);

    expect(screen.queryByTestId("at-driven-badge")).not.toBeInTheDocument();
  });

  it("Given breakdown data, When rendered, Then shows the breakdown table with asset rows", () => {
    render(<MarketEfficiencyGapIndicator {...defaultProps} />);

    expect(screen.getByTestId("breakdown-table")).toBeInTheDocument();
    expect(screen.getByTestId("breakdown-AAPL")).toBeInTheDocument();
    expect(screen.getByTestId("breakdown-MSFT")).toBeInTheDocument();
  });

  it("Given breakdown data, When rendered, Then displays AT Volume and Fundamental values", () => {
    render(<MarketEfficiencyGapIndicator {...defaultProps} />);

    expect(screen.getByText("120.50")).toBeInTheDocument();
    expect(screen.getByText("80.30")).toBeInTheDocument();
    expect(screen.getByText("95.00")).toBeInTheDocument();
    expect(screen.getByText("110.20")).toBeInTheDocument();
  });

  it("Given empty breakdown, When rendered, Then does not show the breakdown table", () => {
    render(<MarketEfficiencyGapIndicator gap={0.1} atDriven={false} breakdown={[]} />);

    expect(screen.queryByTestId("breakdown-table")).not.toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows table column headers", () => {
    render(<MarketEfficiencyGapIndicator {...defaultProps} />);

    expect(screen.getByText("AT Volume")).toBeInTheDocument();
    expect(screen.getByText("Fundamental")).toBeInTheDocument();
  });
});
