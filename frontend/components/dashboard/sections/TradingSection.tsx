"use client";

import type { DashboardData } from "@/hooks/useDashboardData";
import { ExecutionComparisonPanel } from "@/components/panels/ExecutionComparisonPanel";
import { OptimizerStatusPanel } from "@/components/panels/OptimizerStatusPanel";
import { ParticipationPanel } from "@/components/panels/ParticipationPanel";
import { PerformanceDecompositionPanel } from "@/components/panels/PerformanceDecompositionPanel";
import { MonetaryPolicyPanel } from "@/components/panels/MonetaryPolicyPanel";
import { MacroEnvironmentPanel } from "@/components/panels/MacroEnvironmentPanel";
import {
  emptyExecutionComparison,
  emptyOptimizerStatus,
  emptyParticipation,
  emptyPerformanceDecomposition,
  emptyMonetaryPolicy,
  emptyMacroEnvironment,
} from "@/lib/dashboard/empty-state";

// Every widget in this section awaits a Track 1 backend endpoint, so the
// shared DashboardData payload is intentionally unused here.
export function TradingSection(_: { data: DashboardData }) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <ExecutionComparisonPanel {...emptyExecutionComparison} />
      <OptimizerStatusPanel {...emptyOptimizerStatus} />
      <ParticipationPanel {...emptyParticipation} />
      <PerformanceDecompositionPanel {...emptyPerformanceDecomposition} />
      <MonetaryPolicyPanel {...emptyMonetaryPolicy} />
      <MacroEnvironmentPanel {...emptyMacroEnvironment} />
    </div>
  );
}
