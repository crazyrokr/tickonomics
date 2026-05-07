"use client";

interface OptimizerRun {
  method: "Bayesian" | "Firefly";
  lastCalibration: string;
  fitnessScore: number;
  convergenceStatus: "CONVERGED" | "IN_PROGRESS" | "DIVERGED";
  weightHistory: Array<{ date: string; weights: Record<string, number> }>;
}

interface OptimizerStatusPanelProps {
  current: OptimizerRun | undefined;
  comparison?: OptimizerRun | undefined;
  isLoading?: boolean;
}

const CONVERGENCE_CONFIG: Record<string, { label: string; className: string }> = {
  CONVERGED: { label: "Converged", className: "text-ili-green" },
  IN_PROGRESS: { label: "In Progress", className: "text-ili-amber" },
  DIVERGED: { label: "Diverged", className: "text-ili-red" },
};

function OptimizerCard({ run, label }: { run: OptimizerRun; label: string }) {
  const conv = CONVERGENCE_CONFIG[run.convergenceStatus] ?? CONVERGENCE_CONFIG.IN_PROGRESS;

  return (
    <div className="border border-border rounded p-3" data-testid={`optimizer-${label}`}>
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm font-medium">{run.method}</span>
        <span className={`text-xs font-medium ${conv.className}`} data-testid={`convergence-${label}`}>
          {conv.label}
        </span>
      </div>
      <div className="space-y-1 text-sm">
        <div className="flex justify-between">
          <span className="text-muted">Fitness (Sharpe)</span>
          <span className="font-mono">{run.fitnessScore.toFixed(3)}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-muted">Last Calibration</span>
          <span className="text-xs">{new Date(run.lastCalibration).toLocaleDateString()}</span>
        </div>
      </div>
      {run.weightHistory.length > 0 && (
        <div className="mt-2 pt-2 border-t border-border">
          <p className="text-xs text-muted mb-1">Weight Evolution:</p>
          <div className="flex gap-1 flex-wrap">
            {Object.entries(run.weightHistory[run.weightHistory.length - 1].weights).map(
              ([key, val]) => (
                <span key={key} className="text-xs px-1.5 py-0.5 bg-ili-blue/10 text-ili-blue rounded">
                  {key}: {val.toFixed(2)}
                </span>
              ),
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export function OptimizerStatusPanel({ current, comparison, isLoading }: OptimizerStatusPanelProps) {
  if (isLoading || !current) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-32 bg-border rounded" />
      </div>
    );
  }

  const hasComparison = comparison !== undefined;

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Optimizer Status</h3>
      <div className={hasComparison ? "grid grid-cols-2 gap-3" : ""}>
        <OptimizerCard run={current} label="primary" />
        {hasComparison && <OptimizerCard run={comparison} label="comparison" />}
      </div>
    </div>
  );
}
