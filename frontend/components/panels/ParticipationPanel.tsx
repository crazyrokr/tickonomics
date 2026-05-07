"use client";

import { useState } from "react";

interface SuppressionEvent {
  source: string;
  reason: string;
  timestamp: string;
  triggeringCondition: string;
}

interface ParticipationStatus {
  source: string;
  admissible: boolean;
  suppressionReasons: string[];
  history: SuppressionEvent[];
}

interface ParticipationPanelProps {
  statuses: ParticipationStatus[];
  isLoading?: boolean;
}

export function ParticipationPanel({ statuses, isLoading }: ParticipationPanelProps) {
  const [expandedSource, setExpandedSource] = useState<string | null>(null);

  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="space-y-2">
          <div className="h-10 bg-border rounded" />
          <div className="h-10 bg-border rounded" />
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Participation Governance</h3>
      <div className="space-y-2">
        {statuses.map((status) => (
          <div key={status.source} className="border border-border rounded">
            <button
              className="w-full flex items-center justify-between p-2 text-sm"
              onClick={() =>
                setExpandedSource(expandedSource === status.source ? null : status.source)
              }
              data-testid={`participation-toggle-${status.source}`}
            >
              <div className="flex items-center gap-2">
                <span
                  className={`w-2 h-2 rounded-full ${status.admissible ? "bg-ili-green" : "bg-ili-red"}`}
                />
                <span className="font-medium">{status.source}</span>
              </div>
              <span className="text-xs text-muted">
                {status.admissible ? "Admissible" : "Suppressed"}
              </span>
            </button>

            {expandedSource === status.source && (
              <div className="px-2 pb-2 border-t border-border" data-testid={`participation-detail-${status.source}`}>
                {status.suppressionReasons.length > 0 && (
                  <div className="mt-2">
                    <p className="text-xs text-muted mb-1">Active suppression reasons:</p>
                    <ul className="text-xs space-y-0.5">
                      {status.suppressionReasons.map((reason, i) => (
                        <li key={i} className="text-ili-amber">• {reason}</li>
                      ))}
                    </ul>
                  </div>
                )}
                {status.history.length > 0 && (
                  <div className="mt-2">
                    <p className="text-xs text-muted mb-1">Recent events:</p>
                    {status.history.slice(0, 5).map((event, i) => (
                      <div key={i} className="text-xs py-0.5">
                        <span className="text-muted">{new Date(event.timestamp).toLocaleString()}</span>
                        {" — "}
                        <span>{event.reason}</span>
                        <span className="text-muted"> ({event.triggeringCondition})</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
