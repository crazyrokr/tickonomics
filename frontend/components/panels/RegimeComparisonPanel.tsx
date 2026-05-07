"use client";

interface RegimeModel {
  name: string;
  regime: string;
  confidence: number;
}

interface RegimeComparisonPanelProps {
  models: RegimeModel[];
  isLoading?: boolean;
}

function getConsensus(models: RegimeModel[]): string {
  if (models.length === 0) return "NO_DATA";
  const counts: Record<string, number> = {};
  for (const m of models) {
    counts[m.regime] = (counts[m.regime] || 0) + 1;
  }
  const sorted = Object.entries(counts).sort((a, b) => b[1] - a[1]);
  return sorted[0][0];
}

function isConsensus(models: RegimeModel[]): boolean {
  if (models.length <= 1) return true;
  return models.every((m) => m.regime === models[0].regime);
}

export function RegimeComparisonPanel({ models, isLoading }: RegimeComparisonPanelProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="space-y-2">
          <div className="h-10 bg-border rounded" />
          <div className="h-10 bg-border rounded" />
        </div>
      </div>
    );
  }

  const consensus = getConsensus(models);
  const agreed = isConsensus(models);

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-medium text-muted">Regime Comparison</h3>
        <div className="flex items-center gap-2" data-testid="consensus-indicator">
          <span className={`w-2 h-2 rounded-full ${agreed ? "bg-ili-green" : "bg-ili-amber"}`} />
          <span className="text-xs text-muted">
            {agreed ? "Consensus" : "Divergent"}: {consensus}
          </span>
        </div>
      </div>

      <div className="space-y-2">
        {models.map((model) => (
          <div
            key={model.name}
            className={`flex items-center justify-between p-2 rounded border ${
              model.regime !== consensus ? "border-ili-amber bg-ili-amber/5" : "border-border"
            }`}
            data-testid={`model-${model.name}`}
          >
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium">{model.name}</span>
              {model.regime !== consensus && (
                <span className="text-xs text-ili-amber" data-testid="divergence-marker">
                  diverges
                </span>
              )}
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm">{model.regime}</span>
              <span className="text-xs text-muted">({(model.confidence * 100).toFixed(0)}%)</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
