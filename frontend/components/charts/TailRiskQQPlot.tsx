"use client";

import { useRef, useEffect } from "react";

interface QQPoint {
  quantile: number;
  theoretical: number;
  observed: number;
}

interface TailRiskQQPlotProps {
  data: QQPoint[];
  heavyTailRegime: boolean;
  isLoading?: boolean;
}

export function TailRiskQQPlot({ data, heavyTailRegime, isLoading }: TailRiskQQPlotProps) {
  const svgRef = useRef<SVGSVGElement>(null);

  const width = 400;
  const height = 300;
  const padding = 30;

  useEffect(() => {
    const svg = svgRef.current;
    if (!svg || data.length < 2) return;

    const allVals = data.flatMap((d) => [d.theoretical, d.observed]);
    const minVal = Math.min(...allVals);
    const maxVal = Math.max(...allVals);
    const range = maxVal - minVal || 1;

    const toX = (v: number) => padding + ((v - minVal) / range) * (width - 2 * padding);
    const toY = (v: number) => height - padding - ((v - minVal) / range) * (height - 2 * padding);

    svg.innerHTML = "";

    const refLine = document.createElementNS("http://www.w3.org/2000/svg", "line");
    refLine.setAttribute("x1", String(toX(minVal)));
    refLine.setAttribute("x2", String(toX(maxVal)));
    refLine.setAttribute("y1", String(toY(minVal)));
    refLine.setAttribute("y2", String(toY(maxVal)));
    refLine.setAttribute("stroke", "var(--color-muted)");
    refLine.setAttribute("stroke-width", "1");
    refLine.setAttribute("stroke-dasharray", "4,4");
    svg.appendChild(refLine);

    for (const point of data) {
      const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
      circle.setAttribute("cx", String(toX(point.theoretical)));
      circle.setAttribute("cy", String(toY(point.observed)));
      circle.setAttribute("r", "3");
      circle.setAttribute("fill", heavyTailRegime ? "var(--color-ili-red)" : "var(--color-ili-blue)");
      circle.setAttribute("opacity", "0.7");
      circle.setAttribute(
        "title",
        `Q: ${point.quantile.toFixed(2)}, Theoretical: ${point.theoretical.toFixed(3)}, Observed: ${point.observed.toFixed(3)}`
      );
      svg.appendChild(circle);
    }
  }, [data, heavyTailRegime]);

  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-64 bg-border rounded" />
      </div>
    );
  }

  if (data.length < 2) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <h3 className="text-sm font-medium text-muted mb-3">Tail Risk Q-Q Plot</h3>
        <p className="text-sm text-muted text-center py-8">No quantile data available</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-medium text-muted">Tail Risk Q-Q Plot</h3>
        <div className="flex items-center gap-2">
          {heavyTailRegime && (
            <span className="px-2 py-0.5 text-xs rounded-full font-medium bg-ili-red/15 text-ili-red" data-testid="heavy-tail-badge">
              Heavy Tails
            </span>
          )}
          <button
            className="px-2 py-1 text-xs border border-border rounded text-muted hover:bg-border/50 transition-colors"
            data-testid="export-button"
            onClick={() => {}}
          >
            Export
          </button>
        </div>
      </div>

      <svg ref={svgRef} width={width} height={height} className="w-full" data-testid="qq-plot-svg" />

      <div className="flex items-center gap-4 mt-2 text-xs text-muted">
        <span>X: Theoretical Quantiles</span>
        <span>Y: Observed Quantiles</span>
        <span className="text-muted/60">Dashed line: 45-degree reference</span>
      </div>
    </div>
  );
}
