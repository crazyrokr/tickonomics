"use client";

import type { HealthResponse, DataSourceHealth, ProxyDivergence } from "@/types/api";

interface DataFreshnessPanelProps {
  data?: HealthResponse;
  isLoading?: boolean;
}

function SourceRow({ source }: { source: DataSourceHealth }) {
  return (
    <div className="flex items-center justify-between py-1.5 border-b border-border last:border-0">
      <div className="flex items-center gap-2">
        <span
          className={`w-2 h-2 rounded-full ${source.healthy ? "bg-ili-green" : "bg-ili-red"}`}
          data-testid={`source-dot-${source.name}`}
        />
        <span className="text-sm">{source.name}</span>
      </div>
      <div className="text-xs text-muted">
        {source.lastSync ? new Date(source.lastSync).toLocaleTimeString() : "Never"}
        <span className="ml-2 text-muted/60">({source.latencyMs}ms)</span>
      </div>
    </div>
  );
}

function ProxyDivergenceIndicator({ data }: { data: ProxyDivergence }) {
  return (
    <div
      className={`mt-3 p-2 rounded-md border ${
        data.dislocated ? "border-ili-red bg-ili-red/5" : "border-border"
      }`}
      data-testid="proxy-divergence"
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium">Proxy Divergence</span>
        {data.dislocated && (
          <span className="px-1.5 py-0.5 text-xs bg-ili-red text-white rounded-full">
            DISLOCATED
          </span>
        )}
      </div>
      <div className="flex gap-4 mt-1 text-xs text-muted">
        <span>Correlation 5d: {data.tbillSofrCorrelation5d.toFixed(3)}</span>
        <span>Score: {data.divergenceScore.toFixed(3)}</span>
      </div>
    </div>
  );
}

export function DataFreshnessPanel({ data, isLoading }: DataFreshnessPanelProps) {
  if (isLoading || !data) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/2 mb-3" />
        <div className="space-y-2">
          <div className="h-5 bg-border rounded" />
          <div className="h-5 bg-border rounded" />
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Data Freshness</h3>
      <div className="space-y-0">
        {data.sources.map((source) => (
          <SourceRow key={source.name} source={source} />
        ))}
      </div>
      <ProxyDivergenceIndicator data={data.proxyDivergence} />
    </div>
  );
}
