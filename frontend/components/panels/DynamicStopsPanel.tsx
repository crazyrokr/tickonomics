"use client";

interface PositionStop {
  symbol: string;
  stopLoss: number;
  takeProfit: number;
  state: "signal-active" | "signal-exhausted";
}

interface DynamicStopsPanelProps {
  positions: PositionStop[];
  isLoading?: boolean;
}

const STATE_CONFIG = {
  "signal-active": {
    label: "Active",
    dotClass: "bg-ili-green",
    bgClass: "bg-ili-green/15",
    textClass: "text-ili-green",
    borderClass: "border-ili-green/30",
  },
  "signal-exhausted": {
    label: "Exhausted",
    dotClass: "bg-ili-red",
    bgClass: "bg-ili-red/15",
    textClass: "text-ili-red",
    borderClass: "border-ili-red/30",
  },
} as const;

export function DynamicStopsPanel({ positions, isLoading }: DynamicStopsPanelProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="space-y-2">
          <div className="h-14 bg-border rounded" />
          <div className="h-14 bg-border rounded" />
          <div className="h-14 bg-border rounded" />
        </div>
      </div>
    );
  }

  if (positions.length === 0) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <h3 className="text-sm font-medium text-muted mb-3">Dynamic Stops</h3>
        <p className="text-sm text-muted text-center py-8">No active positions</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Dynamic Stops</h3>

      <div className="space-y-2">
        {positions.map((pos) => {
          const stateConfig = STATE_CONFIG[pos.state];
          return (
            <div
              key={pos.symbol}
              className={`flex items-center justify-between p-3 rounded border ${stateConfig.borderClass} ${stateConfig.bgClass}`}
              data-testid={`stop-${pos.symbol}`}
            >
              <div className="flex items-center gap-2">
                <span className="font-medium text-sm">{pos.symbol}</span>
                <span className={`w-2 h-2 rounded-full ${stateConfig.dotClass}`} />
              </div>

              <div className="flex items-center gap-4">
                <div className="text-right">
                  <p className="text-xs text-muted">Stop Loss</p>
                  <p className="text-sm font-mono text-ili-red">{pos.stopLoss.toFixed(2)}</p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-muted">Take Profit</p>
                  <p className="text-sm font-mono text-ili-green">{pos.takeProfit.toFixed(2)}</p>
                </div>
                <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${stateConfig.bgClass} ${stateConfig.textClass}`}>
                  {stateConfig.label}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
