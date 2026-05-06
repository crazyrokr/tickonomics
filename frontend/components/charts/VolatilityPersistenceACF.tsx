"use client";

interface ACFPoint {
  lag: number;
  acf: number;
}

interface VolatilityPersistenceACFProps {
  rawReturns: ACFPoint[];
  absoluteReturns: ACFPoint[];
  isLoading?: boolean;
}

function ACFBarChart({ data, title, color }: { data: ACFPoint[]; title: string; color: string }) {
  if (data.length === 0) {
    return (
      <div>
        <p className="text-xs text-muted mb-1">{title}</p>
        <p className="text-xs text-muted text-center py-4">No data</p>
      </div>
    );
  }

  const maxAcf = Math.max(...data.map((d) => Math.abs(d.acf)), 0.01);

  return (
    <div>
      <p className="text-xs text-muted mb-2">{title}</p>
      <div className="flex items-end gap-0.5 h-32 border-b border-border pb-1" data-testid={`acf-chart-${title.replace(/\s/g, "-").toLowerCase()}`}>
        {data.map((point) => {
          const barHeight = Math.max((Math.abs(point.acf) / maxAcf) * 100, 1);
          const isPositive = point.acf >= 0;
          return (
            <div
              key={point.lag}
              className="flex-1 min-w-2 flex flex-col justify-end items-center"
              title={`Lag ${point.lag}: ${point.acf.toFixed(3)}`}
              data-testid={`acf-bar-${title.replace(/\s/g, "-").toLowerCase()}-${point.lag}`}
            >
              <div
                className="w-full rounded-t"
                style={{
                  height: `${barHeight}%`,
                  backgroundColor: color,
                  opacity: isPositive ? 0.7 : 0.4,
                  marginBottom: isPositive ? "0" : undefined,
                }}
              />
            </div>
          );
        })}
      </div>
      <div className="flex justify-between text-xs text-muted mt-1">
        <span>Lag 0</span>
        <span>Lag {data.length - 1}</span>
      </div>
    </div>
  );
}

export function VolatilityPersistenceACF({ rawReturns, absoluteReturns, isLoading }: VolatilityPersistenceACFProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="grid grid-cols-2 gap-4">
          <div className="h-32 bg-border rounded" />
          <div className="h-32 bg-border rounded" />
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Volatility Persistence (ACF)</h3>

      <div className="grid grid-cols-2 gap-4">
        <ACFBarChart data={rawReturns} title="Raw Returns ACF" color="var(--color-ili-blue)" />
        <ACFBarChart data={absoluteReturns} title="Absolute Returns ACF" color="var(--color-ili-amber)" />
      </div>

      <div className="mt-3 pt-3 border-t border-border text-xs text-muted">
        <p>Raw returns show weak autocorrelation (efficient market). Absolute returns show persistent clustering (volatility clustering).</p>
      </div>
    </div>
  );
}
