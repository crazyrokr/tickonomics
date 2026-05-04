"use client";

import type { RrpDrainVelocity } from "@/types/api";
import { Sparkline } from "@/components/charts/Sparkline";

interface RrpDrainTrendProps {
  data: RrpDrainVelocity | undefined;
  isLoading?: boolean;
}

const TREND_LABEL: Record<string, { symbol: string; className: string }> = {
  ACCELERATING: { symbol: "↑", className: "text-ili-red" },
  DECELERATING: { symbol: "↓", className: "text-ili-green" },
  STABLE: { symbol: "→", className: "text-muted" },
};

export function RrpDrainTrend({ data, isLoading }: RrpDrainTrendProps) {
  if (isLoading || !data) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/2 mb-3" />
        <div className="h-8 bg-border rounded w-1/3" />
      </div>
    );
  }

  const trend = TREND_LABEL[data.trend] ?? TREND_LABEL.STABLE;
  const historyValues = data.history.map((h) => h.velocity);
  const dayChange = data.dayOverDayChange;
  const dayChangeClass = dayChange > 0 ? "text-ili-red" : dayChange < 0 ? "text-ili-green" : "text-muted";

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-2">RRP Drain Velocity</h3>
      <div className="flex items-end gap-2 mb-1">
        <span className="text-2xl font-bold">{data.velocity.toFixed(1)}B</span>
        <span className={`text-sm mb-0.5 ${trend.className}`}>
          {trend.symbol}
        </span>
      </div>
      <p className={`text-xs ${dayChangeClass}`}>
        {dayChange > 0 ? "+" : ""}{dayChange.toFixed(2)}B day/day
      </p>
      {historyValues.length >= 2 && (
        <div className="mt-2">
          <Sparkline data={historyValues} height={28} />
        </div>
      )}
    </div>
  );
}
