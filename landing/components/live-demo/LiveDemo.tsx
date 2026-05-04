"use client";

import dynamic from "next/dynamic";
import Section from "@/components/shared/Section";
import StatusBadges from "./StatusBadges";
import { useApiData } from "@/hooks/useApiData";
import type {
  IliHistoryPoint,
  RegimeResponse,
  ParticipationResponse,
} from "@/lib/types";

const IliChart = dynamic(() => import("./IliChart"), { ssr: false });

export default function LiveDemo() {
  const { data: iliData } = useApiData<IliHistoryPoint[]>(
    "/api/v1/kpi/ili/history?limit=90"
  );
  const { data: regimeData } = useApiData<RegimeResponse>(
    "/api/v1/regime/garch"
  );
  const { data: participationData } = useApiData<ParticipationResponse>(
    "/api/v1/participation/status"
  );

  const chartData = iliData ?? [];
  const latestIli = chartData[chartData.length - 1];
  const anomalyDetected = latestIli?.is_suspect_anomaly ?? false;

  return (
    <Section
      id="live-demo"
      className="mx-auto max-w-5xl px-6 py-20"
    >
      <h2 className="text-center text-3xl font-bold tracking-tight text-zinc-900 sm:text-4xl">
        Live Demo
      </h2>
      <p className="mx-auto mt-4 max-w-2xl text-center text-zinc-600">
        Real-time Interbank Liquidity Index with regime and participation status.
      </p>

      <div className="mt-10 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
        <div className="mb-4">
          <StatusBadges
            iliData={chartData}
            regime={regimeData?.regime_status}
            anomalyDetected={anomalyDetected}
            participation={participationData?.participation_status}
          />
        </div>

        {chartData.length > 0 ? (
          <IliChart data={chartData} />
        ) : (
          <div className="flex h-64 items-center justify-center text-zinc-400">
            Loading chart data...
          </div>
        )}
      </div>
    </Section>
  );
}
