"use client";

import { useQuery } from "@tanstack/react-query";
import {
  getDemoPortfolio,
  getDemoPositions,
  getDemoTrades,
  getDemoSignalQuality,
  getKillSwitchStatus,
} from "@/lib/api";

const DEMO_REFETCH_INTERVAL_MS = 30_000;

export function useDemoPortfolio() {
  return useQuery({
    queryKey: ["demo", "portfolio"],
    queryFn: getDemoPortfolio,
    refetchInterval: DEMO_REFETCH_INTERVAL_MS,
    staleTime: 10_000,
  });
}

export function useDemoPositions() {
  return useQuery({
    queryKey: ["demo", "positions"],
    queryFn: getDemoPositions,
    refetchInterval: DEMO_REFETCH_INTERVAL_MS,
    staleTime: 10_000,
  });
}

export function useDemoTrades(limit = 50, offset = 0) {
  return useQuery({
    queryKey: ["demo", "trades", limit, offset],
    queryFn: () => getDemoTrades(limit, offset),
    refetchInterval: DEMO_REFETCH_INTERVAL_MS,
    staleTime: 10_000,
  });
}

export function useDemoSignalQuality() {
  return useQuery({
    queryKey: ["demo", "signal-quality"],
    queryFn: getDemoSignalQuality,
    refetchInterval: 60_000,
    staleTime: 30_000,
  });
}

export function useKillSwitchStatus() {
  return useQuery({
    queryKey: ["demo", "kill-switch-status"],
    queryFn: getKillSwitchStatus,
    refetchInterval: DEMO_REFETCH_INTERVAL_MS,
    staleTime: 10_000,
  });
}
