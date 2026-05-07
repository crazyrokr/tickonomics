"use client";

interface ExecutionComparisonPanelProps {
  passivePnl: number;
  aggressivePnl: number;
  priceEfficiency: number;
  isLoading?: boolean;
}

export function ExecutionComparisonPanel({ passivePnl, aggressivePnl, priceEfficiency, isLoading }: ExecutionComparisonPanelProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="grid grid-cols-2 gap-4">
          <div className="h-20 bg-border rounded" />
          <div className="h-20 bg-border rounded" />
        </div>
      </div>
    );
  }

  const passiveBetter = passivePnl > aggressivePnl;
  const aggressiveBetter = aggressivePnl > passivePnl;

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Execution Mode Comparison</h3>

      <div className="grid grid-cols-2 gap-4 mb-4" data-testid="execution-cards">
        <div
          className={`p-3 rounded-lg border ${passiveBetter ? "border-ili-green bg-ili-green/5" : "border-border"}`}
          data-testid="passive-card"
        >
          <p className="text-xs text-muted mb-1">Passive P&amp;L</p>
          <p className={`text-xl font-bold font-mono ${passiveBetter ? "text-ili-green" : ""}`}>
            {passivePnl >= 0 ? "+" : ""}{passivePnl.toFixed(2)}
          </p>
        </div>

        <div
          className={`p-3 rounded-lg border ${aggressiveBetter ? "border-ili-green bg-ili-green/5" : "border-border"}`}
          data-testid="aggressive-card"
        >
          <p className="text-xs text-muted mb-1">Aggressive P&amp;L</p>
          <p className={`text-xl font-bold font-mono ${aggressiveBetter ? "text-ili-green" : ""}`}>
            {aggressivePnl >= 0 ? "+" : ""}{aggressivePnl.toFixed(2)}
          </p>
        </div>
      </div>

      <div className="border-t border-border pt-3 space-y-2">
        <div className="flex items-center justify-between text-sm">
          <span className="text-muted">Price Efficiency</span>
          <span className="font-mono font-medium" data-testid="price-efficiency">
            {priceEfficiency.toFixed(4)}
          </span>
        </div>
        <div className="flex items-center justify-between text-sm">
          <span className="text-muted">Optimal Mode</span>
          <span className={`font-medium ${passiveBetter ? "text-ili-green" : aggressiveBetter ? "text-ili-blue" : "text-muted"}`} data-testid="optimal-mode">
            {passiveBetter ? "Passive" : aggressiveBetter ? "Aggressive" : "Neutral"}
          </span>
        </div>
      </div>
    </div>
  );
}
