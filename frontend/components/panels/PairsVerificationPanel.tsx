"use client";

interface SpreadPoint {
  timestamp: string;
  spread: number;
}

interface PairsVerificationPanelProps {
  pair: string;
  distance: number;
  iliSignal: string;
  pairsSignal: string;
  formationEnd: string | null;
  data: SpreadPoint[];
  isLoading?: boolean;
}

export function PairsVerificationPanel({
  pair,
  distance,
  iliSignal,
  pairsSignal,
  formationEnd,
  data,
  isLoading,
}: PairsVerificationPanelProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-48 bg-border rounded" />
      </div>
    );
  }

  const signalsDiverge = iliSignal !== pairsSignal;

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Pairs Verification</h3>

      <div className="flex items-center justify-between mb-4">
        <span className="text-lg font-semibold" data-testid="pair-name">{pair}</span>
        {signalsDiverge && (
          <span
            className="px-2 py-0.5 text-xs rounded-full font-medium bg-ili-amber/15 text-ili-amber"
            data-testid="divergence-badge"
          >
            Signals Diverge
          </span>
        )}
      </div>

      <div className="grid grid-cols-2 gap-3 mb-4">
        <div className="p-2 rounded border border-border">
          <p className="text-xs text-muted mb-1">Current Distance</p>
          <p className="text-lg font-bold font-mono">{distance.toFixed(4)}</p>
        </div>
        <div className="p-2 rounded border border-border">
          <p className="text-xs text-muted mb-1">Formation End</p>
          <p className="text-sm font-mono">
            {formationEnd ? new Date(formationEnd).toLocaleDateString() : "Active"}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 mb-4">
        <div className={`p-2 rounded border ${signalsDiverge ? "border-border" : "border-ili-green/30 bg-ili-green/5"}`}>
          <p className="text-xs text-muted mb-1">ILI Signal</p>
          <p className="text-sm font-medium">{iliSignal}</p>
        </div>
        <div className={`p-2 rounded border ${signalsDiverge ? "border-ili-amber/30 bg-ili-amber/5" : "border-ili-green/30 bg-ili-green/5"}`}>
          <p className="text-xs text-muted mb-1">Pairs Signal</p>
          <p className="text-sm font-medium">{pairsSignal}</p>
        </div>
      </div>

      {data.length > 0 && (
        <div>
          <p className="text-xs text-muted mb-2">Spread History</p>
          <div className="h-20 flex items-end gap-px" data-testid="spread-bars">
            {data.slice(-40).map((point, i) => {
              const maxAbs = Math.max(...data.slice(-40).map((d) => Math.abs(d.spread)), 1);
              const barHeight = Math.max((Math.abs(point.spread) / maxAbs) * 100, 2);
              return (
                <div
                  key={i}
                  className="flex-1 rounded-t"
                  style={{
                    height: `${barHeight}%`,
                    backgroundColor: point.spread >= 0 ? "var(--color-ili-blue)" : "var(--color-ili-red)",
                    opacity: 0.6,
                  }}
                  title={`${new Date(point.timestamp).toLocaleDateString()}: ${point.spread.toFixed(4)}`}
                />
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
