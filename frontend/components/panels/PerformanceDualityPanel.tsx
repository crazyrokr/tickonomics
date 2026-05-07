"use client";

interface PerformanceDualityPanelProps {
  iliReturn: number;
  benchmarkReturn: number;
  confidenceScore: number;
  isLoading?: boolean;
}

export function PerformanceDualityPanel({ iliReturn, benchmarkReturn, confidenceScore, isLoading }: PerformanceDualityPanelProps) {
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

  const iliWinner = iliReturn > benchmarkReturn;
  const benchmarkWinner = benchmarkReturn > iliReturn;
  const diff = iliReturn - benchmarkReturn;

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Performance Duality</h3>

      <div className="grid grid-cols-2 gap-4 mb-4" data-testid="duality-cards">
        <div
          className={`p-3 rounded-lg border ${iliWinner ? "border-ili-green bg-ili-green/5" : "border-border"}`}
          data-testid="ili-card"
        >
          <p className="text-xs text-muted mb-1">Pure ILI Strategy</p>
          <p
            className={`text-2xl font-bold font-mono ${iliWinner ? "text-ili-green" : ""}`}
          >
            {iliReturn >= 0 ? "+" : ""}{iliReturn.toFixed(2)}%
          </p>
        </div>

        <div
          className={`p-3 rounded-lg border ${benchmarkWinner ? "border-ili-green bg-ili-green/5" : "border-border"}`}
          data-testid="benchmark-card"
        >
          <p className="text-xs text-muted mb-1">Buy &amp; Hold Benchmark</p>
          <p
            className={`text-2xl font-bold font-mono ${benchmarkWinner ? "text-ili-green" : ""}`}
          >
            {benchmarkReturn >= 0 ? "+" : ""}{benchmarkReturn.toFixed(2)}%
          </p>
        </div>
      </div>

      <div className="flex items-center justify-between border-t border-border pt-3">
        <div className="text-sm">
          <span className="text-muted">Alpha: </span>
          <span className={`font-mono font-medium ${diff > 0 ? "text-ili-green" : diff < 0 ? "text-ili-red" : ""}`}>
            {diff >= 0 ? "+" : ""}{diff.toFixed(2)}%
          </span>
        </div>
        <div className="text-sm" data-testid="confidence-score">
          <span className="text-muted">Confidence: </span>
          <span className="font-mono font-medium">{(confidenceScore * 100).toFixed(0)}%</span>
        </div>
      </div>
    </div>
  );
}
