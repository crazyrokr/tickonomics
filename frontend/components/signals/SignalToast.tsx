"use client";

import { useEffect, useMemo, useState } from "react";
import type { SignalMarker, SignalStatusCode } from "@/types/api";

interface SignalToastProps {
  signals: SignalMarker[];
  maxVisible?: number;
  autoDismissMs?: number;
}

const STATUS_COLOR: Record<SignalStatusCode, string> = {
  ACTIONABLE: "border-ili-green",
  SPECULATIVE_STALE_MACRO: "border-ili-amber",
  COST_EXCEEDS_EXPECTED_MOVE: "border-muted",
  COOLDOWN: "border-muted",
  INSUFFICIENT_DATA: "border-muted",
};

export function SignalToast({
  signals = [],
  maxVisible = 5,
  autoDismissMs = 5000,
}: SignalToastProps) {
  const visibleSignals = useMemo(
    () => signals.slice(0, maxVisible),
    [signals, maxVisible],
  );

  const [dismissed, setDismissed] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (visibleSignals.length === 0) return;
    const timer = setTimeout(() => {
      setDismissed((prev) => {
        const next = new Set(prev);
        for (const s of visibleSignals) {
          next.add(s.id);
        }
        return next;
      });
    }, autoDismissMs);
    return () => clearTimeout(timer);
  }, [visibleSignals, autoDismissMs]);

  const toasts = visibleSignals.filter((s) => !dismissed.has(s.id));

  if (toasts.length === 0) return null;

  return (
    <div className="fixed top-4 right-4 z-50 flex flex-col gap-2" data-testid="signal-toast-container">
      {toasts.map((signal, i) => {
        const borderColor = STATUS_COLOR[signal.statusCode] ?? "border-muted";
        return (
          <div
            key={signal.id}
            className={`w-72 p-3 rounded-lg border-l-4 ${borderColor} bg-surface shadow-lg transition-all duration-300 ${i === 0 ? "opacity-100 translate-x-0" : "opacity-80 -translate-y-1"}`}
            data-testid={`toast-${signal.id}`}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium px-1.5 py-0.5 rounded bg-surface text-foreground">
                {signal.statusCode}
              </span>
              <span className="text-xs text-muted">{signal.direction}</span>
            </div>
            <p className="text-sm mt-1">
              {signal.symbol} @ ILI {signal.iliValue.toFixed(3)}
            </p>
          </div>
        );
      })}
    </div>
  );
}
