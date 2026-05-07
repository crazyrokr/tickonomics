"use client";

interface TraderTypeRow {
  symbol: string;
  algorithmic: number;
  institutional: number;
  professional: number;
  retail: number;
  spreadDiff: number;
}

interface TraderTypePanelProps {
  data: TraderTypeRow[];
  isLoading?: boolean;
}

const TRADER_TYPES = [
  { key: "algorithmic", label: "Algorithmic", color: "var(--color-ili-blue)" },
  { key: "institutional", label: "Institutional", color: "var(--color-ili-green)" },
  { key: "professional", label: "Professional", color: "var(--color-ili-amber)" },
  { key: "retail", label: "Retail", color: "var(--color-ili-red)" },
] as const;

export function TraderTypePanel({ data, isLoading }: TraderTypePanelProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="space-y-3">
          <div className="h-10 bg-border rounded" />
          <div className="h-10 bg-border rounded" />
          <div className="h-10 bg-border rounded" />
        </div>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <h3 className="text-sm font-medium text-muted mb-3">Trader Type Decomposition</h3>
        <p className="text-sm text-muted text-center py-8">No trader type data available</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Trader Type Decomposition</h3>

      <div className="space-y-3">
        {data.map((row) => {
          const total = row.algorithmic + row.institutional + row.professional + row.retail;
          if (total === 0) return null;

          return (
            <div key={row.symbol} data-testid={`trader-row-${row.symbol}`}>
              <div className="flex items-center justify-between mb-1">
                <span className="text-sm font-medium">{row.symbol}</span>
                <span
                  className={`text-xs font-mono ${row.spreadDiff < 0 ? "text-ili-green" : row.spreadDiff > 0 ? "text-ili-red" : "text-muted"}`}
                  data-testid={`spread-${row.symbol}`}
                >
                  spread: {row.spreadDiff >= 0 ? "+" : ""}{row.spreadDiff.toFixed(2)}bps
                </span>
              </div>
              <div className="flex h-6 rounded overflow-hidden" data-testid={`bar-${row.symbol}`}>
                {TRADER_TYPES.map(({ key, color }) => {
                  const value = row[key];
                  const pct = (value / total) * 100;
                  return (
                    <div
                      key={key}
                      className="flex items-center justify-center text-xs text-white font-mono transition-all duration-300"
                      style={{ width: `${pct}%`, backgroundColor: color, minWidth: pct > 5 ? undefined : "2px" }}
                      title={`${key}: ${value.toFixed(1)}%`}
                      data-testid={`segment-${row.symbol}-${key}`}
                    >
                      {pct > 12 ? `${pct.toFixed(0)}%` : ""}
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      <div className="flex flex-wrap gap-3 mt-4 pt-3 border-t border-border">
        {TRADER_TYPES.map(({ key, label, color }) => (
          <div key={key} className="flex items-center gap-1.5 text-xs text-muted">
            <span className="w-2.5 h-2.5 rounded-sm" style={{ backgroundColor: color }} />
            <span>{label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
