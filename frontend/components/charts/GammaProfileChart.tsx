"use client";

interface GammaLevel {
  price: number;
  gamma: number;
}

interface GammaProfileChartProps {
  data: GammaLevel[];
  flipZonePrice: number | null;
  enabled: boolean;
  isLoading?: boolean;
}

export function GammaProfileChart({ data, flipZonePrice, enabled, isLoading }: GammaProfileChartProps) {
  if (!enabled) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <h3 className="text-sm font-medium text-muted mb-3">Gamma Profile</h3>
        <p className="text-sm text-muted text-center py-4">Enable options data to view gamma profile</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-48 bg-border rounded" />
      </div>
    );
  }

  if (data.length < 2) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <h3 className="text-sm font-medium text-muted mb-3">Gamma Profile</h3>
        <p className="text-sm text-muted text-center py-4">No gamma data available</p>
      </div>
    );
  }

  const width = 400;
  const height = 200;
  const padding = 20;

  const prices = data.map((d) => d.price);
  const gammas = data.map((d) => d.gamma);
  const minP = Math.min(...prices);
  const maxP = Math.max(...prices);
  const minG = Math.min(...gammas);
  const maxG = Math.max(...gammas);
  const pRange = maxP - minP || 1;
  const gRange = maxG - minG || 1;

  const toX = (p: number) => padding + ((p - minP) / pRange) * (width - 2 * padding);
  const toY = (g: number) => height - padding - ((g - minG) / gRange) * (height - 2 * padding);

  const linePoints = data.map((d) => `${toX(d.price)},${toY(d.gamma)}`).join(" ");

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Gamma Profile</h3>

      <svg width={width} height={height} data-testid="gamma-svg" className="w-full">
        {flipZonePrice !== null && (
          <line
            x1={toX(flipZonePrice)}
            x2={toX(flipZonePrice)}
            y1={padding}
            y2={height - padding}
            stroke="var(--color-ili-amber)"
            strokeWidth="2"
            strokeDasharray="6,3"
            data-testid="flip-zone-line"
          />
        )}
        <polyline
          points={linePoints}
          fill="none"
          stroke="var(--color-ili-blue)"
          strokeWidth="2"
        />
      </svg>

      <div className="flex gap-4 mt-2 text-xs text-muted">
        <span>Long Gamma <span className="text-ili-green">(dampening)</span></span>
        <span>Short Gamma <span className="text-ili-red">(amplifying)</span></span>
      </div>
      {flipZonePrice !== null && (
        <p className="text-xs text-ili-amber mt-1">
          Gamma Flip Zone: {flipZonePrice.toFixed(2)}
        </p>
      )}
    </div>
  );
}
