"use client";

import { useQuery } from "@tanstack/react-query";
import { getSignals } from "@/lib/api";
import type { SignalMarker, SignalUpdate } from "@/types/api";
import { useWebSocket } from "./useWebSocket";
import { useMemo } from "react";

export function useSignals(token?: string) {
  const query = useQuery({
    queryKey: ["signals"],
    queryFn: getSignals,
    refetchInterval: 60_000,
    staleTime: 30_000,
    initialData: [],
  });

  const { lastMessage } = useWebSocket("/ws/signals", token);

  const signals = useMemo<SignalMarker[]>(() => {
    const base = query.data ?? [];
    if (!lastMessage) return base;

    const update = lastMessage as SignalUpdate;
    if (update?.signal) {
      return [update.signal, ...base];
    }
    return base;
  }, [query.data, lastMessage]);

  return {
    signals,
    isLoading: query.isLoading,
    error: query.error,
  };
}
