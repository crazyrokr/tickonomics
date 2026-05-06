import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { GammaProfileChart } from "@/components/charts/GammaProfileChart";

describe("GammaProfileChart", () => {
  const mockData = [
    { price: 4400, gamma: 0.12 },
    { price: 4450, gamma: -0.05 },
    { price: 4500, gamma: 0.25 },
    { price: 4550, gamma: 0.08 },
  ];

  it("shows disabled state message when enabled is false", () => {
    // Given the chart is disabled
    render(
      <GammaProfileChart data={[]} flipZonePrice={null} enabled={false} />
    );

    // Then the disabled message should be displayed
    expect(
      screen.getByText("Enable options data to view gamma profile")
    ).toBeInTheDocument();
    // And the SVG should not be rendered
    expect(screen.queryByTestId("gamma-svg")).not.toBeInTheDocument();
  });

  it("shows loading skeleton when isLoading is true", () => {
    // Given the chart is enabled but loading
    render(
      <GammaProfileChart data={[]} flipZonePrice={null} enabled isLoading />
    );

    // Then the SVG should not be rendered
    expect(screen.queryByTestId("gamma-svg")).not.toBeInTheDocument();
    // And the skeleton container should be visible
    const skeleton = document.querySelector(".animate-pulse");
    expect(skeleton).toBeInTheDocument();
  });

  it("renders SVG with data points as a polyline", () => {
    // Given valid gamma profile data with four price levels
    render(
      <GammaProfileChart data={mockData} flipZonePrice={null} enabled />
    );

    // Then the SVG element should be rendered
    const svg = screen.getByTestId("gamma-svg");
    expect(svg).toBeInTheDocument();
    // And the polyline should contain points (one coordinate pair per data entry)
    const polyline = svg.querySelector("polyline");
    expect(polyline).toBeInTheDocument();
    const points = polyline!.getAttribute("points")!.trim().split(" ");
    expect(points).toHaveLength(mockData.length);
  });

  it("renders flip zone line when flipZonePrice is provided", () => {
    // Given a flip zone price of 4500
    render(
      <GammaProfileChart data={mockData} flipZonePrice={4500} enabled />
    );

    // Then the flip zone line should be present inside the SVG
    const flipLine = screen.getByTestId("flip-zone-line");
    expect(flipLine).toBeInTheDocument();
    // And the flip zone price label should show the correct value
    expect(screen.getByText(/Gamma Flip Zone:/)).toHaveTextContent(
      "Gamma Flip Zone: 4500.00"
    );
  });

  it("shows empty data state when data has fewer than 2 points", () => {
    // Given data with only a single point
    render(
      <GammaProfileChart
        data={[{ price: 4500, gamma: 0.1 }]}
        flipZonePrice={null}
        enabled
      />
    );

    // Then the empty state message should be displayed
    expect(screen.getByText("No gamma data available")).toBeInTheDocument();
    // And the SVG should not be rendered
    expect(screen.queryByTestId("gamma-svg")).not.toBeInTheDocument();
  });
});
