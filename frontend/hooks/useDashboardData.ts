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

export function useDashboardData(token?: string) {
  const ili = useIli();
  const iliHistory = useIliHistory();
  const liquidityStress = useLiquidityStress();
  const repoEquityBeta = useRepoEquityBeta();
  const rrpDrain = useRrpDrain();
  const volatilityRegime = useVolatilityRegime();
  const correlationMatrix = useCorrelationMatrix();
  const systemicRiskHeatmap = useSystemicRiskHeatmap();
  const health = useHealth();
  const config = useConfig();
  const configHistory = useConfigHistory();
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
