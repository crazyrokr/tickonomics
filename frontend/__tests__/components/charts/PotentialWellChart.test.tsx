import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PotentialWellChart } from "@/components/charts/PotentialWellChart";

describe("PotentialWellChart", () => {
  const data = [
    { price: 90, potential: 2.0 },
    { price: 95, potential: 0.5 },
    { price: 100, potential: 0.1 },
    { price: 105, potential: 0.8 },
    { price: 110, potential: 2.5 },
  ];

  it("shows loading skeleton", () => {
    render(<PotentialWellChart data={[]} currentPrice={100} minima={[]} isLoading />);
    expect(screen.queryByText("QED Potential Well")).not.toBeInTheDocument();
  });

  it("renders SVG chart", () => {
    render(<PotentialWellChart data={data} currentPrice={100} minima={[100]} />);
    expect(screen.getByTestId("potential-well-svg")).toBeInTheDocument();
  });

  it("renders current price marker", () => {
    render(<PotentialWellChart data={data} currentPrice={100} minima={[100]} />);
    expect(screen.getByTestId("current-price-marker")).toBeInTheDocument();
  });

  it("renders well minima", () => {
    render(<PotentialWellChart data={data} currentPrice={100} minima={[100]} />);
    expect(screen.getByTestId("minimum-0")).toBeInTheDocument();
  });

  it("shows insufficient data message", () => {
    render(<PotentialWellChart data={[{ price: 100, potential: 1 }]} currentPrice={100} minima={[]} />);
    expect(screen.getByText("Insufficient data")).toBeInTheDocument();
  });

  it("shows price position and well count", () => {
    render(<PotentialWellChart data={data} currentPrice={100} minima={[100]} />);
    expect(screen.getByText(/Price position: 100.00/)).toBeInTheDocument();
    expect(screen.getByText(/Wells: 1/)).toBeInTheDocument();
  });
});
