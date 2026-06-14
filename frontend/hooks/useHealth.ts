"use client";

import { useQuery } from "@tanstack/react-query";
import { getHealth } from "@/lib/api";

const HEALTH_REFETCH_INTERVAL_MS = 15_000;

export function useHealth() {
  return useQuery({
    queryKey: ["health"],
    queryFn: getHealth,
    refetchInterval: HEALTH_REFETCH_INTERVAL_MS,
    staleTime: 5_000,
  });
}
