import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ConvergencePlot } from "@/components/charts/ConvergencePlot";

describe("ConvergencePlot", () => {
  const defaultProps = {
    cumulativeMean: [0.1, 0.12, 0.115, 0.118, 0.12],
    cumulativeVariance: [0.05, 0.04, 0.038, 0.037, 0.036],
    sampleSize: 50000,
    stable: true,
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<ConvergencePlot {...defaultProps} isLoading />);

    expect(screen.queryByText("Convergence Diagnostics")).not.toBeInTheDocument();
    expect(screen.queryByTestId("stability-indicator")).not.toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows heading and stability indicator", () => {
    render(<ConvergencePlot {...defaultProps} />);

    expect(screen.getByText("Convergence Diagnostics")).toBeInTheDocument();
    expect(screen.getByTestId("stability-indicator")).toBeInTheDocument();
  });

  it("Given stable is true, When rendered, Then shows Stable status", () => {
    render(<ConvergencePlot {...defaultProps} />);

    expect(screen.getByText("Stable")).toBeInTheDocument();
  });

  it("Given stable is false, When rendered, Then shows Unstable status", () => {
    render(<ConvergencePlot {...defaultProps} stable={false} />);

    expect(screen.getByText("Unstable")).toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then displays last cumulative mean and variance values", () => {
    render(<ConvergencePlot {...defaultProps} />);

    expect(screen.getByTestId("mean-value")).toHaveTextContent("0.1200");
    expect(screen.getByTestId("variance-value")).toHaveTextContent("0.0360");
  });

  it("Given valid props, When rendered, Then displays sample size", () => {
    render(<ConvergencePlot {...defaultProps} />);

    expect(screen.getByTestId("sample-size")).toHaveTextContent("50,000");
  });

  it("Given empty cumulative arrays, When rendered, Then shows zero values for mean and variance", () => {
    render(<ConvergencePlot {...defaultProps} cumulativeMean={[]} cumulativeVariance={[]} />);

    expect(screen.getByTestId("mean-value")).toHaveTextContent("0.0000");
    expect(screen.getByTestId("variance-value")).toHaveTextContent("0.0000");
  });

  it("Given valid props, When rendered, Then shows Cumulative Mean and Cumulative Variance labels", () => {
    render(<ConvergencePlot {...defaultProps} />);

    expect(screen.getByText("Cumulative Mean")).toBeInTheDocument();
    expect(screen.getByText("Cumulative Variance")).toBeInTheDocument();
  });
});
