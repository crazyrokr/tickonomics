"use client";

import { useEffect, useRef } from "react";

interface SparklineProps {
  data: number[];
  width?: number;
  height?: number;
  color?: string;
  threshold?: number;
  thresholdColor?: string;
}

export function Sparkline({
  data,
  width = 120,
  height = 32,
  color = "var(--color-ili-blue)",
  threshold,
  thresholdColor = "var(--color-ili-amber)",
}: SparklineProps) {
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    const svg = svgRef.current;
    if (!svg || data.length < 2) return;

    const min = Math.min(...data);
    const max = Math.max(...data);
    const range = max - min || 1;
    const padding = 2;

    const points = data
      .map((val, i) => {
        const x = padding + (i / (data.length - 1)) * (width - 2 * padding);
        const y = height - padding - ((val - min) / range) * (height - 2 * padding);
        return `${x},${y}`;
      })
      .join(" ");

    svg.innerHTML = "";

    if (threshold !== undefined) {
      const thresholdY =
        height - padding - ((threshold - min) / range) * (height - 2 * padding);
      const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
      line.setAttribute("x1", String(padding));
      line.setAttribute("x2", String(width - padding));
      line.setAttribute("y1", String(thresholdY));
      line.setAttribute("y2", String(thresholdY));
      line.setAttribute("stroke", thresholdColor);
      line.setAttribute("stroke-dasharray", "3,2");
      line.setAttribute("stroke-width", "1");
      svg.appendChild(line);
    }

    const polyline = document.createElementNS("http://www.w3.org/2000/svg", "polyline");
    polyline.setAttribute("points", points);
    polyline.setAttribute("fill", "none");
    polyline.setAttribute("stroke", color);
    polyline.setAttribute("stroke-width", "1.5");
    polyline.setAttribute("stroke-linejoin", "round");
    svg.appendChild(polyline);
  }, [data, width, height, color, threshold, thresholdColor]);

  if (data.length < 2) {
    return (
      <svg width={width} height={height} className="opacity-30">
        <line
          x1="0"
          y1={height / 2}
          x2={width}
          y2={height / 2}
          stroke="var(--color-muted)"
          strokeWidth="1"
          strokeDasharray="3,2"
        />
      </svg>
    );
  }

  return <svg ref={svgRef} width={width} height={height} />;
}
