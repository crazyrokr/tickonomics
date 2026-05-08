import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DynamicStopsPanel } from "@/components/panels/DynamicStopsPanel";

const mockPositions = [
  { symbol: "AAPL", stopLoss: 185.50, takeProfit: 210.00, state: "signal-active" as const },
  { symbol: "MSFT", stopLoss: 380.00, takeProfit: 420.50, state: "signal-exhausted" as const },
];

describe("DynamicStopsPanel", () => {
  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<DynamicStopsPanel positions={[]} isLoading />);

    expect(screen.queryByText("Dynamic Stops")).not.toBeInTheDocument();
    expect(screen.queryByTestId("stop-AAPL")).not.toBeInTheDocument();
  });

  it("Given empty positions, When rendered, Then shows empty state message", () => {
    render(<DynamicStopsPanel positions={[]} />);

    expect(screen.getByText("Dynamic Stops")).toBeInTheDocument();
    expect(screen.getByText("No active positions")).toBeInTheDocument();
    expect(screen.queryByTestId("stop-AAPL")).not.toBeInTheDocument();
  });

  it("Given valid positions, When rendered, Then shows all position cards", () => {
    render(<DynamicStopsPanel positions={mockPositions} />);

    expect(screen.getByText("Dynamic Stops")).toBeInTheDocument();
    expect(screen.getByTestId("stop-AAPL")).toBeInTheDocument();
    expect(screen.getByTestId("stop-MSFT")).toBeInTheDocument();
  });

  it("Given valid positions, When rendered, Then displays symbol names", () => {
    render(<DynamicStopsPanel positions={mockPositions} />);

    expect(screen.getByText("AAPL")).toBeInTheDocument();
    expect(screen.getByText("MSFT")).toBeInTheDocument();
  });

  it("Given valid positions, When rendered, Then displays stop loss and take profit values", () => {
    render(<DynamicStopsPanel positions={mockPositions} />);

    expect(screen.getByText("185.50")).toBeInTheDocument();
    expect(screen.getByText("210.00")).toBeInTheDocument();
    expect(screen.getByText("380.00")).toBeInTheDocument();
    expect(screen.getByText("420.50")).toBeInTheDocument();
  });

  it("Given signal-active position, When rendered, Then shows Active label", () => {
    render(<DynamicStopsPanel positions={[mockPositions[0]]} />);

    expect(screen.getByText("Active")).toBeInTheDocument();
  });

  it("Given signal-exhausted position, When rendered, Then shows Exhausted label", () => {
    render(<DynamicStopsPanel positions={[mockPositions[1]]} />);

    expect(screen.getByText("Exhausted")).toBeInTheDocument();
  });

  it("Given valid positions, When rendered, Then shows Stop Loss and Take Profit column labels", () => {
    render(<DynamicStopsPanel positions={mockPositions} />);

    const stopLossLabels = screen.getAllByText("Stop Loss");
    const takeProfitLabels = screen.getAllByText("Take Profit");
    expect(stopLossLabels.length).toBe(2);
    expect(takeProfitLabels.length).toBe(2);
  });
});
