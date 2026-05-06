"use client";

import type { ConfigHistoryEntry } from "@/types/api";

interface ConfigHistoryProps {
  entries: ConfigHistoryEntry[];
  isLoading?: boolean;
}

export function ConfigHistory({ entries, isLoading }: ConfigHistoryProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="space-y-3">
          <div className="h-12 bg-border rounded" />
          <div className="h-12 bg-border rounded" />
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Configuration History</h3>
      {entries.length === 0 ? (
        <p className="text-sm text-muted text-center py-4">No configuration changes recorded</p>
      ) : (
        <div className="space-y-2">
          {entries.map((entry) => (
            <ConfigHistoryRow key={entry.id} entry={entry} />
          ))}
        </div>
      )}
    </div>
  );
}

function ConfigHistoryRow({ entry }: { entry: ConfigHistoryEntry }) {
  return (
    <div className="p-3 border border-border rounded" data-testid={`history-${entry.id}`}>
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs text-muted">
          {new Date(entry.timestamp).toLocaleString()} — {entry.author}
        </span>
      </div>
      <p className="text-sm font-medium mb-1" data-testid={`audit-reason-${entry.id}`}>
        {entry.auditReason}
      </p>
      {entry.diff && (
        <pre className="text-xs font-mono text-muted bg-surface-dark p-2 rounded overflow-x-auto max-h-24">
          {entry.diff}
        </pre>
      )}
    </div>
  );
}
