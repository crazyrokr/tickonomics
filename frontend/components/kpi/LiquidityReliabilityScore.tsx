"use client";

import { Sparkline } from "@/components/charts/Sparkline";

interface LiquidityReliabilityScoreProps {
  score: number;
  pliHigh: boolean;
  history: number[];
  isLoading?: boolean;
}

function getScoreColor(score: number): string {
  if (score >= 70) return "var(--color-ili-green)";
  if (score >= 40) return "var(--color-ili-amber)";
  return "var(--color-ili-red)";
}

export function LiquidityReliabilityScore({ score, pliHigh, history, isLoading }: LiquidityReliabilityScoreProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-2/3 mb-3" />
        <div className="h-8 bg-border rounded w-1/3" />
      </div>
    );
  }

  const color = getScoreColor(score);

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <div className="flex items-center justify-between mb-2">
        <h3 className="text-sm font-medium text-muted">Liquidity Reliability</h3>
        {pliHigh && (
          <span
            className="px-2 py-0.5 text-xs rounded-full font-medium text-white bg-ili-amber"
            data-testid="pli-high-badge"
          >
            PLI High
          </span>
        )}
      </div>

      <div className="flex items-center gap-3">
        <span className="text-3xl font-bold" style={{ color }} data-testid="lrs-score">
          {score.toFixed(1)}
        </span>
        <div className="flex-1">
          <Sparkline data={history} width={140} height={36} color={color} />
        </div>
      </div>

      <div className="w-full h-2 bg-border rounded-full overflow-hidden mt-3">
        <div
          className="h-full rounded-full transition-all duration-300"
          style={{ width: `${Math.min(Math.max(score, 0), 100)}%`, backgroundColor: color }}
          data-testid="lrs-bar-fill"
        />
      </div>

      <div className="flex justify-between text-xs text-muted mt-1">
        <span>Stressed</span>
        <span>Reliable</span>
      </div>
    </div>
  );
}
