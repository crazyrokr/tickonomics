"use client";

import type { DashboardData } from "@/hooks/useDashboardData";
import { RepoEquityBetaTable } from "@/components/kpi/RepoEquityBetaTable";
import { RrpDrainTrend } from "@/components/kpi/RrpDrainTrend";
import { AnomalyScoreIndicator } from "@/components/kpi/AnomalyScoreIndicator";
import { LiquidityReliabilityScore } from "@/components/kpi/LiquidityReliabilityScore";
import { MarketEfficiencyGapIndicator } from "@/components/panels/MarketEfficiencyGapIndicator";
import { PerformanceDualityPanel } from "@/components/panels/PerformanceDualityPanel";
import { LeverageRegimeIndicator } from "@/components/panels/LeverageRegimeIndicator";
import {
  emptyAnomalyScore,
  emptyLiquidityReliability,
  emptyMarketEfficiencyGap,
  emptyPerformanceDuality,
  emptyLeverageRegime,
} from "@/lib/dashboard/empty-state";

export function KpiSection({ data }: { data: DashboardData }) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <RepoEquityBetaTable
        data={data.repoEquityBeta.data}
        isLoading={data.repoEquityBeta.isLoading}
      />
      <RrpDrainTrend data={data.rrpDrain.data} isLoading={data.rrpDrain.isLoading} />
      <AnomalyScoreIndicator {...emptyAnomalyScore} />
      <LiquidityReliabilityScore {...emptyLiquidityReliability} />
      <MarketEfficiencyGapIndicator {...emptyMarketEfficiencyGap} />
      <PerformanceDualityPanel {...emptyPerformanceDuality} />
      <LeverageRegimeIndicator {...emptyLeverageRegime} />
    </div>
  );
}
