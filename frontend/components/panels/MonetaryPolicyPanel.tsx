"use client";

interface MonetaryPolicyPanelProps {
  balanceSheet: { expanding: boolean; trend: number };
  policyRates: { fedFunds: number; iorb: number; sofrSpread: number };
  yieldCurveShape: string;
  isLoading?: boolean;
}

function RateRow({ label, value, unit = "%" }: { label: string; value: number; unit?: string }) {
  return (
    <div className="flex items-center justify-between py-1.5">
      <span className="text-xs text-muted">{label}</span>
      <span className="text-sm font-mono font-medium">{value.toFixed(2)}{unit}</span>
    </div>
  );
}

export function MonetaryPolicyPanel({ balanceSheet, policyRates, yieldCurveShape, isLoading }: MonetaryPolicyPanelProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="space-y-3">
          <div className="h-12 bg-border rounded" />
          <div className="h-24 bg-border rounded" />
          <div className="h-8 bg-border rounded" />
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Monetary Policy Overview</h3>

      <div className="mb-4 p-3 rounded-lg border border-border" data-testid="balance-sheet">
        <div className="flex items-center justify-between mb-2">
          <p className="text-xs text-muted">Fed Balance Sheet</p>
          <span
            className={`px-2 py-0.5 text-xs rounded-full font-medium text-white ${balanceSheet.expanding ? "bg-ili-green" : "bg-ili-red"}`}
            data-testid="bs-direction"
          >
            {balanceSheet.expanding ? "Expanding" : "Contracting"}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-xs text-muted">Trend</span>
          <span className={`text-sm font-mono font-medium ${balanceSheet.trend > 0 ? "text-ili-green" : "text-ili-red"}`}>
            {balanceSheet.trend >= 0 ? "+" : ""}{balanceSheet.trend.toFixed(2)}B
          </span>
        </div>
      </div>

      <div className="mb-4" data-testid="policy-rates">
        <p className="text-xs text-muted mb-2 font-medium">Policy Rates</p>
        <div className="border-t border-border">
          <RateRow label="Fed Funds Rate" value={policyRates.fedFunds} />
          <RateRow label="IORB" value={policyRates.iorb} />
          <RateRow label="SOFR Spread" value={policyRates.sofrSpread} />
        </div>
      </div>

      <div className="p-3 rounded-lg border border-border" data-testid="yield-curve">
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted">Yield Curve Shape</p>
          <span
            className={`text-sm font-medium ${
              yieldCurveShape === "normal" ? "text-ili-green" :
              yieldCurveShape === "inverted" ? "text-ili-red" :
              "text-ili-amber"
            }`}
            data-testid="curve-shape"
          >
            {yieldCurveShape.charAt(0).toUpperCase() + yieldCurveShape.slice(1)}
          </span>
        </div>
      </div>
    </div>
  );
}
