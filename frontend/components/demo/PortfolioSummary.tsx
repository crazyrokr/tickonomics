import type { DemoPortfolio } from "@/lib/api";

interface PortfolioSummaryProps {
  portfolio: DemoPortfolio | undefined;
  isLoading: boolean;
}

function formatCurrency(value: number): string {
  return value.toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  });
}

export function PortfolioSummary({ portfolio, isLoading }: PortfolioSummaryProps) {
  if (isLoading) {
    return <div className="text-sm text-slate-400">Loading portfolio…</div>;
  }
  if (!portfolio) {
    return <div className="text-sm text-slate-400">No portfolio data.</div>;
  }

  const pnlPositive = portfolio.totalPnl >= 0;

  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
      <div className="rounded-md border border-slate-700 bg-slate-900/50 p-4">
        <div className="text-xs uppercase text-slate-400">Balance</div>
        <div className="text-lg font-semibold">{formatCurrency(portfolio.balance)}</div>
      </div>
      <div className="rounded-md border border-slate-700 bg-slate-900/50 p-4">
        <div className="text-xs uppercase text-slate-400">Total P&amp;L</div>
        <div className={`text-lg font-semibold ${pnlPositive ? "text-emerald-400" : "text-rose-400"}`}>
          {formatCurrency(portfolio.totalPnl)}
        </div>
      </div>
      <div className="rounded-md border border-slate-700 bg-slate-900/50 p-4">
        <div className="text-xs uppercase text-slate-400">Open Positions</div>
        <div className="text-lg font-semibold">{portfolio.openPositions}</div>
      </div>
      <div className="rounded-md border border-slate-700 bg-slate-900/50 p-4">
        <div className="text-xs uppercase text-slate-400">Win Rate</div>
        <div className="text-lg font-semibold">
          {(portfolio.winRate * 100).toFixed(1)}%
        </div>
      </div>
    </div>
  );
}
