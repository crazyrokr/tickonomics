import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ToxicityHeatmap } from "@/components/charts/ToxicityHeatmap";

const mockData = [
  { asset: "AAPL", venue: "NYSE", score: 0.2 },
  { asset: "AAPL", venue: "NASDAQ", score: 0.55 },
  { asset: "MSFT", venue: "NYSE", score: 0.8 },
  { asset: "MSFT", venue: "NASDAQ", score: 0.1 },
];

describe("ToxicityHeatmap", () => {
  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<ToxicityHeatmap data={[]} isLoading />);

    expect(screen.queryByText("Order Flow Toxicity")).not.toBeInTheDocument();
    expect(screen.queryByTestId("toxicity-grid")).not.toBeInTheDocument();
  });

  it("Given empty data, When rendered, Then shows empty state message", () => {
    render(<ToxicityHeatmap data={[]} />);

    expect(screen.getByText("Order Flow Toxicity")).toBeInTheDocument();
    expect(screen.getByText("No toxicity data available")).toBeInTheDocument();
    expect(screen.queryByTestId("toxicity-grid")).not.toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows the toxicity grid with all cells", () => {
    render(<ToxicityHeatmap data={mockData} />);

    expect(screen.getByText("Order Flow Toxicity")).toBeInTheDocument();
    expect(screen.getByTestId("toxicity-grid")).toBeInTheDocument();

    expect(screen.getByTestId("cell-AAPL-NYSE")).toHaveTextContent("0.20");
    expect(screen.getByTestId("cell-AAPL-NASDAQ")).toHaveTextContent("0.55");
    expect(screen.getByTestId("cell-MSFT-NYSE")).toHaveTextContent("0.80");
    expect(screen.getByTestId("cell-MSFT-NASDAQ")).toHaveTextContent("0.10");
  });

  it("Given valid data, When rendered, Then displays venue headers and asset row labels", () => {
    render(<ToxicityHeatmap data={mockData} />);

    expect(screen.getByText("NYSE")).toBeInTheDocument();
    expect(screen.getByText("NASDAQ")).toBeInTheDocument();
    expect(screen.getByText("AAPL")).toBeInTheDocument();
    expect(screen.getByText("MSFT")).toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows the legend with Beneficial, Neutral, Harmful", () => {
    render(<ToxicityHeatmap data={mockData} />);

    expect(screen.getByText("Beneficial")).toBeInTheDocument();
    expect(screen.getByText("Neutral")).toBeInTheDocument();
    expect(screen.getByText("Harmful")).toBeInTheDocument();
  });

  it("Given data with missing asset-venue pairs, When rendered, Then uses default score 0.50 for missing cells", () => {
    const partialData = [
      { asset: "AAPL", venue: "NYSE", score: 0.3 },
    ];

    render(<ToxicityHeatmap data={partialData} />);

    expect(screen.getByTestId("cell-AAPL-NYSE")).toHaveTextContent("0.30");
  });
});
