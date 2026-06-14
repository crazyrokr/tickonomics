import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PortfolioSummary } from "@/components/demo/PortfolioSummary";
import type { DemoPortfolio } from "@/lib/api";

const portfolio: DemoPortfolio = {
  balance: 105_000,
  initialBalance: 100_000,
  realizedPnl: 4_000,
  unrealizedPnl: 1_000,
  totalPnl: 5_000,
  openPositions: 3,
  totalTrades: 12,
  winRate: 0.6,
  enabled: true,
};

describe("PortfolioSummary", () => {
  it("renders balance and total P&L when data is present", () => {
    render(<PortfolioSummary portfolio={portfolio} isLoading={false} />);
    expect(screen.getByText("$105,000.00")).toBeInTheDocument();
    expect(screen.getByText("$5,000.00")).toBeInTheDocument();
  });

  it("renders win rate as a percentage", () => {
    render(<PortfolioSummary portfolio={portfolio} isLoading={false} />);
    expect(screen.getByText("60.0%")).toBeInTheDocument();
  });

  it("shows loading state while loading", () => {
    render(<PortfolioSummary portfolio={undefined} isLoading={true} />);
    expect(screen.getByText(/Loading portfolio/i)).toBeInTheDocument();
  });

  it("shows empty state when no portfolio data", () => {
    render(<PortfolioSummary portfolio={undefined} isLoading={false} />);
    expect(screen.getByText(/No portfolio data/i)).toBeInTheDocument();
  });
});
