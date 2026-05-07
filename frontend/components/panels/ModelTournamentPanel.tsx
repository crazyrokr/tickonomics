"use client";

interface ModelEntry {
  name: string;
  sharpe: number;
  bestRegime: string;
}

interface ShapEntry {
  feature: string;
  importance: number;
}

interface ModelTournamentPanelProps {
  models: ModelEntry[];
  shapData: ShapEntry[];
  isLoading?: boolean;
}

function SharpeCard({ model, isBest }: { model: ModelEntry; isBest: boolean }) {
  const sharpeColor = model.sharpe > 1 ? "var(--color-ili-green)" : model.sharpe > 0 ? "var(--color-ili-blue)" : "var(--color-ili-red)";

  return (
    <div
      className={`p-3 rounded-lg border ${isBest ? "border-ili-green/40 bg-ili-green/5" : "border-border"}`}
      data-testid={`model-card-${model.name}`}
    >
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm font-medium">{model.name}</span>
        {isBest && (
          <span className="text-xs text-ili-green font-medium">Best</span>
        )}
      </div>
      <div className="flex items-end justify-between">
        <div>
          <p className="text-xs text-muted">Sharpe</p>
          <p className="text-xl font-bold font-mono" style={{ color: sharpeColor }}>
            {model.sharpe.toFixed(2)}
          </p>
        </div>
        <div className="text-right">
          <p className="text-xs text-muted">Best Regime</p>
          <p className="text-xs font-medium">{model.bestRegime}</p>
        </div>
      </div>
    </div>
  );
}

export function ModelTournamentPanel({ models, shapData, isLoading }: ModelTournamentPanelProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="grid grid-cols-2 gap-3 mb-3">
          <div className="h-24 bg-border rounded" />
          <div className="h-24 bg-border rounded" />
        </div>
        <div className="h-32 bg-border rounded" />
      </div>
    );
  }

  const bestSharpe = Math.max(...models.map((m) => m.sharpe), -Infinity);

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Model Tournament</h3>

      <div className="grid grid-cols-2 gap-3 mb-4" data-testid="model-cards">
        {models.map((model) => (
          <SharpeCard key={model.name} model={model} isBest={model.sharpe === bestSharpe} />
        ))}
      </div>

      {shapData.length > 0 && (
        <div>
          <p className="text-xs text-muted font-medium mb-2">SHAP Feature Importance</p>
          <div className="space-y-1.5" data-testid="shap-bars">
            {shapData
              .sort((a, b) => b.importance - a.importance)
              .map((entry) => {
                const maxImportance = Math.max(...shapData.map((s) => s.importance), 0.01);
                const widthPct = (entry.importance / maxImportance) * 100;
                return (
                  <div key={entry.feature} className="flex items-center gap-2" data-testid={`shap-${entry.feature}`}>
                    <span className="text-xs text-muted w-24 truncate" title={entry.feature}>{entry.feature}</span>
                    <div className="flex-1 h-4 bg-border/50 rounded overflow-hidden">
                      <div
                        className="h-full rounded transition-all duration-300"
                        style={{ width: `${widthPct}%`, backgroundColor: "var(--color-ili-blue)", opacity: 0.7 }}
                      />
                    </div>
                    <span className="text-xs font-mono w-12 text-right">{entry.importance.toFixed(3)}</span>
                  </div>
                );
              })}
          </div>
        </div>
      )}
    </div>
  );
}
