"use client";

import { useState } from "react";
import { useDashboardData } from "@/hooks/useDashboardData";
import { DEFAULT_SECTION, type SectionId } from "@/lib/dashboard/sections";
import { SectionTabs } from "./SectionTabs";
import { OverviewSection } from "./sections/OverviewSection";
import { KpiSection } from "./sections/KpiSection";
import { ChartsSection } from "./sections/ChartsSection";
import { RiskSection } from "./sections/RiskSection";
import { TradingSection } from "./sections/TradingSection";
import { ConfigSection } from "./sections/ConfigSection";
import { SignalToast } from "@/components/signals/SignalToast";

export function DashboardClient() {
  const [activeSection, setActiveSection] = useState<SectionId>(DEFAULT_SECTION);
  const data = useDashboardData();

  return (
    <>
      <SectionTabs active={activeSection} onSelect={setActiveSection} />

      <div data-testid={`section-${activeSection}`}>
        {activeSection === "overview" && <OverviewSection data={data} />}
        {activeSection === "kpis" && <KpiSection data={data} />}
        {activeSection === "charts" && <ChartsSection data={data} />}
        {activeSection === "risk" && <RiskSection data={data} />}
        {activeSection === "trading" && <TradingSection data={data} />}
        {activeSection === "config" && <ConfigSection data={data} />}
      </div>

      <SignalToast signals={data.signals.signals} />
    </>
  );
}
