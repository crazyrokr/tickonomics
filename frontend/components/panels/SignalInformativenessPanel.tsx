"use client";

interface SignalRow {
  signalId: string;
  pjr: number;
  car: number;
}

interface SignalInformativenessPanelProps {
  data: SignalRow[];
  isLoading?: boolean;
}

function getSignalLabel(pjr: number): { text: string; className: string } {
  if (pjr > 0.6) return { text: "Informed", className: "bg-ili-green/15 text-ili-green" };
  if (pjr < 0.4) return { text: "Noise", className: "bg-ili-red/15 text-ili-red" };
  return { text: "Mixed", className: "bg-ili-amber/15 text-ili-amber" };
}

export function SignalInformativenessPanel({ data, isLoading }: SignalInformativenessPanelProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="space-y-2">
          <div className="h-8 bg-border rounded" />
          <div className="h-8 bg-border rounded" />
          <div className="h-8 bg-border rounded" />
        </div>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <h3 className="text-sm font-medium text-muted mb-3">Signal Informativeness</h3>
        <p className="text-sm text-muted text-center py-8">No signal data available</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Signal Informativeness</h3>

      <div className="overflow-x-auto">
        <table className="w-full text-sm" data-testid="signal-table">
          <thead>
            <tr className="border-b border-border">
              <th className="text-left text-xs text-muted font-medium pb-2">Signal</th>
              <th className="text-right text-xs text-muted font-medium pb-2">PJR</th>
              <th className="text-right text-xs text-muted font-medium pb-2">CAR</th>
              <th className="text-right text-xs text-muted font-medium pb-2">Classification</th>
            </tr>
          </thead>
          <tbody>
            {data.map((row) => {
              const label = getSignalLabel(row.pjr);
              return (
                <tr key={row.signalId} className="border-b border-border/50" data-testid={`signal-${row.signalId}`}>
                  <td className="py-2 font-medium text-sm">{row.signalId}</td>
                  <td className="py-2 text-right font-mono">{row.pjr.toFixed(3)}</td>
                  <td className="py-2 text-right font-mono">{row.car.toFixed(3)}</td>
                  <td className="py-2 text-right">
                    <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${label.className}`}>
                      {label.text}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
