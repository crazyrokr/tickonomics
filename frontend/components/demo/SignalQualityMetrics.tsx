import type { DemoSignalQuality } from "@/lib/api";

interface SignalQualityMetricsProps {
  report: DemoSignalQuality | undefined;
  isLoading: boolean;
}

function percent(value: number | undefined): string {
  if (value === undefined) {
    return "—";
  }
  return `${(value * 100).toFixed(1)}%`;
}

export function SignalQualityMetrics({ report, isLoading }: SignalQualityMetricsProps) {
  if (isLoading) {
    return <div className="text-sm text-slate-400">Loading signal quality…</div>;
  }
  if (!report) {
    return <div className="text-sm text-slate-400">No signal quality report available.</div>;
  }

  const metrics = [
    { label: "5-Day Hit Rate", value: percent(report.hitRate5d) },
    { label: "False Positive Rate", value: percent(report.falsePositiveRate) },
    { label: "Avg Return / Signal", value: percent(report.avgReturnPerSignal) },
    { label: "Sharpe Ratio", value: report.portfolioSharpe?.toFixed(2) ?? "—" },
    { label: "vs SPY", value: percent(report.vsSpyReturn) },
    { label: "Actionable Signals", value: String(report.actionableSignals ?? 0) },
  ];

  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
      {metrics.map((metric) => (
        <div key={metric.label} className="rounded-md border border-slate-700 bg-slate-900/50 p-4">
          <div className="text-xs uppercase text-slate-400">{metric.label}</div>
          <div className="text-lg font-semibold">{metric.value}</div>
        </div>
      ))}
    </div>
  );
}
