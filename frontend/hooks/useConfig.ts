"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getConfig, getConfigHistory, updateConfig } from "@/lib/api";
import type { ConfigEntry } from "@/types/api";

const CONFIG_STALE_TIME_MS = 60_000;

export function useConfig(enabled = true) {
  return useQuery({
    queryKey: ["config"],
    queryFn: getConfig,
    staleTime: CONFIG_STALE_TIME_MS,
    enabled,
  });
}

export function useConfigHistory(enabled = true) {
  return useQuery({
    queryKey: ["config", "history"],
    queryFn: getConfigHistory,
    staleTime: CONFIG_STALE_TIME_MS,
    enabled,
  });
}

export function useUpdateConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (entries: ConfigEntry[]) => updateConfig(entries),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["config"] });
      queryClient.invalidateQueries({ queryKey: ["config", "history"] });
    },
  });
}
