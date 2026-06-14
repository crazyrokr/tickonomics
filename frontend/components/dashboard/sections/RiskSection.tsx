"use client";

import type { DashboardData } from "@/hooks/useDashboardData";
import { GreeksSensitivityDashboard } from "@/components/panels/GreeksSensitivityDashboard";
import { DynamicStopsPanel } from "@/components/panels/DynamicStopsPanel";
import { RegimeComparisonPanel } from "@/components/panels/RegimeComparisonPanel";
import { ModelTournamentPanel } from "@/components/panels/ModelTournamentPanel";
import { SignalExplainabilityPanel } from "@/components/panels/SignalExplainabilityPanel";
import { SignalInformativenessPanel } from "@/components/panels/SignalInformativenessPanel";
import { TraderTypePanel } from "@/components/panels/TraderTypePanel";
import { PairsVerificationPanel } from "@/components/panels/PairsVerificationPanel";
import {
  emptyGreeks,
  emptyDynamicStops,
  emptyRegimeComparison,
  emptyModelTournament,
  emptySignalExplainability,
  emptySignalInformativeness,
  emptyTraderType,
  emptyPairsVerification,
} from "@/lib/dashboard/empty-state";

export function RiskSection(_: { data: DashboardData }) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <GreeksSensitivityDashboard {...emptyGreeks} />
      <DynamicStopsPanel {...emptyDynamicStops} />
      <RegimeComparisonPanel {...emptyRegimeComparison} />
      <ModelTournamentPanel {...emptyModelTournament} />
      <SignalExplainabilityPanel {...emptySignalExplainability} />
      <SignalInformativenessPanel {...emptySignalInformativeness} />
      <TraderTypePanel {...emptyTraderType} />
      <PairsVerificationPanel {...emptyPairsVerification} />
    </div>
  );
}
