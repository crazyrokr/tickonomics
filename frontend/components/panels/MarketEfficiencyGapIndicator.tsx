"use client";

interface AssetBreakdown {
  asset: string;
  atVolume: number;
  fundamentalVolume: number;
}

interface MarketEfficiencyGapIndicatorProps {
  gap: number;
  atDriven: boolean;
  breakdown: AssetBreakdown[];
  isLoading?: boolean;
}

export function MarketEfficiencyGapIndicator({ gap, atDriven, breakdown, isLoading }: MarketEfficiencyGapIndicatorProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/2 mb-3" />
        <div className="h-8 bg-border rounded w-1/3 mb-3" />
        <div className="space-y-2">
          <div className="h-8 bg-border rounded" />
          <div className="h-8 bg-border rounded" />
        </div>
      </div>
    );
  }

  const gapColor = gap > 0.5 ? "var(--color-ili-red)" : gap > 0.2 ? "var(--color-ili-amber)" : "var(--color-ili-green)";

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Market Efficiency Gap</h3>

      <div className="flex items-center gap-3 mb-4">
        <span className="text-3xl font-bold" style={{ color: gapColor }} data-testid="gap-value">
          {gap.toFixed(3)}
        </span>
        {atDriven && (
          <span
            className="px-2 py-0.5 text-xs rounded-full font-medium bg-ili-amber/15 text-ili-amber"
            data-testid="at-driven-badge"
          >
            AT-Driven
          </span>
        )}
      </div>

      <div className="w-full h-2 bg-border rounded-full overflow-hidden mb-4">
        <div
          className="h-full rounded-full transition-all duration-300"
          style={{ width: `${Math.min(gap * 100, 100)}%`, backgroundColor: gapColor }}
          data-testid="gap-bar"
        />
      </div>

      {breakdown.length > 0 && (
        <div>
          <p className="text-xs text-muted mb-2">Volume Breakdown by Asset</p>
          <div className="overflow-x-auto">
            <table className="w-full text-sm" data-testid="breakdown-table">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left text-xs text-muted font-medium pb-2">Asset</th>
                  <th className="text-right text-xs text-muted font-medium pb-2">AT Volume</th>
                  <th className="text-right text-xs text-muted font-medium pb-2">Fundamental</th>
                </tr>
              </thead>
              <tbody>
                {breakdown.map((row) => {
                  const total = row.atVolume + row.fundamentalVolume;
                  const atPct = total > 0 ? (row.atVolume / total) * 100 : 0;
                  return (
                    <tr key={row.asset} className="border-b border-border/50" data-testid={`breakdown-${row.asset}`}>
                      <td className="py-2 font-medium">{row.asset}</td>
                      <td className="py-2 text-right font-mono">{row.atVolume.toFixed(2)}</td>
                      <td className="py-2 text-right">
                        <span className="font-mono">{row.fundamentalVolume.toFixed(2)}</span>
                        <span className="text-xs text-muted ml-1">({atPct.toFixed(0)}% AT)</span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
