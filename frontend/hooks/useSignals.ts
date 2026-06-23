"use client";

import { useQuery } from "@tanstack/react-query";
import { getSignals } from "@/lib/api";
import type { SignalMarker } from "@/types/api";
import { useWebSocket } from "./useWebSocket";
import { useMemo } from "react";
import { signalFrameSchema } from "@/lib/ws-schemas";

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

    const parsed = signalFrameSchema.safeParse(lastMessage);
    if (parsed.success) {
      return [parsed.data.signal, ...base];
    }
    // Malformed frame: discard rather than cast blindly.
    return base;
  }, [query.data, lastMessage]);

  return {
    signals,
    isLoading: query.isLoading,
    error: query.error,
  };
}
