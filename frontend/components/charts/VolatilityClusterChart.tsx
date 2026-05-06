"use client";

import type { VolatilityRegime } from "@/types/api";

interface RegimePeriod {
  timestamp: string;
  regime: VolatilityRegime;
}

interface VolatilityClusterChartProps {
  data: RegimePeriod[];
  currentRegime: VolatilityRegime;
  isLoading?: boolean;
}

const REGIME_CONFIG: Record<VolatilityRegime, { color: string; label: string }> = {
  LOW_VOL: { color: "var(--color-ili-green)", label: "Low Volatility" },
  NORMAL: { color: "var(--color-ili-blue)", label: "Normal" },
  HIGH_VOL: { color: "var(--color-ili-red)", label: "High Volatility" },
};

function getRegimeDuration(data: RegimePeriod[], regime: VolatilityRegime): number {
  return data.filter((d) => d.regime === regime).length;
}

export function VolatilityClusterChart({
  data,
  currentRegime,
  isLoading,
}: VolatilityClusterChartProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-48 bg-border rounded" />
      </div>
    );
  }

  const config = REGIME_CONFIG[currentRegime];
  const durations = {
    LOW_VOL: getRegimeDuration(data, "LOW_VOL"),
    NORMAL: getRegimeDuration(data, "NORMAL"),
    HIGH_VOL: getRegimeDuration(data, "HIGH_VOL"),
  };
  const total = data.length || 1;

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Volatility Clusters</h3>

      <div className="flex items-center gap-2 mb-3">
        <span className="text-xs text-muted">Current:</span>
        <span
          className="px-2 py-0.5 text-xs rounded-full font-medium text-white"
          style={{ backgroundColor: config.color }}
          data-testid="current-regime-badge"
        >
          {config.label}
        </span>
      </div>

      <div className="flex h-8 rounded overflow-hidden" data-testid="cluster-bar">
        {data.map((d, i) => {
          const rc = REGIME_CONFIG[d.regime];
          return (
            <div
              key={`${d.timestamp}-${i}`}
              className="flex-1 min-w-1"
              style={{ backgroundColor: rc.color, opacity: 0.8 }}
              title={`${new Date(d.timestamp).toLocaleDateString()}: ${rc.label}`}
            />
          );
        })}
      </div>

      <div className="flex gap-4 mt-3 text-xs text-muted">
        {Object.entries(durations).map(([regime, count]) => {
          const rc = REGIME_CONFIG[regime as VolatilityRegime];
          return (
            <div key={regime} className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full" style={{ backgroundColor: rc.color }} />
              <span>{rc.label}: {((count / total) * 100).toFixed(0)}%</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
