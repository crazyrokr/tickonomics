"use client";

import type { LiquidityStressIndex } from "@/types/api";

interface LiquidityStressGaugeProps {
  data: LiquidityStressIndex | undefined;
  isLoading?: boolean;
}

function getGaugeColor(value: number): string {
  if (value < 0) return "var(--color-ili-green)";
  if (value < 0.5) return "var(--color-ili-amber)";
  return "var(--color-ili-red)";
}

function getTrendIndicator(trend: string): string {
  switch (trend) {
    case "ACCELERATING":
      return "↑";
    case "DECELERATING":
      return "↓";
    default:
      return "→";
  }
}

export function LiquidityStressGauge({ data, isLoading }: LiquidityStressGaugeProps) {
  if (isLoading || !data) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-2/3 mb-3" />
        <div className="h-8 bg-border rounded w-1/3" />
      </div>
    );
  }

  const color = getGaugeColor(data.value);
  const percentage = Math.min(Math.max((data.value + 1) / 2 * 100, 0), 100);

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-2">
        Liquidity Stress Index
      </h3>
      <div className="flex items-end gap-2 mb-2">
        <span className="text-2xl font-bold" style={{ color }}>
          {data.value.toFixed(3)}
        </span>
        <span className="text-sm text-muted mb-0.5">
          {getTrendIndicator(data.trend)}
        </span>
      </div>
      <div className="w-full h-2 bg-border rounded-full overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-300"
          style={{ width: `${percentage}%`, backgroundColor: color }}
          data-testid="stress-gauge-fill"
        />
      </div>
    </div>
  );
}
