"use client";

import { ConnectionStatus } from "./ConnectionStatus";

export function Header() {
  return (
    <header className="h-14 border-b border-border flex items-center justify-between px-4 bg-surface">
      <h2 className="text-lg font-medium">Tickonomics Dashboard</h2>
      <div className="flex items-center gap-4">
        <ConnectionStatus />
      </div>
    </header>
  );
}
