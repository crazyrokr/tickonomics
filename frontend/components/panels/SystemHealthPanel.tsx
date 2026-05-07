"use client";

import type { HealthResponse } from "@/types/api";

interface SystemHealthPanelProps {
  data: HealthResponse | undefined;
  isLoading?: boolean;
}

const CB_STATE_CONFIG: Record<string, { label: string; className: string }> = {
  CLOSED: { label: "Closed", className: "bg-ili-green text-white" },
  OPEN: { label: "Open", className: "bg-ili-red text-white" },
  HALF_OPEN: { label: "Half-Open", className: "bg-ili-amber text-white" },
};

export function SystemHealthPanel({ data, isLoading }: SystemHealthPanelProps) {
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
      <h3 className="text-sm font-medium text-muted mb-3">System Health</h3>

      <div className="space-y-3">
        <div>
          <h4 className="text-xs font-medium text-muted mb-1">TimescaleDB</h4>
          <div className="flex items-center gap-2 text-sm">
            <span
              className={`w-2 h-2 rounded-full ${
                data.timescaleDb.connected ? "bg-ili-green" : "bg-ili-red"
              }`}
            />
            <span>{data.timescaleDb.connected ? "Connected" : "Disconnected"}</span>
            {data.timescaleDb.connected && (
              <span className="text-xs text-muted">
                Compression: {data.timescaleDb.compressionStatus} | Lag: {data.timescaleDb.aggregateLagSeconds}s
              </span>
            )}
          </div>
        </div>

        <div>
          <h4 className="text-xs font-medium text-muted mb-1">Circuit Breakers</h4>
          <div className="flex flex-wrap gap-2">
            {Object.entries(data.circuitBreakers).map(([name, state]) => {
              const config = CB_STATE_CONFIG[state] ?? CB_STATE_CONFIG.CLOSED;
              return (
                <div key={name} className="flex items-center gap-1.5 text-sm">
                  <span className="text-muted">{name}</span>
                  <span
                    className={`px-1.5 py-0.5 text-xs rounded-full font-medium ${config.className}`}
                    data-testid={`cb-${name}`}
                  >
                    {config.label}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
