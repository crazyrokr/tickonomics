"use client";

import { useEffect, useRef } from "react";
import type { CorrelationEntry } from "@/types/api";

interface CorrelationMatrixProps {
  data: CorrelationEntry[];
  isLoading?: boolean;
}

export function CorrelationMatrix({ data, isLoading }: CorrelationMatrixProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current || data.length === 0) return;

    let disposed = false;

    async function init() {
      if (disposed) return;

      const perspective = await import("@perspective-dev/client");
      await import("@perspective-dev/viewer");
      await import("@perspective-dev/viewer-datagrid");

      if (disposed || !containerRef.current) return;

      const worker = await perspective.worker();
      const table = await worker.table(
        data.map((d) => ({
          source: d.source,
          target: d.target,
          correlation: d.correlation,
          pValue: d.pValue,
          sampleSize: d.sampleSize,
          aicLagOrder: d.aicLagOrder,
        })),
      );

      const viewer = document.createElement("perspective-viewer") as HTMLElement & {
        load: (t: unknown) => void;
      };
      viewer.load(table);
      viewer.setAttribute("row-pivots", '["source"]');
      viewer.setAttribute("columns", '["correlation","pValue","sampleSize","aicLagOrder"]');
      viewer.setAttribute("sort", '[["correlation","desc"]]');
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
  }, [data]);

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
      <h3 className="text-sm font-medium text-muted mb-3">Correlation Matrix</h3>
      <div ref={containerRef} data-testid="correlation-matrix" />
      {data.length === 0 && (
        <p className="text-sm text-muted text-center py-8">No correlation data available</p>
      )}
    </div>
  );
}
