"use client";

import type { VolatilityRegimeData } from "@/types/api";

interface VolatilityRegimeIndicatorProps {
  data: VolatilityRegimeData | undefined;
  isLoading?: boolean;
}

const REGIME_CONFIG: Record<string, { label: string; color: string; dotClass: string }> = {
  LOW_VOL: { label: "Low Volatility", color: "var(--color-ili-green)", dotClass: "bg-ili-green" },
  NORMAL: { label: "Normal", color: "var(--color-ili-blue)", dotClass: "bg-ili-blue" },
  HIGH_VOL: { label: "High Volatility", color: "var(--color-ili-red)", dotClass: "bg-ili-red" },
};

export function VolatilityRegimeIndicator({ data, isLoading }: VolatilityRegimeIndicatorProps) {
  if (isLoading || !data) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/2 mb-3" />
        <div className="h-8 bg-border rounded w-1/3" />
      </div>
    );
  }

  const config = REGIME_CONFIG[data.regime] ?? REGIME_CONFIG.NORMAL;
  const bandWidth = data.upperBand - data.lowerBand;
  const position = bandWidth > 0
    ? ((data.currentPrice - data.lowerBand) / bandWidth) * 100
    : 50;
  const clampedPosition = Math.min(Math.max(position, 0), 100);

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-2">Volatility Regime</h3>
      <div className="flex items-center gap-2 mb-3">
        <span className={`w-3 h-3 rounded-full ${config.dotClass}`} data-testid="regime-dot" />
        <span className="text-lg font-semibold" data-testid="regime-label">{config.label}</span>
      </div>
      <div className="relative w-full h-2 bg-border rounded-full overflow-hidden">
        <div className="absolute inset-0 flex">
          <div className="w-1/3 h-full bg-ili-green/20" />
          <div className="w-1/3 h-full bg-ili-blue/20" />
          <div className="w-1/3 h-full bg-ili-red/20" />
        </div>
        <div
          className="absolute top-1/2 -translate-y-1/2 w-2 h-4 rounded-sm"
          style={{
            left: `calc(${clampedPosition}% - 4px)`,
            backgroundColor: config.color,
          }}
          data-testid="regime-position-marker"
        />
      </div>
      <div className="flex justify-between text-xs text-muted mt-1">
        <span>{data.lowerBand.toFixed(1)}</span>
        <span>{data.currentPrice.toFixed(1)}</span>
        <span>{data.upperBand.toFixed(1)}</span>
      </div>
    </div>
  );
}
