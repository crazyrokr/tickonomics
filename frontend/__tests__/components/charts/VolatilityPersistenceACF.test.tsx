import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { VolatilityPersistenceACF } from "@/components/charts/VolatilityPersistenceACF";

const mockRawReturns = [
  { lag: 0, acf: 1.0 },
  { lag: 1, acf: 0.05 },
  { lag: 2, acf: -0.02 },
  { lag: 3, acf: 0.01 },
];

const mockAbsoluteReturns = [
  { lag: 0, acf: 1.0 },
  { lag: 1, acf: 0.45 },
  { lag: 2, acf: 0.38 },
  { lag: 3, acf: 0.30 },
];

describe("VolatilityPersistenceACF", () => {
  const defaultProps = {
    rawReturns: mockRawReturns,
    absoluteReturns: mockAbsoluteReturns,
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<VolatilityPersistenceACF {...defaultProps} isLoading />);

    expect(screen.queryByText("Volatility Persistence (ACF)")).not.toBeInTheDocument();
    expect(screen.queryByTestId("acf-chart-raw-returns-acf")).not.toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows heading and both ACF charts", () => {
    render(<VolatilityPersistenceACF {...defaultProps} />);

    expect(screen.getByText("Volatility Persistence (ACF)")).toBeInTheDocument();
    expect(screen.getByTestId("acf-chart-raw-returns-acf")).toBeInTheDocument();
    expect(screen.getByTestId("acf-chart-absolute-returns-acf")).toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then renders ACF bars for each lag", () => {
    render(<VolatilityPersistenceACF {...defaultProps} />);

    expect(screen.getByTestId("acf-bar-raw-returns-acf-0")).toBeInTheDocument();
    expect(screen.getByTestId("acf-bar-raw-returns-acf-1")).toBeInTheDocument();
    expect(screen.getByTestId("acf-bar-absolute-returns-acf-0")).toBeInTheDocument();
    expect(screen.getByTestId("acf-bar-absolute-returns-acf-3")).toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows lag range labels for both charts", () => {
    render(<VolatilityPersistenceACF {...defaultProps} />);

    const lag0Labels = screen.getAllByText("Lag 0");
    const lag3Labels = screen.getAllByText("Lag 3");
    expect(lag0Labels.length).toBe(2);
    expect(lag3Labels.length).toBe(2);
  });

  it("Given valid data, When rendered, Then shows the explanatory footer text", () => {
    render(<VolatilityPersistenceACF {...defaultProps} />);

    expect(screen.getByText(/Raw returns show weak autocorrelation/)).toBeInTheDocument();
  });

  it("Given empty raw returns data, When rendered, Then shows No Data for raw returns chart", () => {
    render(<VolatilityPersistenceACF rawReturns={[]} absoluteReturns={mockAbsoluteReturns} />);

    expect(screen.getByText("Raw Returns ACF")).toBeInTheDocument();
    expect(screen.getByText("No data")).toBeInTheDocument();
  });

  it("Given empty absolute returns data, When rendered, Then shows No Data for absolute returns chart", () => {
    render(<VolatilityPersistenceACF rawReturns={mockRawReturns} absoluteReturns={[]} />);

    expect(screen.getByText("Absolute Returns ACF")).toBeInTheDocument();
  });
});
