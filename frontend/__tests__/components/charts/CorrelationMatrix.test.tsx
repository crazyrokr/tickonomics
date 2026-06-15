import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { CorrelationMatrix } from "@/components/charts/CorrelationMatrix";

vi.mock("@perspective-dev/client", () => ({
  worker: vi.fn(() => Promise.resolve({
    table: vi.fn(() => "mock-table"),
  })),
}));

vi.mock("@perspective-dev/viewer", () => ({}));
vi.mock("@perspective-dev/viewer-datagrid", () => ({}));

describe("CorrelationMatrix", () => {
  const mockData = [
    { source: "RRP", target: "SPY", correlation: 0.85, pValue: 0.001, sampleSize: 252, aicLagOrder: 3 },
    { source: "SOFR", target: "QQQ", correlation: -0.42, pValue: 0.05, sampleSize: 252, aicLagOrder: 5 },
  ];

  it("shows loading skeleton", () => {
    render(<CorrelationMatrix data={[]} isLoading />);
    expect(screen.queryByText("Correlation Matrix")).not.toBeInTheDocument();
  });

  it("renders panel with data", () => {
    render(<CorrelationMatrix data={mockData} />);
    expect(screen.getByText("Correlation Matrix")).toBeInTheDocument();
    expect(screen.getByTestId("correlation-matrix")).toBeInTheDocument();
  });

  it("shows empty state when no data", () => {
    render(<CorrelationMatrix data={[]} />);
    expect(screen.getByText("No correlation data available")).toBeInTheDocument();
  });
});
