"use client";

interface ConvergencePlotProps {
  cumulativeMean: number[];
  cumulativeVariance: number[];
  sampleSize: number;
  stable: boolean;
  isLoading?: boolean;
}

function MiniLine({ data, color, width = 200, height = 60 }: { data: number[]; color: string; width?: number; height?: number }) {
  if (data.length < 2) return null;

  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const padding = 2;

  const points = data
    .map((val, i) => {
      const x = padding + (i / (data.length - 1)) * (width - 2 * padding);
      const y = height - padding - ((val - min) / range) * (height - 2 * padding);
      return `${x},${y}`;
    })
    .join(" ");

  return (
    <svg width={width} height={height}>
      <polyline
        points={points}
        fill="none"
        stroke={color}
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function ConvergencePlot({ cumulativeMean, cumulativeVariance, sampleSize, stable, isLoading }: ConvergencePlotProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="grid grid-cols-2 gap-4">
          <div className="h-24 bg-border rounded" />
          <div className="h-24 bg-border rounded" />
        </div>
      </div>
    );
  }

  const meanValue = cumulativeMean.length > 0 ? cumulativeMean[cumulativeMean.length - 1] : 0;
  const varianceValue = cumulativeVariance.length > 0 ? cumulativeVariance[cumulativeVariance.length - 1] : 0;

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-medium text-muted">Convergence Diagnostics</h3>
        <div className="flex items-center gap-2" data-testid="stability-indicator">
          <span className={`w-2 h-2 rounded-full ${stable ? "bg-ili-green" : "bg-ili-red"}`} />
          <span className={`text-xs font-medium ${stable ? "text-ili-green" : "text-ili-red"}`}>
            {stable ? "Stable" : "Unstable"}
          </span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 mb-3">
        <div className="p-2 rounded border border-border">
          <div className="flex items-center justify-between mb-1">
            <p className="text-xs text-muted">Cumulative Mean</p>
            <span className="text-sm font-mono font-medium" data-testid="mean-value">
              {meanValue.toFixed(4)}
            </span>
          </div>
          <MiniLine data={cumulativeMean} color="var(--color-ili-blue)" />
        </div>

        <div className="p-2 rounded border border-border">
          <div className="flex items-center justify-between mb-1">
            <p className="text-xs text-muted">Cumulative Variance</p>
            <span className="text-sm font-mono font-medium" data-testid="variance-value">
              {varianceValue.toFixed(4)}
            </span>
          </div>
          <MiniLine data={cumulativeVariance} color="var(--color-ili-amber)" />
        </div>
      </div>

      <div className="flex items-center justify-between text-xs text-muted border-t border-border pt-2">
        <span>Sample Size: <span className="font-mono font-medium" data-testid="sample-size">{sampleSize.toLocaleString()}</span></span>
        <span>{cumulativeMean.length} cumulative points</span>
      </div>
    </div>
  );
}
