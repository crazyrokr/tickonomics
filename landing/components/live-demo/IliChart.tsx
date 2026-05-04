"use client";

import { useEffect, useRef } from "react";
import type { IliHistoryPoint } from "@/lib/types";

interface IliChartProps {
  data: IliHistoryPoint[];
}

export default function IliChart({ data }: IliChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<ReturnType<
    typeof import("lightweight-charts").createChart
  > | null>(null);

  useEffect(() => {
    if (!containerRef.current || data.length === 0) return;

    let disposed = false;

    import("lightweight-charts").then(({ createChart, LineSeries }) => {
      if (disposed || !containerRef.current) return;

      if (chartRef.current) {
        chartRef.current.remove();
        chartRef.current = null;
      }

      const chart = createChart(containerRef.current, {
        layout: {
          background: { color: "transparent" },
          textColor: "#71717a",
          fontSize: 12,
        },
        grid: {
          vertLines: { color: "#e4e4e7" },
          horzLines: { color: "#e4e4e7" },
        },
        width: containerRef.current.clientWidth,
        height: 320,
        timeScale: { timeVisible: false },
      });

      chartRef.current = chart;

      const series = chart.addSeries(LineSeries, {
        color: "#3b82f6",
        lineWidth: 2,
      });

      series.setData(
        data.map((p) => ({
          time: p.time,
          value: p.value,
        }))
      );

      chart.timeScale().fitContent();

      const onResize = () => {
        if (containerRef.current) {
          chart.applyOptions({
            width: containerRef.current.clientWidth,
          });
        }
      };

      const observer = new ResizeObserver(onResize);
      observer.observe(containerRef.current);

      return () => {
        observer.disconnect();
      };
    });

    return () => {
      disposed = true;
      if (chartRef.current) {
        chartRef.current.remove();
        chartRef.current = null;
      }
    };
  }, [data]);

  return <div ref={containerRef} className="w-full" />;
}
