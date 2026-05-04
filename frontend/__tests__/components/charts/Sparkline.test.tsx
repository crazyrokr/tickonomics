import { describe, it, expect } from "vitest";
import { render } from "@testing-library/react";
import { Sparkline } from "@/components/charts/Sparkline";

describe("Sparkline", () => {
  it("renders an SVG element", () => {
    const { container } = render(<Sparkline data={[1, 2, 3, 4, 5]} />);
    const svg = container.querySelector("svg");
    expect(svg).toBeInTheDocument();
  });

  it("renders polyline when data has 2+ points", () => {
    const { container } = render(<Sparkline data={[10, 20, 30]} />);
    const polyline = container.querySelector("polyline");
    expect(polyline).toBeInTheDocument();
  });

  it("renders fallback when data has fewer than 2 points", () => {
    const { container } = render(<Sparkline data={[42]} />);
    const polyline = container.querySelector("polyline");
    expect(polyline).toBeNull();
    const line = container.querySelector("line");
    expect(line).toBeInTheDocument();
  });

  it("renders threshold line when threshold prop provided", () => {
    const { container } = render(
      <Sparkline data={[10, 20, 30, 40, 50]} threshold={30} />,
    );
    const lines = container.querySelectorAll("line");
    expect(lines.length).toBe(1);
  });

  it("uses custom dimensions", () => {
    const { container } = render(
      <Sparkline data={[1, 2, 3]} width={200} height={50} />,
    );
    const svg = container.querySelector("svg");
    expect(svg?.getAttribute("width")).toBe("200");
    expect(svg?.getAttribute("height")).toBe("50");
  });
});
