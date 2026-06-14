export type SectionId =
  | "overview"
  | "kpis"
  | "charts"
  | "risk"
  | "trading"
  | "config";

export interface SectionMeta {
  id: SectionId;
  label: string;
}

export const SECTIONS: readonly SectionMeta[] = [
  { id: "overview", label: "Overview" },
  { id: "kpis", label: "KPIs" },
  { id: "charts", label: "Charts" },
  { id: "risk", label: "Risk" },
  { id: "trading", label: "Trading" },
  { id: "config", label: "Config" },
] as const;

export const DEFAULT_SECTION: SectionId = "overview";
