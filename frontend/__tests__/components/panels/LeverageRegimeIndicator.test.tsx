import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { LeverageRegimeIndicator } from "@/components/panels/LeverageRegimeIndicator";

const mockData = [
  { timestamp: "2026-05-28T10:00:00Z", price: 5200, ma: 5150 },
  { timestamp: "2026-05-29T10:00:00Z", price: 5250, ma: 5160 },
  { timestamp: "2026-05-30T10:00:00Z", price: 5300, ma: 5170 },
];

describe("LeverageRegimeIndicator", () => {
  const defaultProps = {
    regime: "LEVERAGE_ON" as const,
    sp500Position: 5300.45,
    ma200: 5150.20,
    data: mockData,
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<LeverageRegimeIndicator {...defaultProps} isLoading />);

    expect(screen.queryByText("Leverage Regime")).not.toBeInTheDocument();
    expect(screen.queryByTestId("regime-badge")).not.toBeInTheDocument();
  });

  it("Given LEVERAGE_ON regime, When rendered, Then shows Leverage ON badge", () => {
    render(<LeverageRegimeIndicator {...defaultProps} />);

    expect(screen.getByText("Leverage Regime")).toBeInTheDocument();
    expect(screen.getByTestId("regime-badge")).toHaveTextContent("Leverage ON");
  });

  it("Given LEVERAGE_OFF regime, When rendered, Then shows Leverage OFF badge", () => {
    render(<LeverageRegimeIndicator {...defaultProps} regime="LEVERAGE_OFF" />);

    expect(screen.getByTestId("regime-badge")).toHaveTextContent("Leverage OFF");
  });

  it("Given valid props, When rendered, Then displays S&P 500 and 200-Day MA values", () => {
    render(<LeverageRegimeIndicator {...defaultProps} />);

    expect(screen.getByText("S&P 500")).toBeInTheDocument();
    expect(screen.getByText("200-Day MA")).toBeInTheDocument();
    expect(screen.getByText("5300.45")).toBeInTheDocument();
    expect(screen.getByText("5150.20")).toBeInTheDocument();
  });

  it("Given position above MA, When rendered, Then shows positive distance percentage", () => {
    render(<LeverageRegimeIndicator {...defaultProps} />);

    expect(screen.getByTestId("position-vs-ma")).toHaveTextContent("+2.92%");
  });

  it("Given position below MA, When rendered, Then shows negative distance percentage", () => {
    render(<LeverageRegimeIndicator {...defaultProps} sp500Position={5000} ma200={5150} />);

    expect(screen.getByTestId("position-vs-ma")).toHaveTextContent("-2.91%");
  });

  it("Given data with multiple points, When rendered, Then shows regime spark chart", () => {
    render(<LeverageRegimeIndicator {...defaultProps} />);

    expect(screen.getByTestId("regime-spark")).toBeInTheDocument();
  });

  it("Given data with fewer than 2 points, When rendered, Then does not show spark chart", () => {
    render(<LeverageRegimeIndicator {...defaultProps} data={[]} />);

    expect(screen.queryByTestId("regime-spark")).not.toBeInTheDocument();
  });
});
