"use client";

import Section from "@/components/shared/Section";
import SignalStatusBadge from "./SignalStatusBadge";
import { useApiData } from "@/hooks/useApiData";
import type { AlphaSignal } from "@/lib/types";

export default function SignalShowcase() {
  const { data: signals, error, isLoading } = useApiData<AlphaSignal[]>(
    "/api/v1/quant/signals/active"
  );

  const displaySignals = signals ?? [];

  return (
    <Section
      id="signals"
      className="mx-auto max-w-5xl px-6 py-20 bg-zinc-50"
    >
      <h2 className="text-center text-3xl font-bold tracking-tight text-zinc-900 sm:text-4xl">
        Recent Signals
      </h2>
      <p className="mx-auto mt-4 max-w-2xl text-center text-zinc-600">
        Latest alpha signals with status, direction, and confidence scores.
      </p>

      <div className="mt-10 overflow-x-auto rounded-xl border border-zinc-200 bg-white shadow-sm">
        {isLoading && (
          <div className="flex h-32 items-center justify-center text-zinc-400">
            Loading signals...
          </div>
        )}

        {error && (
          <div className="flex h-32 items-center justify-center text-zinc-400">
            Showing demo data (API unavailable)
          </div>
        )}

        {!isLoading && displaySignals.length === 0 && (
          <div className="flex h-32 items-center justify-center text-zinc-400">
            No signals available
          </div>
        )}

        {!isLoading && displaySignals.length > 0 && (
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-zinc-100 bg-zinc-50">
                <th className="px-4 py-3 font-medium text-zinc-600">Symbol</th>
                <th className="px-4 py-3 font-medium text-zinc-600">Direction</th>
                <th className="px-4 py-3 font-medium text-zinc-600">Strength</th>
                <th className="px-4 py-3 font-medium text-zinc-600">Confidence</th>
                <th className="px-4 py-3 font-medium text-zinc-600">Status</th>
              </tr>
            </thead>
            <tbody>
              {displaySignals.map((signal, i) => (
                <tr key={`${signal.strategyId}-${i}`} className="border-b border-zinc-50">
                  <td className="px-4 py-3 font-medium text-zinc-900">
                    {signal.symbol}
                  </td>
                  <td className="px-4 py-3 text-zinc-700">{signal.direction}</td>
                  <td className="px-4 py-3 text-zinc-700">
                    {(signal.strength * 100).toFixed(0)}%
                  </td>
                  <td className="px-4 py-3 text-zinc-700">
                    {(signal.confidence * 100).toFixed(0)}%
                  </td>
                  <td className="px-4 py-3">
                    {signal.status && (
                      <SignalStatusBadge status={signal.status} />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </Section>
  );
}
