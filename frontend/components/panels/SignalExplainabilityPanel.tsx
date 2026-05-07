"use client";

interface SignalExplanation {
  signalId: string;
  zScores: Record<string, number>;
  percentileRanks: Record<string, number>;
  activeFilters: string[];
  iliFormula: string;
}

interface SignalExplainabilityPanelProps {
  explanation: SignalExplanation | undefined;
  isLoading?: boolean;
}

export function SignalExplainabilityPanel({ explanation, isLoading }: SignalExplainabilityPanelProps) {
  if (isLoading || !explanation) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="space-y-2">
          <div className="h-8 bg-border rounded" />
          <div className="h-8 bg-border rounded" />
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface" data-testid="signal-explainability">
      <h3 className="text-sm font-medium text-muted mb-3">Signal Explainability</h3>

      <div className="mb-3">
        <p className="text-xs text-muted mb-1">ILI Formula</p>
        <code className="text-sm bg-surface-dark px-2 py-1 rounded block" data-testid="ili-formula">
          {explanation.iliFormula}
        </code>
      </div>

      <div className="mb-3">
        <p className="text-xs text-muted mb-1">Contributing Z-Scores</p>
        <div className="space-y-1">
          {Object.entries(explanation.zScores).map(([key, value]) => {
            const rank = explanation.percentileRanks[key];
            return (
              <div key={key} className="flex items-center justify-between text-sm" data-testid={`zscore-${key}`}>
                <span className="font-medium">{key}</span>
                <div className="flex items-center gap-3">
                  <span className="font-mono">{value.toFixed(3)}</span>
                  <span className="text-xs text-muted">pctl: {rank !== undefined ? `${rank.toFixed(1)}%` : "N/A"}</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {explanation.activeFilters.length > 0 && (
        <div>
          <p className="text-xs text-muted mb-1">Active Filters</p>
          <div className="flex flex-wrap gap-1">
            {explanation.activeFilters.map((filter, i) => (
              <span key={i} className="px-1.5 py-0.5 text-xs bg-ili-blue/10 text-ili-blue rounded" data-testid={`filter-${i}`}>
                {filter}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
