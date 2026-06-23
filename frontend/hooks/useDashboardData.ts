"use client";

import {
  useIli,
  useIliHistory,
  useLiquidityStress,
  useRepoEquityBeta,
  useRrpDrain,
  useVolatilityRegime,
  useCorrelationMatrix,
  useSystemicRiskHeatmap,
} from "./useKpiData";
import { useHealth } from "./useHealth";
import { useConfig, useConfigHistory } from "./useConfig";
import { useSignals } from "./useSignals";
import type { SectionId } from "@/lib/dashboard/sections";

// Tabs whose widgets read KPI endpoints. Risk and trading render empty-state placeholders
// (no live data), and config only reads config endpoints — so KPI queries are gated off them.
const KPI_SECTIONS: ReadonlySet<SectionId> = new Set(["overview", "kpis", "charts"]);

export function useDashboardData(token?: string, activeSection?: SectionId) {
  const kpiEnabled = !activeSection || KPI_SECTIONS.has(activeSection);
  const configEnabled = !activeSection || activeSection === "config";

  const ili = useIli(kpiEnabled);
  const iliHistory = useIliHistory(kpiEnabled);
  const liquidityStress = useLiquidityStress(kpiEnabled);
  const repoEquityBeta = useRepoEquityBeta(kpiEnabled);
  const rrpDrain = useRrpDrain(kpiEnabled);
  const volatilityRegime = useVolatilityRegime(kpiEnabled);
  const correlationMatrix = useCorrelationMatrix(kpiEnabled);
  const systemicRiskHeatmap = useSystemicRiskHeatmap(kpiEnabled);
  // Health and signals stay always-on: health is a cheap shell status, and the SignalToast
  // renders outside the section switch and needs the live signal stream on every tab.
  const health = useHealth();
  const config = useConfig(configEnabled);
  const configHistory = useConfigHistory(configEnabled);
  const signals = useSignals(token);

  return {
    ili,
    iliHistory,
    liquidityStress,
    repoEquityBeta,
    rrpDrain,
    volatilityRegime,
    correlationMatrix,
    systemicRiskHeatmap,
    health,
    config,
    configHistory,
    signals,
  };
}

export type DashboardData = ReturnType<typeof useDashboardData>;
