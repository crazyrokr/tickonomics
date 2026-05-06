import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SentimentHeatmap } from "@/components/charts/SentimentHeatmap";

const mockData = [
  { symbol: "AAPL", lexiconScore: 0.45, finbertScore: 0.62, source: "NewsAPI", timestamp: "2026-05-30T10:00:00Z" },
  { symbol: "MSFT", lexiconScore: -0.35, finbertScore: -0.12, source: "Twitter", timestamp: "2026-05-30T09:00:00Z" },
  { symbol: "GOOG", lexiconScore: 0.05, finbertScore: 0.10, source: "Reddit", timestamp: "2026-05-30T08:00:00Z" },
];

describe("SentimentHeatmap", () => {
  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<SentimentHeatmap data={[]} isLoading />);

    expect(screen.queryByText("Sentiment Analysis")).not.toBeInTheDocument();
    expect(screen.queryByTestId("sentiment-grid")).not.toBeInTheDocument();
  });

  it("Given empty data, When rendered, Then shows empty state message", () => {
    render(<SentimentHeatmap data={[]} />);

    expect(screen.getByText("Sentiment Analysis")).toBeInTheDocument();
    expect(screen.getByText("No sentiment data available")).toBeInTheDocument();
    expect(screen.queryByTestId("sentiment-grid")).not.toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows the sentiment grid with all rows", () => {
    render(<SentimentHeatmap data={mockData} />);

    expect(screen.getByTestId("sentiment-grid")).toBeInTheDocument();
    expect(screen.getByTestId("sentiment-AAPL")).toBeInTheDocument();
    expect(screen.getByTestId("sentiment-MSFT")).toBeInTheDocument();
    expect(screen.getByTestId("sentiment-GOOG")).toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then displays symbol names and source values", () => {
    render(<SentimentHeatmap data={mockData} />);

    expect(screen.getByText("AAPL")).toBeInTheDocument();
    expect(screen.getByText("MSFT")).toBeInTheDocument();
    expect(screen.getByText("GOOG")).toBeInTheDocument();
    expect(screen.getByText("NewsAPI")).toBeInTheDocument();
    expect(screen.getByText("Twitter")).toBeInTheDocument();
    expect(screen.getByText("Reddit")).toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then displays lexicon and finbert scores in correct format", () => {
    render(<SentimentHeatmap data={[mockData[0]]} />);

    expect(screen.getByText("0.45")).toBeInTheDocument();
    expect(screen.getByText("0.62")).toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows column headers", () => {
    render(<SentimentHeatmap data={mockData} />);

    expect(screen.getByText("Symbol")).toBeInTheDocument();
    expect(screen.getByText("Source")).toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows the legend with Negative, Neutral, Positive", () => {
    render(<SentimentHeatmap data={mockData} />);

    expect(screen.getByText("Negative")).toBeInTheDocument();
    expect(screen.getByText("Neutral")).toBeInTheDocument();
    expect(screen.getByText("Positive")).toBeInTheDocument();
  });
});
