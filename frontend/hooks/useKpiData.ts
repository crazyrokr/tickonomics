"use client";

import { useQuery } from "@tanstack/react-query";
import {
  getIli,
  getIliHistory,
  getLiquidityStress,
  getRepoEquityBeta,
  getRrpDrain,
  getVolatilityRegime,
  getCorrelationMatrix,
  getSystemicRiskHeatmap,
} from "@/lib/api";

const KPI_REFETCH_INTERVAL_MS = 30_000;
const KPI_HEAVY_REFETCH_INTERVAL_MS = 60_000;

export function useIli() {
  return useQuery({
    queryKey: ["kpi", "ili"],
    queryFn: getIli,
    refetchInterval: KPI_REFETCH_INTERVAL_MS,
    staleTime: 10_000,
  });
}

export function useLiquidityStress() {
  return useQuery({
    queryKey: ["kpi", "liquidity-stress"],
    queryFn: getLiquidityStress,
    refetchInterval: KPI_REFETCH_INTERVAL_MS,
    staleTime: 10_000,
  });
}

export function useRepoEquityBeta() {
  return useQuery({
    queryKey: ["kpi", "repo-equity-beta"],
    queryFn: getRepoEquityBeta,
    refetchInterval: KPI_REFETCH_INTERVAL_MS,
    staleTime: 10_000,
  });
}

export function useRrpDrain() {
  return useQuery({
    queryKey: ["kpi", "rrp-drain"],
    queryFn: getRrpDrain,
    refetchInterval: KPI_REFETCH_INTERVAL_MS,
    staleTime: 10_000,
  });
}

export function useVolatilityRegime() {
  return useQuery({
    queryKey: ["kpi", "volatility-regime"],
    queryFn: getVolatilityRegime,
    refetchInterval: KPI_REFETCH_INTERVAL_MS,
    staleTime: 10_000,
  });
}

export function useIliHistory() {
  return useQuery({
    queryKey: ["kpi", "ili-history"],
    queryFn: getIliHistory,
    refetchInterval: KPI_HEAVY_REFETCH_INTERVAL_MS,
    staleTime: 30_000,
  });
}

export function useCorrelationMatrix() {
  return useQuery({
    queryKey: ["kpi", "correlation-matrix"],
    queryFn: getCorrelationMatrix,
    refetchInterval: KPI_HEAVY_REFETCH_INTERVAL_MS,
    staleTime: 30_000,
  });
}

export function useSystemicRiskHeatmap() {
  return useQuery({
    queryKey: ["kpi", "systemic-risk-heatmap"],
    queryFn: getSystemicRiskHeatmap,
    refetchInterval: KPI_HEAVY_REFETCH_INTERVAL_MS,
    staleTime: 30_000,
  });
}
