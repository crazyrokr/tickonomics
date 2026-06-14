import { describe, it, expect } from "vitest";
import { render } from "@testing-library/react";
import type { ComponentType } from "react";

import { BacktestHeatmap } from "@/components/charts/BacktestHeatmap";
import { D3IliHeatmap } from "@/components/charts/D3IliHeatmap";
import { GammaProfileChart } from "@/components/charts/GammaProfileChart";
import { PotentialWellChart } from "@/components/charts/PotentialWellChart";
import { SentimentHeatmap } from "@/components/charts/SentimentHeatmap";
import { TailRiskQQPlot } from "@/components/charts/TailRiskQQPlot";
import { ToxicityHeatmap } from "@/components/charts/ToxicityHeatmap";
import { VolatilityClusterChart } from "@/components/charts/VolatilityClusterChart";
import { VolatilityPersistenceACF } from "@/components/charts/VolatilityPersistenceACF";
import { ConvergencePlot } from "@/components/charts/ConvergencePlot";
import { AnomalyScoreIndicator } from "@/components/kpi/AnomalyScoreIndicator";
import { LiquidityReliabilityScore } from "@/components/kpi/LiquidityReliabilityScore";
import { DynamicStopsPanel } from "@/components/panels/DynamicStopsPanel";
import { ExecutionComparisonPanel } from "@/components/panels/ExecutionComparisonPanel";
import { GreeksSensitivityDashboard } from "@/components/panels/GreeksSensitivityDashboard";
import { LeverageRegimeIndicator } from "@/components/panels/LeverageRegimeIndicator";
import { MacroEnvironmentPanel } from "@/components/panels/MacroEnvironmentPanel";
import { MarketEfficiencyGapIndicator } from "@/components/panels/MarketEfficiencyGapIndicator";
import { ModelTournamentPanel } from "@/components/panels/ModelTournamentPanel";
import { MonetaryPolicyPanel } from "@/components/panels/MonetaryPolicyPanel";
import { OptimizerStatusPanel } from "@/components/panels/OptimizerStatusPanel";
import { PairsVerificationPanel } from "@/components/panels/PairsVerificationPanel";
import { ParticipationPanel } from "@/components/panels/ParticipationPanel";
import { PerformanceDecompositionPanel } from "@/components/panels/PerformanceDecompositionPanel";
import { PerformanceDualityPanel } from "@/components/panels/PerformanceDualityPanel";
import { RegimeComparisonPanel } from "@/components/panels/RegimeComparisonPanel";
import { SignalExplainabilityPanel } from "@/components/panels/SignalExplainabilityPanel";
import { SignalInformativenessPanel } from "@/components/panels/SignalInformativenessPanel";
import { TraderTypePanel } from "@/components/panels/TraderTypePanel";

import {
  emptyBacktestHeatmap,
  emptyD3IliHeatmap,
  emptyGammaProfile,
  emptyPotentialWell,
  emptySentimentHeatmap,
  emptyTailRiskQQPlot,
  emptyToxicityHeatmap,
  emptyVolatilityCluster,
  emptyVolatilityPersistenceACF,
  emptyConvergencePlot,
  emptyAnomalyScore,
  emptyLiquidityReliability,
  emptyDynamicStops,
  emptyExecutionComparison,
  emptyGreeks,
  emptyLeverageRegime,
  emptyMacroEnvironment,
  emptyMarketEfficiencyGap,
  emptyModelTournament,
  emptyMonetaryPolicy,
  emptyOptimizerStatus,
  emptyPairsVerification,
  emptyParticipation,
  emptyPerformanceDecomposition,
  emptyPerformanceDuality,
  emptyRegimeComparison,
  emptySignalExplainability,
  emptySignalInformativeness,
  emptyTraderType,
} from "@/lib/dashboard/empty-state";

interface Case {
  name: string;
  // Heterogeneous components with distinct prop shapes are rendered with their
  // own empty-state props, so a permissive component type keeps the table uniform.
  Component: ComponentType<any>;
  props: Record<string, unknown>;
}

const CASES: Case[] = [
  { name: "BacktestHeatmap", Component: BacktestHeatmap, props: emptyBacktestHeatmap },
  { name: "D3IliHeatmap", Component: D3IliHeatmap, props: emptyD3IliHeatmap },
  { name: "GammaProfileChart", Component: GammaProfileChart, props: emptyGammaProfile },
  { name: "PotentialWellChart", Component: PotentialWellChart, props: emptyPotentialWell },
  { name: "SentimentHeatmap", Component: SentimentHeatmap, props: emptySentimentHeatmap },
  { name: "TailRiskQQPlot", Component: TailRiskQQPlot, props: emptyTailRiskQQPlot },
  { name: "ToxicityHeatmap", Component: ToxicityHeatmap, props: emptyToxicityHeatmap },
  { name: "VolatilityClusterChart", Component: VolatilityClusterChart, props: emptyVolatilityCluster },
  { name: "VolatilityPersistenceACF", Component: VolatilityPersistenceACF, props: emptyVolatilityPersistenceACF },
  { name: "ConvergencePlot", Component: ConvergencePlot, props: emptyConvergencePlot },
  { name: "AnomalyScoreIndicator", Component: AnomalyScoreIndicator, props: emptyAnomalyScore },
  { name: "LiquidityReliabilityScore", Component: LiquidityReliabilityScore, props: emptyLiquidityReliability },
  { name: "DynamicStopsPanel", Component: DynamicStopsPanel, props: emptyDynamicStops },
  { name: "ExecutionComparisonPanel", Component: ExecutionComparisonPanel, props: emptyExecutionComparison },
  { name: "GreeksSensitivityDashboard", Component: GreeksSensitivityDashboard, props: emptyGreeks },
  { name: "LeverageRegimeIndicator", Component: LeverageRegimeIndicator, props: emptyLeverageRegime },
  { name: "MacroEnvironmentPanel", Component: MacroEnvironmentPanel, props: emptyMacroEnvironment },
  { name: "MarketEfficiencyGapIndicator", Component: MarketEfficiencyGapIndicator, props: emptyMarketEfficiencyGap },
  { name: "ModelTournamentPanel", Component: ModelTournamentPanel, props: emptyModelTournament },
  { name: "MonetaryPolicyPanel", Component: MonetaryPolicyPanel, props: emptyMonetaryPolicy },
  { name: "OptimizerStatusPanel", Component: OptimizerStatusPanel, props: emptyOptimizerStatus },
  { name: "PairsVerificationPanel", Component: PairsVerificationPanel, props: emptyPairsVerification },
  { name: "ParticipationPanel", Component: ParticipationPanel, props: emptyParticipation },
  { name: "PerformanceDecompositionPanel", Component: PerformanceDecompositionPanel, props: emptyPerformanceDecomposition },
  { name: "PerformanceDualityPanel", Component: PerformanceDualityPanel, props: emptyPerformanceDuality },
  { name: "RegimeComparisonPanel", Component: RegimeComparisonPanel, props: emptyRegimeComparison },
  { name: "SignalExplainabilityPanel", Component: SignalExplainabilityPanel, props: emptySignalExplainability },
  { name: "SignalInformativenessPanel", Component: SignalInformativenessPanel, props: emptySignalInformativeness },
  { name: "TraderTypePanel", Component: TraderTypePanel, props: emptyTraderType },
];

describe("empty-state defaults (unbacked components)", () => {
  it.each(CASES)(
    "given its empty-state default, when $name renders, it does not throw and mounts its container",
    ({ Component, props }) => {
      const { container } = render(<Component {...props} />);
      expect(container.firstChild).not.toBeNull();
    },
  );

  it("covers every unbacked dashboard component", () => {
    expect(CASES).toHaveLength(29);
  });
});
