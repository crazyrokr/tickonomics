"use client";

interface MaPoint {
  timestamp: string;
  price: number;
  ma: number;
}

interface LeverageRegimeIndicatorProps {
  regime: "LEVERAGE_ON" | "LEVERAGE_OFF";
  sp500Position: number;
  ma200: number;
  data: MaPoint[];
  isLoading?: boolean;
}

const REGIME_CONFIG = {
  LEVERAGE_ON: {
    label: "Leverage ON",
    color: "var(--color-ili-green)",
    bgClass: "bg-ili-green",
    bgAlpha: "bg-ili-green/15",
    textClass: "text-ili-green",
  },
  LEVERAGE_OFF: {
    label: "Leverage OFF",
    color: "var(--color-ili-red)",
    bgClass: "bg-ili-red",
    bgAlpha: "bg-ili-red/15",
    textClass: "text-ili-red",
  },
} as const;

export function LeverageRegimeIndicator({ regime, sp500Position, ma200, data, isLoading }: LeverageRegimeIndicatorProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-8 bg-border rounded w-1/2 mb-3" />
        <div className="h-24 bg-border rounded" />
      </div>
    );
  }

  const config = REGIME_CONFIG[regime];
  const aboveMa = sp500Position > ma200;
  const distancePct = ma200 !== 0 ? ((sp500Position - ma200) / ma200) * 100 : 0;

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Leverage Regime</h3>

      <div className="flex items-center gap-2 mb-4">
        <span className={`w-3 h-3 rounded-full ${config.bgClass}`} />
        <span
          className={`px-2.5 py-1 text-sm rounded-full font-medium text-white ${config.bgClass}`}
          data-testid="regime-badge"
        >
          {config.label}
        </span>
      </div>

      <div className="grid grid-cols-2 gap-3 mb-4">
        <div className="p-2 rounded border border-border">
          <p className="text-xs text-muted mb-1">S&amp;P 500</p>
          <p className="text-lg font-bold font-mono">{sp500Position.toFixed(2)}</p>
        </div>
        <div className="p-2 rounded border border-border">
          <p className="text-xs text-muted mb-1">200-Day MA</p>
          <p className="text-lg font-bold font-mono">{ma200.toFixed(2)}</p>
        </div>
      </div>

      <div className="flex items-center justify-between text-sm mb-3" data-testid="position-vs-ma">
        <span className="text-muted">Position vs MA</span>
        <span className={`font-mono font-medium ${aboveMa ? "text-ili-green" : "text-ili-red"}`}>
          {aboveMa ? "+" : ""}{distancePct.toFixed(2)}%
        </span>
      </div>

      {data.length > 1 && (
        <div className="h-24 flex items-end gap-px" data-testid="regime-spark">
          {data.slice(-50).map((point, i) => {
            const prices = data.slice(-50).map((d) => d.price);
            const maxPrice = Math.max(...prices);
            const minPrice = Math.min(...prices);
            const range = maxPrice - minPrice || 1;
            const barHeight = Math.max(((point.price - minPrice) / range) * 100, 2);
            return (
              <div
                key={i}
                className="flex-1 rounded-t"
                style={{
                  height: `${barHeight}%`,
                  backgroundColor: point.price >= point.ma ? config.color : "var(--color-ili-red)",
                  opacity: 0.5,
                }}
                title={`${new Date(point.timestamp).toLocaleDateString()}: ${point.price.toFixed(2)}`}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}
