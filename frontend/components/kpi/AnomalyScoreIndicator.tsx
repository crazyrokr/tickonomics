"use client";

import { Sparkline } from "@/components/charts/Sparkline";

interface AnomalyScoreIndicatorProps {
  mse: number | undefined;
  threshold: number;
  suspectAnomaly: boolean;
  history?: number[];
  isLoading?: boolean;
}

export function AnomalyScoreIndicator({
  mse,
  threshold,
  suspectAnomaly,
  history = [],
  isLoading,
}: AnomalyScoreIndicatorProps) {
  if (isLoading || mse === undefined) {
    return (
      <div className="rounded-lg border border-border p-3 bg-surface animate-pulse">
        <div className="h-3 bg-border rounded w-1/2 mb-2" />
        <div className="h-6 bg-border rounded w-1/3" />
      </div>
    );
  }

  const exceedsThreshold = mse > threshold;

  return (
    <div
      className={`rounded-lg border p-3 bg-surface ${suspectAnomaly ? "border-ili-amber" : "border-border"}`}
      data-testid="anomaly-indicator"
    >
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs text-muted">Anomaly Score</span>
        {suspectAnomaly && (
          <span className="px-1.5 py-0.5 text-xs bg-ili-amber text-white rounded-full" data-testid="anomaly-badge">
            SUSPECT
          </span>
        )}
      </div>
      <div className="flex items-end gap-2">
        <span className={`text-lg font-bold ${exceedsThreshold ? "text-ili-amber" : ""}`}>
          {mse.toFixed(4)}
        </span>
        <span className="text-xs text-muted mb-0.5">/ {threshold.toFixed(2)} threshold</span>
      </div>
      {history.length >= 2 && (
        <div className="mt-1">
          <Sparkline
            data={history}
            height={24}
            threshold={threshold}
          />
        </div>
      )}
    </div>
  );
}
