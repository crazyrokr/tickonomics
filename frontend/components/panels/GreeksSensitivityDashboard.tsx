"use client";

interface Greeks {
  repoDelta: number;
  rateDelta: number;
  rateGamma: number;
  spreadDelta: number;
  volga: number;
}

interface GreeksSensitivityDashboardProps {
  greeks: Greeks;
  dv01: number;
  convexity: number;
  isLoading?: boolean;
}

function getMagnitudeColor(value: number): string {
  const abs = Math.abs(value);
  if (abs > 0.5) return "var(--color-ili-red)";
  if (abs > 0.2) return "var(--color-ili-amber)";
  return "var(--color-ili-green)";
}

function getMagnitudeBg(value: number): string {
  const abs = Math.abs(value);
  if (abs > 0.5) return "bg-ili-red/10";
  if (abs > 0.2) return "bg-ili-amber/10";
  return "bg-ili-green/10";
}

function GreekCard({ label, value }: { label: string; value: number }) {
  return (
    <div className={`p-3 rounded-lg border border-border ${getMagnitudeBg(value)}`} data-testid={`greek-${label.replace(/\s/g, "-").toLowerCase()}`}>
      <p className="text-xs text-muted mb-1">{label}</p>
      <p className="text-lg font-bold font-mono" style={{ color: getMagnitudeColor(value) }}>
        {value >= 0 ? "+" : ""}{value.toFixed(4)}
      </p>
    </div>
  );
}

function GaugeBar({ label, value, maxAbs }: { label: string; value: number; maxAbs: number }) {
  const pct = maxAbs > 0 ? Math.min((Math.abs(value) / maxAbs) * 100, 100) : 0;
  const color = getMagnitudeColor(value);

  return (
    <div data-testid={`gauge-${label.replace(/\s/g, "-").toLowerCase()}`}>
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs text-muted">{label}</span>
        <span className="text-sm font-mono font-medium">{value.toFixed(2)}</span>
      </div>
      <div className="w-full h-2 bg-border rounded-full overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-300"
          style={{ width: `${pct}%`, backgroundColor: color }}
          data-testid={`gauge-fill-${label.replace(/\s/g, "-").toLowerCase()}`}
        />
      </div>
    </div>
  );
}

export function GreeksSensitivityDashboard({ greeks, dv01, convexity, isLoading }: GreeksSensitivityDashboardProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="grid grid-cols-3 gap-2 mb-3">
          <div className="h-16 bg-border rounded" />
          <div className="h-16 bg-border rounded" />
          <div className="h-16 bg-border rounded" />
          <div className="h-16 bg-border rounded" />
          <div className="h-16 bg-border rounded" />
        </div>
        <div className="h-8 bg-border rounded" />
      </div>
    );
  }

  const greekEntries = [
    { label: "Repo Delta", value: greeks.repoDelta },
    { label: "Rate Delta", value: greeks.rateDelta },
    { label: "Rate Gamma", value: greeks.rateGamma },
    { label: "Spread Delta", value: greeks.spreadDelta },
    { label: "Volga", value: greeks.volga },
  ];

  const gaugeMax = Math.max(Math.abs(dv01), Math.abs(convexity), 0.01);

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Greeks Sensitivity</h3>

      <div className="grid grid-cols-3 gap-2 mb-4" data-testid="greeks-grid">
        {greekEntries.map(({ label, value }) => (
          <GreekCard key={label} label={label} value={value} />
        ))}
      </div>

      <div className="border-t border-border pt-3 space-y-3" data-testid="risk-gauges">
        <GaugeBar label="DV01" value={dv01} maxAbs={gaugeMax} />
        <GaugeBar label="Convexity" value={convexity} maxAbs={gaugeMax} />
      </div>

      <div className="flex items-center gap-4 mt-3 text-xs text-muted">
        <div className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-full bg-ili-green" />
          <span>Low</span>
        </div>
        <div className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-full bg-ili-amber" />
          <span>Medium</span>
        </div>
        <div className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-full bg-ili-red" />
          <span>High</span>
        </div>
      </div>
    </div>
  );
}
