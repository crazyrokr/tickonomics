"use client";

interface HeatmapCell {
  paramA: string;
  paramB: string;
  value: number;
}

interface BacktestHeatmapProps {
  type: "time" | "parameter";
  data: HeatmapCell[];
  paramALabel: string;
  paramBLabel: string;
  isLoading?: boolean;
}

function getHeatmapColor(value: number, min: number, max: number): string {
  if (max === min) return "var(--color-ili-blue)";
  const t = (value - min) / (max - min);
  if (t < 0.33) return "var(--color-ili-red)";
  if (t < 0.66) return "var(--color-ili-amber)";
  return "var(--color-ili-green)";
}

export function BacktestHeatmap({
  type,
  data,
  paramALabel,
  paramBLabel,
  isLoading,
}: BacktestHeatmapProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-64 bg-border rounded" />
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <h3 className="text-sm font-medium text-muted mb-3">
          {type === "time" ? "Time Sensitivity" : "Parameter Sensitivity"} Heatmap
        </h3>
        <p className="text-sm text-muted text-center py-8">No heatmap data available</p>
      </div>
    );
  }

  const values = data.map((d) => d.value);
  const min = Math.min(...values);
  const max = Math.max(...values);

  const aValues = [...new Set(data.map((d) => d.paramA))];
  const bValues = [...new Set(data.map((d) => d.paramB))];

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3" data-testid="heatmap-title">
        {type === "time" ? "Time Sensitivity" : "Parameter Sensitivity"} Heatmap
      </h3>

      <div className="overflow-x-auto" data-testid="heatmap-grid">
        <table className="text-xs">
          <thead>
            <tr>
              <th className="p-1 text-muted">{paramBLabel} ↓ {paramALabel} →</th>
              {aValues.map((a) => (
                <th key={a} className="p-1 text-muted font-normal">{a}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {bValues.map((b) => (
              <tr key={b}>
                <td className="p-1 text-muted">{b}</td>
                {aValues.map((a) => {
                  const cell = data.find((d) => d.paramA === a && d.paramB === b);
                  if (!cell) return <td key={a} className="p-1" />;
                  return (
                    <td
                      key={a}
                      className="p-1 text-center font-mono rounded-sm min-w-12"
                      style={{ backgroundColor: getHeatmapColor(cell.value, min, max), opacity: 0.7 }}
                      title={`${a} × ${b}: ${cell.value.toFixed(2)}`}
                      data-testid={`cell-${a}-${b}`}
                    >
                      {cell.value.toFixed(2)}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
