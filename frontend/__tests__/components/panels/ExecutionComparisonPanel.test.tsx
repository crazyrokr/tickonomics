import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ExecutionComparisonPanel } from "@/components/panels/ExecutionComparisonPanel";

describe("ExecutionComparisonPanel", () => {
  const defaultProps = {
    passivePnl: 150.25,
    aggressivePnl: 95.10,
    priceEfficiency: 0.9876,
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<ExecutionComparisonPanel {...defaultProps} isLoading />);

    expect(screen.queryByText("Execution Mode Comparison")).not.toBeInTheDocument();
    expect(screen.queryByTestId("execution-cards")).not.toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows heading and both execution cards", () => {
    render(<ExecutionComparisonPanel {...defaultProps} />);

    expect(screen.getByText("Execution Mode Comparison")).toBeInTheDocument();
    expect(screen.getByTestId("execution-cards")).toBeInTheDocument();
    expect(screen.getByTestId("passive-card")).toBeInTheDocument();
    expect(screen.getByTestId("aggressive-card")).toBeInTheDocument();
  });

  it("Given positive PnL values, When rendered, Then displays them with plus sign", () => {
    render(<ExecutionComparisonPanel {...defaultProps} />);

    expect(screen.getByTestId("passive-card")).toHaveTextContent("+150.25");
    expect(screen.getByTestId("aggressive-card")).toHaveTextContent("+95.10");
  });

  it("Given negative PnL values, When rendered, Then displays them without plus sign", () => {
    render(<ExecutionComparisonPanel passivePnl={-20.5} aggressivePnl={-35.0} priceEfficiency={0.5} />);

    expect(screen.getByTestId("passive-card")).toHaveTextContent("-20.50");
    expect(screen.getByTestId("aggressive-card")).toHaveTextContent("-35.00");
  });

  it("Given valid props, When rendered, Then displays price efficiency value", () => {
    render(<ExecutionComparisonPanel {...defaultProps} />);

    expect(screen.getByTestId("price-efficiency")).toHaveTextContent("0.9876");
  });

  it("Given passive outperforms aggressive, When rendered, Then shows Passive as optimal mode", () => {
    render(<ExecutionComparisonPanel {...defaultProps} />);

    expect(screen.getByTestId("optimal-mode")).toHaveTextContent("Passive");
  });

  it("Given aggressive outperforms passive, When rendered, Then shows Aggressive as optimal mode", () => {
    render(<ExecutionComparisonPanel passivePnl={50} aggressivePnl={80} priceEfficiency={0.9} />);

    expect(screen.getByTestId("optimal-mode")).toHaveTextContent("Aggressive");
  });

  it("Given equal PnL values, When rendered, Then shows Neutral as optimal mode", () => {
    render(<ExecutionComparisonPanel passivePnl={100} aggressivePnl={100} priceEfficiency={0.9} />);

    expect(screen.getByTestId("optimal-mode")).toHaveTextContent("Neutral");
  });
});
