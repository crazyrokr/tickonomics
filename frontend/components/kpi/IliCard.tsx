"use client";

import type { IliValue } from "@/types/api";
import { Sparkline } from "@/components/charts/Sparkline";

const STATUS_BADGE: Record<string, { label: string; className: string }> = {
  VALID: { label: "VALID", className: "bg-ili-green text-white" },
  DEGRADED_COMPONENT_STALE: { label: "DEGRADED", className: "bg-ili-amber text-white" },
  DISLOCATED: { label: "DISLOCATED", className: "bg-ili-red text-white" },
};

interface IliCardProps {
  data: IliValue | undefined;
  history?: number[];
  isLoading?: boolean;
}

export function IliCard({ data, history = [], isLoading }: IliCardProps) {
  if (isLoading || !data) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/2 mb-3" />
        <div className="h-8 bg-border rounded w-1/3 mb-2" />
        <div className="h-3 bg-border rounded w-2/3" />
      </div>
    );
  }

  const badge = STATUS_BADGE[data.status] ?? STATUS_BADGE.VALID;
  const isDegraded = data.status !== "VALID";

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <div className="flex items-center justify-between mb-2">
        <h3 className="text-sm font-medium text-muted">ILI</h3>
        <span
          className={`px-2 py-0.5 text-xs rounded-full font-medium ${badge.className}`}
          data-testid="ili-status-badge"
        >
          {badge.label}
        </span>
      </div>
      <div className="text-2xl font-bold mb-2">
        {data.value.toFixed(3)}
      </div>
      {history.length >= 2 && (
        <Sparkline data={history} height={32} />
      )}
      {isDegraded && (
        <div className="mt-3 pt-3 border-t border-border" data-testid="ili-degraded-info">
          <p className="text-xs text-muted mb-1">Active weights:</p>
          <div className="flex flex-wrap gap-1">
            {Object.entries(data.activeWeights).map(([key, weight]) => (
              <span
                key={key}
                className="px-1.5 py-0.5 text-xs bg-ili-blue/10 text-ili-blue rounded"
              >
                {key}: {weight.toFixed(2)}
              </span>
            ))}
          </div>
          {data.excludedComponents.length > 0 && (
            <p className="text-xs text-ili-red mt-1">
              Excluded: {data.excludedComponents.join(", ")}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
