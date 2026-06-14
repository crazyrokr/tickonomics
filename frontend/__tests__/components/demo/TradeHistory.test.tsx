import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TradeHistory } from "@/components/demo/TradeHistory";
import type { DemoTrade } from "@/lib/api";

const trades: DemoTrade[] = [
  {
    id: 1,
    symbol: "SPY",
    direction: "BUY",
    quantity: 10.5,
    fillPrice: 500.25,
    commission: 1.0,
    slippage: 0.5,
    realizedPnl: null,
    executedAt: "2026-06-13T10:00:00Z",
    tradeType: "PAPER",
  },
  {
    id: 2,
    symbol: "QQQ",
    direction: "SELL",
    quantity: 5.0,
    fillPrice: 400.0,
    commission: 0.5,
    slippage: 0.2,
    realizedPnl: 120.5,
    executedAt: "2026-06-13T11:00:00Z",
    tradeType: "PAPER",
  },
];

describe("TradeHistory", () => {
  it("renders a row per trade", () => {
    render(<TradeHistory trades={trades} isLoading={false} />);
    expect(screen.getByText("SPY")).toBeInTheDocument();
    expect(screen.getByText("QQQ")).toBeInTheDocument();
  });

  it("renders realized P&L when present", () => {
    render(<TradeHistory trades={trades} isLoading={false} />);
    expect(screen.getByText("120.50")).toBeInTheDocument();
  });

  it("shows loading state while loading", () => {
    render(<TradeHistory trades={[]} isLoading={true} />);
    expect(screen.getByText(/Loading trades/i)).toBeInTheDocument();
  });

  it("shows empty state when there are no trades", () => {
    render(<TradeHistory trades={[]} isLoading={false} />);
    expect(screen.getByText(/No trades recorded/i)).toBeInTheDocument();
  });
});
