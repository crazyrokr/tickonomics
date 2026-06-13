import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { D3IliHeatmap } from "@/components/charts/D3IliHeatmap";

vi.mock("d3", () => {
  const chainable = (): unknown => {
    const fn = (): unknown => chainable();
    return new Proxy(fn, {
      get: () => (..._args: unknown[]) => chainable(),
      apply: () => chainable(),
    });
  };
  return {
    select: chainable,
    scaleBand: chainable,
    scaleLinear: chainable,
    axisBottom: chainable,
    axisLeft: chainable,
  };
});

describe("D3IliHeatmap", () => {
  it("shows loading skeleton when isLoading is true", () => {
    const { container } = render(
      <D3IliHeatmap
        data={[]}
        rowLabels={[]}
        colLabels={[]}
        isLoading
      />
    );
    expect(container.querySelector(".animate-pulse")).toBeTruthy();
  });

  it("shows empty state when data is empty", () => {
    render(<D3IliHeatmap data={[]} rowLabels={[]} colLabels={[]} />);
    expect(screen.getByText("No heatmap data available")).toBeTruthy();
  });

  it("renders svg element when data is provided", () => {
    const data = [
      { row: "A", col: "X", value: 0.5 },
      { row: "A", col: "Y", value: 0.8 },
      { row: "B", col: "X", value: 0.3 },
      { row: "B", col: "Y", value: 0.9 },
    ];
    const { container } = render(
      <D3IliHeatmap data={data} rowLabels={["A", "B"]} colLabels={["X", "Y"]} />
    );
    expect(screen.getByTestId("d3-ili-heatmap")).toBeTruthy();
    expect(container.querySelector("svg")).toBeTruthy();
  });

  it("renders with custom color scale", () => {
    const data = [{ row: "A", col: "X", value: 0.5 }];
    const { container } = render(
      <D3IliHeatmap
        data={data}
        rowLabels={["A"]}
        colLabels={["X"]}
        colorScale={["#00ff00", "#ffff00", "#ff0000"]}
      />
    );
    expect(container.querySelector("svg")).toBeTruthy();
  });
});
