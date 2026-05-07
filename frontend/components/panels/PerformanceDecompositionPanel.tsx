"use client";

interface ReturnGapData {
  holdingsReturn: number;
  returnGap: number;
  timestamp: string;
}

interface PerformanceDecompositionPanelProps {
  data: ReturnGapData | undefined;
  isLoading?: boolean;
}

export function PerformanceDecompositionPanel({ data, isLoading }: PerformanceDecompositionPanelProps) {
  if (isLoading || !data) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/2 mb-3" />
        <div className="h-32 bg-border rounded" />
      </div>
    );
  }

  const totalReturn = data.holdingsReturn + data.returnGap;

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Performance Decomposition</h3>

      <div className="flex items-end gap-4 mb-4" data-testid="decomposition-bars">
        <div className="flex-1">
          <div className="text-xs text-muted mb-1">Holdings Return</div>
          <div
            className="h-16 rounded-t flex items-end justify-center pb-1"
            style={{
              backgroundColor: "var(--color-ili-blue)",
              opacity: 0.7,
              height: `${Math.max(Math.abs(data.holdingsReturn) * 80, 8)}px`,
            }}
            data-testid="holdings-bar"
          >
            <span className="text-xs text-white font-mono">{data.holdingsReturn.toFixed(2)}%</span>
          </div>
        </div>
        <div className="flex-1">
          <div className="text-xs text-muted mb-1">Return Gap (Alpha)</div>
          <div
            className="rounded-t flex items-end justify-center pb-1"
            style={{
              backgroundColor: data.returnGap >= 0 ? "var(--color-ili-green)" : "var(--color-ili-red)",
              opacity: 0.7,
              height: `${Math.max(Math.abs(data.returnGap) * 80, 8)}px`,
            }}
            data-testid="gap-bar"
          >
            <span className="text-xs text-white font-mono">{data.returnGap > 0 ? "+" : ""}{data.returnGap.toFixed(2)}%</span>
          </div>
        </div>
      </div>

      <div className="flex justify-between text-sm border-t border-border pt-2">
        <span className="text-muted">Total</span>
        <span className="font-mono font-medium">{totalReturn.toFixed(2)}%</span>
      </div>
    </div>
  );
}
