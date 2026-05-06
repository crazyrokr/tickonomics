import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TailRiskQQPlot } from "@/components/charts/TailRiskQQPlot";

const mockData = [
  { quantile: 0.1, theoretical: -1.28, observed: -1.55 },
  { quantile: 0.5, theoretical: 0.0, observed: 0.05 },
  { quantile: 0.9, theoretical: 1.28, observed: 1.62 },
];

describe("TailRiskQQPlot", () => {
  const defaultProps = {
    data: mockData,
    heavyTailRegime: false,
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<TailRiskQQPlot {...defaultProps} isLoading />);

    expect(screen.queryByText("Tail Risk Q-Q Plot")).not.toBeInTheDocument();
    expect(screen.queryByTestId("qq-plot-svg")).not.toBeInTheDocument();
  });

  it("Given data with fewer than 2 points, When rendered, Then shows empty state message", () => {
    render(<TailRiskQQPlot data={[{ quantile: 0.5, theoretical: 0, observed: 0 }]} heavyTailRegime={false} />);

    expect(screen.getByText("Tail Risk Q-Q Plot")).toBeInTheDocument();
    expect(screen.getByText("No quantile data available")).toBeInTheDocument();
    expect(screen.queryByTestId("qq-plot-svg")).not.toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows the Q-Q plot SVG and export button", () => {
    render(<TailRiskQQPlot {...defaultProps} />);

    expect(screen.getByText("Tail Risk Q-Q Plot")).toBeInTheDocument();
    expect(screen.getByTestId("qq-plot-svg")).toBeInTheDocument();
    expect(screen.getByTestId("export-button")).toHaveTextContent("Export");
  });

  it("Given valid data, When rendered, Then shows axis labels", () => {
    render(<TailRiskQQPlot {...defaultProps} />);

    expect(screen.getByText("X: Theoretical Quantiles")).toBeInTheDocument();
    expect(screen.getByText("Y: Observed Quantiles")).toBeInTheDocument();
  });

  it("Given heavyTailRegime is true, When rendered, Then shows heavy tail badge", () => {
    render(<TailRiskQQPlot {...defaultProps} heavyTailRegime />);

    expect(screen.getByTestId("heavy-tail-badge")).toHaveTextContent("Heavy Tails");
  });

  it("Given heavyTailRegime is false, When rendered, Then does not show heavy tail badge", () => {
    render(<TailRiskQQPlot {...defaultProps} />);

    expect(screen.queryByTestId("heavy-tail-badge")).not.toBeInTheDocument();
  });

  it("Given empty data array, When rendered, Then shows empty state message", () => {
    render(<TailRiskQQPlot data={[]} heavyTailRegime={false} />);

    expect(screen.getByText("No quantile data available")).toBeInTheDocument();
    expect(screen.queryByTestId("qq-plot-svg")).not.toBeInTheDocument();
  });
});
