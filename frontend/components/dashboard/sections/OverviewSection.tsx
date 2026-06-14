"use client";

import type { DashboardData } from "@/hooks/useDashboardData";
import { IliCard } from "@/components/kpi/IliCard";
import { LiquidityStressGauge } from "@/components/kpi/LiquidityStressGauge";
import { VolatilityRegimeIndicator } from "@/components/kpi/VolatilityRegimeIndicator";
import { DataFreshnessPanel } from "@/components/panels/DataFreshnessPanel";
import { SystemHealthPanel } from "@/components/panels/SystemHealthPanel";
import { D3IliHeatmap } from "@/components/charts/D3IliHeatmap";
import { PriceIliChart } from "@/components/charts/PriceIliChart";
import { SignalLog } from "@/components/signals/SignalLog";
import { emptyD3IliHeatmap } from "@/lib/dashboard/empty-state";

export function OverviewSection({ data }: { data: DashboardData }) {
  const iliHistory = Array.isArray(data.iliHistory.data) ? data.iliHistory.data : [];

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <IliCard
          data={data.ili.data}
          history={iliHistory.map((p) => p.value)}
          isLoading={data.ili.isLoading}
        />
        <LiquidityStressGauge
          data={data.liquidityStress.data}
          isLoading={data.liquidityStress.isLoading}
        />
        <VolatilityRegimeIndicator
          data={data.volatilityRegime.data}
          isLoading={data.volatilityRegime.isLoading}
        />
      </div>

      <PriceIliChart
        priceData={[]}
        iliData={iliHistory}
        signals={data.signals.signals}
      />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <SignalLog
          signals={data.signals.signals}
          isLoading={data.signals.isLoading}
        />
        <D3IliHeatmap {...emptyD3IliHeatmap} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <DataFreshnessPanel
          data={data.health.data}
          isLoading={data.health.isLoading}
        />
        <SystemHealthPanel
          data={data.health.data}
          isLoading={data.health.isLoading}
        />
      </div>
    </div>
  );
}
