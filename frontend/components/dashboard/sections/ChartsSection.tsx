"use client";

import type { DashboardData } from "@/hooks/useDashboardData";
import { CorrelationMatrix } from "@/components/charts/CorrelationMatrix";
import { LiquidityHeatmap } from "@/components/charts/LiquidityHeatmap";
import { BacktestHeatmap } from "@/components/charts/BacktestHeatmap";
import { ConvergencePlot } from "@/components/charts/ConvergencePlot";
import { GammaProfileChart } from "@/components/charts/GammaProfileChart";
import { PotentialWellChart } from "@/components/charts/PotentialWellChart";
import { SentimentHeatmap } from "@/components/charts/SentimentHeatmap";
import { TailRiskQQPlot } from "@/components/charts/TailRiskQQPlot";
import { ToxicityHeatmap } from "@/components/charts/ToxicityHeatmap";
import { VolatilityClusterChart } from "@/components/charts/VolatilityClusterChart";
import { VolatilityPersistenceACF } from "@/components/charts/VolatilityPersistenceACF";
import {
  emptyBacktestHeatmap,
  emptyConvergencePlot,
  emptyGammaProfile,
  emptyPotentialWell,
  emptySentimentHeatmap,
  emptyTailRiskQQPlot,
  emptyToxicityHeatmap,
  emptyVolatilityCluster,
  emptyVolatilityPersistenceACF,
} from "@/lib/dashboard/empty-state";

export function ChartsSection({ data }: { data: DashboardData }) {
  const heatmap = data.systemicRiskHeatmap.data
    ? [data.systemicRiskHeatmap.data]
    : [];

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <CorrelationMatrix
        data={data.correlationMatrix.data ?? []}
        isLoading={data.correlationMatrix.isLoading}
      />
      <LiquidityHeatmap
        data={heatmap}
        isLoading={data.systemicRiskHeatmap.isLoading}
      />
      <BacktestHeatmap {...emptyBacktestHeatmap} />
      <ConvergencePlot {...emptyConvergencePlot} />
      <GammaProfileChart {...emptyGammaProfile} />
      <PotentialWellChart {...emptyPotentialWell} />
      <SentimentHeatmap {...emptySentimentHeatmap} />
      <TailRiskQQPlot {...emptyTailRiskQQPlot} />
      <ToxicityHeatmap {...emptyToxicityHeatmap} />
      <VolatilityClusterChart {...emptyVolatilityCluster} />
      <VolatilityPersistenceACF {...emptyVolatilityPersistenceACF} />
    </div>
  );
}
