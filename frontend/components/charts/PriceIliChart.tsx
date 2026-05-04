"use client";

import { useEffect, useRef, useCallback } from "react";
import type {
  IChartApi,
  ISeriesApi,
  SeriesType,
  Time,
} from "lightweight-charts";
import type { IliHistoryPoint, SignalMarker, SignalStatusCode } from "@/types/api";

interface PriceIliChartProps {
  priceData: Array<{ time: string; open: number; high: number; low: number; close: number }>;
  iliData: IliHistoryPoint[];
  signals?: SignalMarker[];
  height?: number;
}

const SIGNAL_MARKER_COLORS: Record<SignalStatusCode, string> = {
  ACTIONABLE: "#22c55e",
  SPECULATIVE_STALE_MACRO: "#f59e0b",
  COST_EXCEEDS_EXPECTED_MOVE: "#71717a",
  COOLDOWN: "#a1a1aa",
  INSUFFICIENT_DATA: "#d4d4d8",
};

const ILI_STATUS_COLORS: Record<string, string> = {
  VALID: "#22c55e",
  DEGRADED_COMPONENT_STALE: "#f59e0b",
  DISLOCATED: "#ef4444",
};

interface ChartRefs {
  chart: IChartApi;
  priceSeries: ISeriesApi<SeriesType, Time>;
  iliSeries: ISeriesApi<SeriesType, Time>;
  priceMarkers: ReturnType<typeof import("lightweight-charts").createSeriesMarkers<Time>> | null;
  iliMarkers: ReturnType<typeof import("lightweight-charts").createSeriesMarkers<Time>> | null;
}

export function PriceIliChart({
  priceData,
  iliData,
  signals = [],
  height = 500,
}: PriceIliChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<ChartRefs | null>(null);

  const getOrCreateChart = useCallback(async () => {
    if (!containerRef.current) return null;
    if (chartRef.current) return chartRef.current;

    const {
      createChart,
      CandlestickSeries,
      LineSeries,
    } = await import("lightweight-charts");

    const chart = createChart(containerRef.current, {
      height,
      layout: {
        background: { color: "transparent" },
        textColor: "var(--color-muted)",
      },
      grid: {
        vertLines: { color: "var(--color-border)" },
        horzLines: { color: "var(--color-border)" },
      },
      timeScale: { timeVisible: true },
    });

    const priceSeries = chart.addSeries(CandlestickSeries, {
      upColor: "#22c55e",
      downColor: "#ef4444",
      borderDownColor: "#ef4444",
      borderUpColor: "#22c55e",
      wickDownColor: "#ef4444",
      wickUpColor: "#22c55e",
    });

    const iliSeries = chart.addSeries(LineSeries, {
      priceScaleId: "ili",
      lineWidth: 2,
    });

    chart.priceScale("ili").applyOptions({
      scaleMargins: { top: 0.6, bottom: 0 },
    });

    const refs: ChartRefs = {
      chart,
      priceSeries,
      iliSeries,
      priceMarkers: null,
      iliMarkers: null,
    };
    chartRef.current = refs;
    return refs;
  }, [height]);

  useEffect(() => {
    getOrCreateChart();

    return () => {
      if (chartRef.current) {
        chartRef.current.chart.remove();
        chartRef.current = null;
      }
    };
  }, [getOrCreateChart]);

  useEffect(() => {
    if (!chartRef.current) return;
    chartRef.current.priceSeries.setData(
      priceData.map((d) => ({
        time: d.time as Time,
        open: d.open,
        high: d.high,
        low: d.low,
        close: d.close,
      })),
    );
  }, [priceData]);

  useEffect(() => {
    if (!chartRef.current) return;
    chartRef.current.iliSeries.setData(
      iliData.map((d) => ({
        time: d.timestamp as Time,
        value: d.value,
        color: ILI_STATUS_COLORS[d.status] ?? ILI_STATUS_COLORS.VALID,
      })),
    );
  }, [iliData]);

  useEffect(() => {
    if (!chartRef.current || signals.length === 0) return;
    const { priceSeries } = chartRef.current;

    import("lightweight-charts").then(({ createSeriesMarkers }) => {
      if (!chartRef.current) return;

      const markers = signals.map((s) => ({
        time: s.timestamp as Time,
        position: s.direction === "LONG" ? ("belowBar" as const) : ("aboveBar" as const),
        color: SIGNAL_MARKER_COLORS[s.statusCode] ?? "#71717a",
        shape: "arrowUp" as const,
        text: s.statusCode,
      }));

      if (chartRef.current.priceMarkers) {
        chartRef.current.priceMarkers.setMarkers(markers);
      } else {
        chartRef.current.priceMarkers = createSeriesMarkers(
          priceSeries,
          markers,
        );
      }
    });
  }, [signals]);

  return (
    <div ref={containerRef} data-testid="price-ili-chart" className="w-full" />
  );
}

export { SIGNAL_MARKER_COLORS, ILI_STATUS_COLORS };
