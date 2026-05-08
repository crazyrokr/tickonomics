import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PerformanceDualityPanel } from "@/components/panels/PerformanceDualityPanel";

describe("PerformanceDualityPanel", () => {
  const defaultProps = {
    iliReturn: 12.5,
    benchmarkReturn: 8.3,
    confidenceScore: 0.85,
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<PerformanceDualityPanel {...defaultProps} isLoading />);

    expect(screen.queryByText("Performance Duality")).not.toBeInTheDocument();
    expect(screen.queryByTestId("duality-cards")).not.toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows heading and both cards", () => {
    render(<PerformanceDualityPanel {...defaultProps} />);

    expect(screen.getByText("Performance Duality")).toBeInTheDocument();
    expect(screen.getByTestId("duality-cards")).toBeInTheDocument();
    expect(screen.getByTestId("ili-card")).toBeInTheDocument();
    expect(screen.getByTestId("benchmark-card")).toBeInTheDocument();
  });

  it("Given positive returns, When rendered, Then displays ILI and benchmark returns with plus sign", () => {
    render(<PerformanceDualityPanel {...defaultProps} />);

    expect(screen.getByTestId("ili-card")).toHaveTextContent("+12.50%");
    expect(screen.getByTestId("benchmark-card")).toHaveTextContent("+8.30%");
  });

  it("Given negative returns, When rendered, Then displays returns without plus sign", () => {
    render(<PerformanceDualityPanel iliReturn={-3.2} benchmarkReturn={-1.1} confidenceScore={0.5} />);

    expect(screen.getByTestId("ili-card")).toHaveTextContent("-3.20%");
    expect(screen.getByTestId("benchmark-card")).toHaveTextContent("-1.10%");
  });

  it("Given ILI outperforms benchmark, When rendered, Then shows positive alpha", () => {
    render(<PerformanceDualityPanel {...defaultProps} />);

    expect(screen.getByText(/Alpha:/)).toBeInTheDocument();
    expect(screen.getByText("+4.20%")).toBeInTheDocument();
  });

  it("Given ILI underperforms benchmark, When rendered, Then shows negative alpha", () => {
    render(<PerformanceDualityPanel iliReturn={2.0} benchmarkReturn={5.5} confidenceScore={0.6} />);

    expect(screen.getByText("-3.50%")).toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows confidence score as percentage", () => {
    render(<PerformanceDualityPanel {...defaultProps} />);

    expect(screen.getByTestId("confidence-score")).toHaveTextContent("85%");
  });

  it("Given valid props, When rendered, Then shows Pure ILI Strategy and Buy & Hold Benchmark labels", () => {
    render(<PerformanceDualityPanel {...defaultProps} />);

    expect(screen.getByText("Pure ILI Strategy")).toBeInTheDocument();
    expect(screen.getByText("Buy & Hold Benchmark")).toBeInTheDocument();
  });
});
