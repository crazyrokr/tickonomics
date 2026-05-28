"use client";

import { useEffect, useRef } from "react";
import * as d3 from "d3";

interface HeatmapCell {
  row: string;
  col: string;
  value: number;
}

interface D3IliHeatmapProps {
  data: HeatmapCell[];
  rowLabels: string[];
  colLabels: string[];
  width?: number;
  height?: number;
  title?: string;
  colorScale?: [string, string, string];
  isLoading?: boolean;
}

export function D3IliHeatmap({
  data,
  rowLabels,
  colLabels,
  width = 480,
  height = 320,
  title,
  colorScale = ["var(--color-ili-green)", "var(--color-ili-amber)", "var(--color-ili-red)"],
  isLoading = false,
}: D3IliHeatmapProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const svg = svgRef.current;
    if (!svg || isLoading || data.length === 0) return;

    const margin = { top: 40, right: 20, bottom: 60, left: 80 };
    const innerWidth = width - margin.left - margin.right;
    const innerHeight = height - margin.top - margin.bottom;

    d3.select(svg).selectAll("*").remove();

    const root = d3.select(svg).attr("width", width).attr("height", height);

    const g = root
      .append("g")
      .attr("transform", `translate(${margin.left},${margin.top})`);

    const xScale = d3.scaleBand().domain(colLabels).range([0, innerWidth]).padding(0.05);

    const yScale = d3.scaleBand().domain(rowLabels).range([0, innerHeight]).padding(0.05);

    const values = data.map((d) => d.value);
    const minVal = Math.min(...values);
    const maxVal = Math.max(...values);

    const interpolateColor = d3
      .scaleLinear<string>()
      .domain([minVal, (minVal + maxVal) / 2, maxVal])
      .range(colorScale)
      .clamp(true);

    if (title) {
      root
        .append("text")
        .attr("x", width / 2)
        .attr("y", 16)
        .attr("text-anchor", "middle")
        .attr("fill", "var(--color-muted)")
        .attr("font-size", "12px")
        .text(title);
    }

    g.selectAll("rect")
      .data(data)
      .join("rect")
      .attr("x", (d) => xScale(d.col) ?? 0)
      .attr("y", (d) => yScale(d.row) ?? 0)
      .attr("width", xScale.bandwidth())
      .attr("height", yScale.bandwidth())
      .attr("rx", 2)
      .attr("fill", (d) => interpolateColor(d.value))
      .attr("opacity", 0.9)
      .append("title")
      .text((d) => `${d.row} × ${d.col}: ${d.value.toFixed(2)}`);

    g.selectAll(".value-label")
      .data(data)
      .join("text")
      .attr("class", "value-label")
      .attr("x", (d) => (xScale(d.col) ?? 0) + xScale.bandwidth() / 2)
      .attr("y", (d) => (yScale(d.row) ?? 0) + yScale.bandwidth() / 2)
      .attr("text-anchor", "middle")
      .attr("dominant-baseline", "central")
      .attr("fill", (d) => (d.value > (minVal + maxVal) / 2 ? "white" : "black"))
      .attr("font-size", "10px")
      .text((d) => d.value.toFixed(1));

    g.append("g")
      .attr("transform", `translate(0,${innerHeight})`)
      .call(d3.axisBottom(xScale).tickSize(0))
      .selectAll("text")
      .attr("transform", "rotate(-30)")
      .attr("text-anchor", "end")
      .attr("fill", "var(--color-muted)")
      .attr("font-size", "10px");

    g.append("g")
      .call(d3.axisLeft(yScale).tickSize(0))
      .selectAll("text")
      .attr("fill", "var(--color-muted)")
      .attr("font-size", "10px");

    g.selectAll(".domain").attr("stroke", "var(--color-border)");
    g.selectAll(".tick line").attr("stroke", "var(--color-border)");
  }, [data, rowLabels, colLabels, width, height, title, colorScale, isLoading]);

  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <div className="animate-pulse space-y-3">
          <div className="h-4 bg-border rounded w-1/3" />
          <div className="h-64 bg-border rounded" />
        </div>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div
        ref={containerRef}
        className="rounded-lg border border-border p-4 bg-surface"
        data-testid="d3-ili-heatmap"
      >
        <p className="text-sm text-muted text-center">No heatmap data available</p>
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      className="rounded-lg border border-border p-4 bg-surface"
      data-testid="d3-ili-heatmap"
    >
      <svg ref={svgRef} />
    </div>
  );
}
