import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { D3IliHeatmap } from "@/components/charts/D3IliHeatmap";

vi.mock("d3", () => ({
  select: vi.fn(() => ({
    selectAll: vi.fn(() => ({
      remove: vi.fn(),
      join: vi.fn(() => []),
    })),
    attr: vi.fn(() => ({
      append: vi.fn(() => ({
        attr: vi.fn(() => ({
          call: vi.fn(),
          append: vi.fn(() => ({
            text: vi.fn(),
            attr: vi.fn(),
          })),
          selectAll: vi.fn(() => ({
            attr: vi.fn(),
          })),
        })),
      })),
    })),
  })),
  scaleBand: vi.fn(() => ({
    domain: vi.fn(() => ({
      range: vi.fn(() => ({
        padding: vi.fn(() => ({
          copy: vi.fn(),
        })),
      })),
    })),
    bandwidth: vi.fn(() => 40),
  })),
  scaleLinear: vi.fn(() => ({
    domain: vi.fn(() => ({
      range: vi.fn(() => ({
        clamp: vi.fn(),
      })),
    })),
  })),
  axisBottom: vi.fn(() => ({
    tickSize: vi.fn(),
  })),
  axisLeft: vi.fn(() => ({
    tickSize: vi.fn(),
  })),
}));

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
