import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SignalQualityMetrics } from "@/components/demo/SignalQualityMetrics";
import type { DemoSignalQuality } from "@/lib/api";

const report: DemoSignalQuality = {
  reportDate: "2026-06-13",
  totalSignals: 100,
  actionableSignals: 62,
  hitRate5d: 0.62,
  falsePositiveRate: 0.12,
  avgReturnPerSignal: 0.008,
  portfolioSharpe: 0.74,
  vsSpyReturn: 0.03,
};

describe("SignalQualityMetrics", () => {
  it("renders hit rate and false-positive rate as percentages", () => {
    render(<SignalQualityMetrics report={report} isLoading={false} />);
    expect(screen.getByText("62.0%")).toBeInTheDocument();
    expect(screen.getByText("12.0%")).toBeInTheDocument();
  });

  it("renders the Sharpe ratio", () => {
    render(<SignalQualityMetrics report={report} isLoading={false} />);
    expect(screen.getByText("0.74")).toBeInTheDocument();
  });

  it("shows loading state while loading", () => {
    render(<SignalQualityMetrics report={undefined} isLoading={true} />);
    expect(screen.getByText(/Loading signal quality/i)).toBeInTheDocument();
  });

  it("shows empty state when no report", () => {
    render(<SignalQualityMetrics report={undefined} isLoading={false} />);
    expect(screen.getByText(/No signal quality report/i)).toBeInTheDocument();
  });

  it("renders em-dash for missing metric values", () => {
    render(<SignalQualityMetrics report={{ actionableSignals: 5 }} isLoading={false} />);
    expect(screen.getAllByText("—").length).toBeGreaterThan(0);
  });
});
