import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { BacktestHeatmap } from "@/components/charts/BacktestHeatmap";

describe("BacktestHeatmap", () => {
  const mockData = [
    { paramA: "1D", paramB: "SPY", value: 1.25 },
    { paramA: "1W", paramB: "SPY", value: 0.87 },
    { paramA: "1D", paramB: "QQQ", value: -0.42 },
    { paramA: "1W", paramB: "QQQ", value: 0.33 },
  ];

  it("shows loading skeleton when isLoading is true", () => {
    // Given the component is in a loading state
    render(
      <BacktestHeatmap
        type="time"
        data={[]}
        paramALabel="Holding Period"
        paramBLabel="Ticker"
        isLoading
      />
    );

    // Then the heatmap title and grid should not be rendered
    expect(screen.queryByTestId("heatmap-title")).not.toBeInTheDocument();
    expect(screen.queryByTestId("heatmap-grid")).not.toBeInTheDocument();
    // And the skeleton container should be visible with pulse animation
    const skeleton = document.querySelector(".animate-pulse");
    expect(skeleton).toBeInTheDocument();
  });

  it("renders table with data cells", () => {
    // Given valid heatmap data for a time sensitivity type
    render(
      <BacktestHeatmap
        type="time"
        data={mockData}
        paramALabel="Holding Period"
        paramBLabel="Ticker"
      />
    );

    // Then the heatmap grid should be present
    expect(screen.getByTestId("heatmap-grid")).toBeInTheDocument();
    // And each cell should be rendered with the correct formatted value
    expect(screen.getByTestId("cell-1D-SPY")).toHaveTextContent("1.25");
    expect(screen.getByTestId("cell-1W-SPY")).toHaveTextContent("0.87");
    expect(screen.getByTestId("cell-1D-QQQ")).toHaveTextContent("-0.42");
    expect(screen.getByTestId("cell-1W-QQQ")).toHaveTextContent("0.33");
    // And axis label should be embedded in the table header
    expect(screen.getByText(/Holding Period/)).toBeInTheDocument();
  });

  it("shows empty state when data array is empty", () => {
    // Given no heatmap data
    render(
      <BacktestHeatmap
        type="time"
        data={[]}
        paramALabel="Holding Period"
        paramBLabel="Ticker"
      />
    );

    // Then the empty state message should be shown
    expect(screen.getByText("No heatmap data available")).toBeInTheDocument();
    // And the grid should not be rendered
    expect(screen.queryByTestId("heatmap-grid")).not.toBeInTheDocument();
  });

  it("shows correct title based on type prop", () => {
    // Given a parameter sensitivity heatmap
    const { rerender } = render(
      <BacktestHeatmap
        type="parameter"
        data={mockData}
        paramALabel="Threshold"
        paramBLabel="Lookback"
      />
    );

    // Then the title should read "Parameter Sensitivity Heatmap"
    expect(screen.getByTestId("heatmap-title")).toHaveTextContent(
      "Parameter Sensitivity Heatmap"
    );

    // When switching to time sensitivity type
    rerender(
      <BacktestHeatmap
        type="time"
        data={mockData}
        paramALabel="Holding Period"
        paramBLabel="Ticker"
      />
    );

    // Then the title should read "Time Sensitivity Heatmap"
    expect(screen.getByTestId("heatmap-title")).toHaveTextContent(
      "Time Sensitivity Heatmap"
    );
  });
});
