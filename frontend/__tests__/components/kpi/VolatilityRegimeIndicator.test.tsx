import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { VolatilityRegimeIndicator } from "@/components/kpi/VolatilityRegimeIndicator";
import type { VolatilityRegimeData } from "@/types/api";

describe("VolatilityRegimeIndicator", () => {
  it("shows loading skeleton", () => {
    render(<VolatilityRegimeIndicator isLoading />);
    expect(screen.queryByText("Volatility Regime")).not.toBeInTheDocument();
  });

  it("renders LOW_VOL regime with green dot", () => {
    const data: VolatilityRegimeData = {
      regime: "LOW_VOL",
      upperBand: 110,
      lowerBand: 90,
      currentPrice: 95,
      timestamp: "2026-05-30T12:00:00Z",
    };
    render(<VolatilityRegimeIndicator data={data} />);
    expect(screen.getByTestId("regime-label")).toHaveTextContent("Low Volatility");
    expect(screen.getByTestId("regime-dot").className).toContain("bg-ili-green");
  });

  it("renders NORMAL regime with blue dot", () => {
    const data: VolatilityRegimeData = {
      regime: "NORMAL",
      upperBand: 110,
      lowerBand: 90,
      currentPrice: 100,
      timestamp: "2026-05-30T12:00:00Z",
    };
    render(<VolatilityRegimeIndicator data={data} />);
    expect(screen.getByTestId("regime-label")).toHaveTextContent("Normal");
    expect(screen.getByTestId("regime-dot").className).toContain("bg-ili-blue");
  });

  it("renders HIGH_VOL regime with red dot", () => {
    const data: VolatilityRegimeData = {
      regime: "HIGH_VOL",
      upperBand: 120,
      lowerBand: 80,
      currentPrice: 115,
      timestamp: "2026-05-30T12:00:00Z",
    };
    render(<VolatilityRegimeIndicator data={data} />);
    expect(screen.getByTestId("regime-label")).toHaveTextContent("High Volatility");
    expect(screen.getByTestId("regime-dot").className).toContain("bg-ili-red");
  });

  it("renders position marker within band range", () => {
    const data: VolatilityRegimeData = {
      regime: "NORMAL",
      upperBand: 110,
      lowerBand: 90,
      currentPrice: 100,
      timestamp: "2026-05-30T12:00:00Z",
    };
    render(<VolatilityRegimeIndicator data={data} />);
    const marker = screen.getByTestId("regime-position-marker");
    expect(marker).toBeInTheDocument();
  });

  it("renders band range labels", () => {
    const data: VolatilityRegimeData = {
      regime: "NORMAL",
      upperBand: 110,
      lowerBand: 90,
      currentPrice: 100,
      timestamp: "2026-05-30T12:00:00Z",
    };
    render(<VolatilityRegimeIndicator data={data} />);
    expect(screen.getByText("90.0")).toBeInTheDocument();
    expect(screen.getByText("100.0")).toBeInTheDocument();
    expect(screen.getByText("110.0")).toBeInTheDocument();
  });
});
