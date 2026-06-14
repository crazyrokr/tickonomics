"use client";

import {
  useDemoPortfolio,
  useDemoPositions,
  useDemoTrades,
  useDemoSignalQuality,
  useKillSwitchStatus,
} from "@/hooks/useDemoData";
import { DisclaimerBanner } from "@/components/demo/DisclaimerBanner";
import { LiveIndicator } from "@/components/demo/LiveIndicator";
import { DemoBadge } from "@/components/demo/DemoBadge";
import { PortfolioSummary } from "@/components/demo/PortfolioSummary";
import { TradeHistory } from "@/components/demo/TradeHistory";
import { SignalQualityMetrics } from "@/components/demo/SignalQualityMetrics";

export default function DemoPage() {
  const portfolio = useDemoPortfolio();
  const positions = useDemoPositions();
  const trades = useDemoTrades(50, 0);
  const signalQuality = useDemoSignalQuality();
  const killSwitch = useKillSwitchStatus();

  const live = !portfolio.isLoading;

  return (
    <main className="mx-auto max-w-6xl space-y-6 p-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">Demo Virtual Portfolio</h1>
          <p className="text-sm text-slate-400">
            Signal quality verification against live market data.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <LiveIndicator live={live} />
          <DemoBadge variant="virtual" />
          {killSwitch.data?.active === true && (
            <DemoBadge variant="dislocated" />
          )}
        </div>
      </header>

      <DisclaimerBanner />

      {killSwitch.data?.active === true && (
        <div className="rounded-md border border-rose-500/40 bg-rose-500/10 px-4 py-2 text-sm text-rose-200">
          Kill-switch active: new trades are blocked and open positions are being liquidated.
        </div>
      )}

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">Portfolio Summary</h2>
        <PortfolioSummary portfolio={portfolio.data} isLoading={portfolio.isLoading} />
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">Signal Quality</h2>
        <SignalQualityMetrics
          report={signalQuality.data}
          isLoading={signalQuality.isLoading}
        />
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">
          Trade History ({positions.data?.length ?? 0} open positions)
        </h2>
        <TradeHistory trades={trades.data ?? []} isLoading={trades.isLoading} />
      </section>
    </main>
  );
}
