import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TraderTypePanel } from "@/components/panels/TraderTypePanel";

const mockData = [
  { symbol: "AAPL", algorithmic: 40, institutional: 25, professional: 20, retail: 15, spreadDiff: -1.5 },
  { symbol: "MSFT", algorithmic: 30, institutional: 35, professional: 25, retail: 10, spreadDiff: 2.3 },
];

describe("TraderTypePanel", () => {
  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<TraderTypePanel data={[]} isLoading />);

    expect(screen.queryByText("Trader Type Decomposition")).not.toBeInTheDocument();
    expect(screen.queryByTestId("trader-row-AAPL")).not.toBeInTheDocument();
  });

  it("Given empty data, When rendered, Then shows empty state message", () => {
    render(<TraderTypePanel data={[]} />);

    expect(screen.getByText("Trader Type Decomposition")).toBeInTheDocument();
    expect(screen.getByText("No trader type data available")).toBeInTheDocument();
    expect(screen.queryByTestId("trader-row-AAPL")).not.toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows trader rows with symbols and spread values", () => {
    render(<TraderTypePanel data={mockData} />);

    expect(screen.getByText("Trader Type Decomposition")).toBeInTheDocument();
    expect(screen.getByTestId("trader-row-AAPL")).toBeInTheDocument();
    expect(screen.getByTestId("trader-row-MSFT")).toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then displays spread values with correct signs", () => {
    render(<TraderTypePanel data={mockData} />);

    expect(screen.getByTestId("spread-AAPL")).toHaveTextContent("spread: -1.50bps");
    expect(screen.getByTestId("spread-MSFT")).toHaveTextContent("spread: +2.30bps");
  });

  it("Given valid data, When rendered, Then renders stacked bar segments for each trader type", () => {
    render(<TraderTypePanel data={mockData} />);

    expect(screen.getByTestId("bar-AAPL")).toBeInTheDocument();
    expect(screen.getByTestId("bar-MSFT")).toBeInTheDocument();

    expect(screen.getByTestId("segment-AAPL-algorithmic")).toBeInTheDocument();
    expect(screen.getByTestId("segment-AAPL-institutional")).toBeInTheDocument();
    expect(screen.getByTestId("segment-AAPL-professional")).toBeInTheDocument();
    expect(screen.getByTestId("segment-AAPL-retail")).toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows legend with all trader types", () => {
    render(<TraderTypePanel data={mockData} />);

    expect(screen.getByText("Algorithmic")).toBeInTheDocument();
    expect(screen.getByText("Institutional")).toBeInTheDocument();
    expect(screen.getByText("Professional")).toBeInTheDocument();
    expect(screen.getByText("Retail")).toBeInTheDocument();
  });

  it("Given row with all zero values, When rendered, Then skips that row entirely", () => {
    const zeroRow = [
      { symbol: "ZERO", algorithmic: 0, institutional: 0, professional: 0, retail: 0, spreadDiff: 0 },
    ];

    render(<TraderTypePanel data={zeroRow} />);

    expect(screen.queryByTestId("trader-row-ZERO")).not.toBeInTheDocument();
  });
});
