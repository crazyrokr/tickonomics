"use client";

import { useEffect, useRef } from "react";
import type { SignalMarker } from "@/types/api";

interface SignalLogProps {
  signals: SignalMarker[];
  isLoading?: boolean;
}

export function SignalLog({ signals, isLoading }: SignalLogProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current || signals.length === 0) return;

    let disposed = false;

    async function init() {
      if (disposed) return;

      const perspective = await import("@finos/perspective");
      await import("@finos/perspective-viewer");
      await import("@finos/perspective-viewer-datagrid");

      if (disposed || !containerRef.current) return;

      const worker = await perspective.worker();
      const table = await worker.table(
        signals.map((s) => ({
          timestamp: s.timestamp,
          statusCode: s.statusCode,
          direction: s.direction,
          symbol: s.symbol,
          iliValue: s.iliValue,
        })),
      );

      const viewer = document.createElement("perspective-viewer") as HTMLElement & {
        load: (t: unknown) => void;
      };
      viewer.load(table);
      viewer.setAttribute("columns", '["timestamp","statusCode","direction","symbol","iliValue"]');
      viewer.setAttribute("sort", '[["timestamp","desc"]]');
      viewer.setAttribute("plugin", "Datagrid");
      viewer.style.height = "400px";
      viewer.style.width = "100%";

      containerRef.current.innerHTML = "";
      containerRef.current.appendChild(viewer);
    }

    init();

    return () => {
      disposed = true;
    };
  }, [signals]);

  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-96 bg-border rounded" />
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Signal Log</h3>
      <div ref={containerRef} data-testid="signal-log" />
      {signals.length === 0 && (
        <p className="text-sm text-muted text-center py-8">No signals recorded</p>
      )}
    </div>
  );
}
