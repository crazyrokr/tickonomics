"use client";

import type { DashboardData } from "@/hooks/useDashboardData";
import { ConfigEditor } from "@/components/config/ConfigEditor";
import { ConfigHistory } from "@/components/config/ConfigHistory";
import { useUpdateConfig } from "@/hooks/useConfig";

const isAdmin = process.env.NEXT_PUBLIC_CONFIG_ADMIN === "true";

export function ConfigSection({ data }: { data: DashboardData }) {
  const updateConfig = useUpdateConfig();

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <ConfigEditor
        initialConfig={data.config.data ?? []}
        onSubmit={(entries) => updateConfig.mutateAsync(entries)}
        isAdmin={isAdmin}
      />
      <ConfigHistory
        entries={data.configHistory.data ?? []}
        isLoading={data.configHistory.isLoading}
      />
    </div>
  );
}
