"use client";

import { useEffect, useRef, useState } from "react";
import type { SystemicRiskHeatmap } from "@/types/api";

interface LiquidityHeatmapProps {
  data: SystemicRiskHeatmap[] | undefined;
  perspectiveThreshold?: number;
  isLoading?: boolean;
}

const AXIS_LABELS = [
  { key: "triPartyGcfSpread", label: "Tri-Party / GCF Spread" },
  { key: "sofrPctlRange", label: "SOFR 99th-25th Pctl" },
  { key: "tgcrBgcrSpread", label: "TGCR / BGCR Spread" },
  { key: "tgaBalanceChange", label: "TGA Balance Change" },
] as const;

function getQuadrantColor(value: number): string {
  if (value > 0.5) return "var(--color-ili-red)";
  if (value > 0) return "var(--color-ili-amber)";
  return "var(--color-ili-green)";
}

function D3Heatmap({ data }: { data: SystemicRiskHeatmap[] }) {
  const latest = data[data.length - 1];
  if (!latest) return null;

  return (
    <div className="grid grid-cols-2 gap-3" data-testid="d3-heatmap">
      {AXIS_LABELS.map(({ key, label }) => {
        const value = latest[key];
        return (
          <div
            key={key}
            className="p-3 rounded-md border border-border"
            style={{ borderColor: getQuadrantColor(value) }}
          >
            <p className="text-xs text-muted mb-1">{label}</p>
            <p className="text-lg font-bold" style={{ color: getQuadrantColor(value) }}>
              {value.toFixed(4)}
            </p>
          </div>
        );
      })}
    </div>
  );
}

function PerspectiveHeatmap({ data }: { data: SystemicRiskHeatmap[] }) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    let disposed = false;

    async function init() {
      if (disposed) return;

      const perspective = await import("@finos/perspective");
      await import("@finos/perspective-viewer");
      await import("@finos/perspective-viewer-d3fc");

      if (disposed || !containerRef.current) return;

      const worker = await perspective.worker();
      const table = await worker.table(
        data.map((d) => ({
          timestamp: d.timestamp,
          triPartyGcfSpread: d.triPartyGcfSpread,
          sofrPctlRange: d.sofrPctlRange,
          tgcrBgcrSpread: d.tgcrBgcrSpread,
          tgaBalanceChange: d.tgaBalanceChange,
        })),
      );

      const viewer = document.createElement("perspective-viewer") as HTMLElement & {
        load: (t: unknown) => void;
      };
      viewer.load(table);
      viewer.setAttribute("plugin", "Heatmap");
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

  return <div ref={containerRef} />;
}

export function LiquidityHeatmap({
  data = [],
  perspectiveThreshold = 10000,
  isLoading,
}: LiquidityHeatmapProps) {
  const [usePerspective] = useState(data.length >= perspectiveThreshold);

  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-64 bg-border rounded" />
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">
        Systemic Risk Heatmap
        {usePerspective && (
          <span className="ml-2 text-xs text-muted">(Perspective mode)</span>
        )}
      </h3>
      {data.length === 0 ? (
        <p className="text-sm text-muted text-center py-8">No heatmap data available</p>
      ) : usePerspective ? (
        <PerspectiveHeatmap data={data} />
      ) : (
        <D3Heatmap data={data} />
      )}
    </div>
  );
}
