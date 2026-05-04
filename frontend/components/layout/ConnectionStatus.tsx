"use client";

import type { ConnectionState } from "@/lib/websocket";

interface ConnectionStatusProps {
  state?: ConnectionState;
}

const STATE_CONFIG: Record<
  ConnectionState,
  { label: string; colorClass: string }
> = {
  connected: { label: "Connected", colorClass: "bg-ili-green" },
  connecting: { label: "Connecting", colorClass: "bg-ili-amber" },
  disconnected: { label: "Disconnected", colorClass: "bg-ili-red" },
};

export function ConnectionStatus({ state = "disconnected" }: ConnectionStatusProps) {
  const config = STATE_CONFIG[state];

  return (
    <div className="flex items-center gap-2 text-xs">
      <span
        className={`w-2 h-2 rounded-full ${config.colorClass}`}
        aria-hidden="true"
      />
      <span className="text-muted">{config.label}</span>
    </div>
  );
}
