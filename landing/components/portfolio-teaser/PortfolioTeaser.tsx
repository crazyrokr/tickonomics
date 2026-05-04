"use client";

import Section from "@/components/shared/Section";
import { useApiData } from "@/hooks/useApiData";
import type { DemoPortfolio } from "@/lib/types";

export default function PortfolioTeaser() {
  const { data: portfolio, isLoading } = useApiData<DemoPortfolio>(
    "/api/v1/demo/portfolio"
  );

  const display = portfolio ?? { pnl: 0, win_rate: 0, signal_count: 0, period_days: 90 };

  return (
    <Section id="portfolio" className="mx-auto max-w-5xl px-6 py-20">
      <h2 className="text-center text-3xl font-bold tracking-tight text-zinc-900 sm:text-4xl">
        Demo Portfolio
      </h2>
      <p className="mx-auto mt-4 max-w-xl text-center text-zinc-600">
        Track performance of our demo virtual portfolio running on live signals.
      </p>

      <div className="mx-auto mt-10 max-w-lg rounded-xl border border-zinc-200 bg-white p-6 shadow-sm">
        {isLoading ? (
          <div className="flex h-32 items-center justify-center text-zinc-400">
            Loading portfolio...
          </div>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-6">
              <div>
                <p className="text-sm text-zinc-500">P&amp;L ({display.period_days}d)</p>
                <p
                  className={`text-2xl font-bold ${
                    display.pnl >= 0 ? "text-emerald-600" : "text-red-600"
                  }`}
                >
                  {display.pnl >= 0 ? "+" : ""}
                  {display.pnl.toFixed(1)}%
                </p>
              </div>
              <div>
                <p className="text-sm text-zinc-500">Win Rate</p>
                <p className="text-2xl font-bold text-zinc-900">
                  {(display.win_rate * 100).toFixed(0)}%
                </p>
              </div>
              <div>
                <p className="text-sm text-zinc-500">Signals</p>
                <p className="text-2xl font-bold text-zinc-900">
                  {display.signal_count}
                </p>
              </div>
              <div>
                <p className="text-sm text-zinc-500">Status</p>
                <span className="inline-flex items-center rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800">
                  Coming Soon
                </span>
              </div>
            </div>

            <p className="mt-6 text-center text-xs text-zinc-400">
              Simulated performance for demonstration purposes. Past signals do not guarantee future results.
            </p>
          </>
        )}
      </div>
    </Section>
  );
}
