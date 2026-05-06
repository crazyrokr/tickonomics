"use client";

interface ToxicityCell {
  asset: string;
  venue: string;
  score: number;
}

interface ToxicityHeatmapProps {
  data: ToxicityCell[];
  isLoading?: boolean;
}

function getScoreColor(score: number): string {
  if (score <= 0.33) return "var(--color-ili-green)";
  if (score <= 0.66) return "var(--color-ili-amber)";
  return "var(--color-ili-red)";
}

function getScoreBgClass(score: number): string {
  if (score <= 0.33) return "bg-ili-green/15";
  if (score <= 0.66) return "bg-ili-amber/15";
  return "bg-ili-red/15";
}

export function ToxicityHeatmap({ data, isLoading }: ToxicityHeatmapProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-64 bg-border rounded" />
      </div>
    );
  }

  const assets = [...new Set(data.map((d) => d.asset))];
  const venues = [...new Set(data.map((d) => d.venue))];

  if (assets.length === 0 || venues.length === 0) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <h3 className="text-sm font-medium text-muted mb-3">Order Flow Toxicity</h3>
        <p className="text-sm text-muted text-center py-8">No toxicity data available</p>
      </div>
    );
  }

  const cellMap = new Map<string, ToxicityCell>();
  for (const cell of data) {
    cellMap.set(`${cell.asset}-${cell.venue}`, cell);
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Order Flow Toxicity</h3>

      <div className="overflow-x-auto">
        <table className="w-full text-sm" data-testid="toxicity-grid">
          <thead>
            <tr>
              <th className="text-left text-xs text-muted font-medium p-2" />
              {venues.map((venue) => (
                <th key={venue} className="text-center text-xs text-muted font-medium p-2">
                  {venue}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {assets.map((asset) => (
              <tr key={asset}>
                <td className="text-xs font-medium p-2 whitespace-nowrap">{asset}</td>
                {venues.map((venue) => {
                  const cell = cellMap.get(`${asset}-${venue}`);
                  const score = cell?.score ?? 0.5;
                  return (
                    <td key={venue} className="p-1">
                      <div
                        className={`rounded px-2 py-1.5 text-center text-xs font-mono ${getScoreBgClass(score)}`}
                        style={{ color: getScoreColor(score) }}
                        data-testid={`cell-${asset}-${venue}`}
                      >
                        {score.toFixed(2)}
                      </div>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex items-center gap-3 mt-3 text-xs text-muted">
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded bg-ili-green/30" />
          <span>Beneficial</span>
        </div>
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded bg-ili-amber/30" />
          <span>Neutral</span>
        </div>
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded bg-ili-red/30" />
          <span>Harmful</span>
        </div>
      </div>
    </div>
  );
}
