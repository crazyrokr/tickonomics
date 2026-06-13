"use client";

import { SECTIONS, type SectionId } from "@/lib/dashboard/sections";

interface SectionTabsProps {
  active: SectionId;
  onSelect: (id: SectionId) => void;
}

export function SectionTabs({ active, onSelect }: SectionTabsProps) {
  return (
    <div
      role="tablist"
      aria-label="Dashboard sections"
      className="flex gap-1 border-b border-border mb-6 overflow-x-auto"
      data-testid="section-tabs"
    >
      {SECTIONS.map((section) => {
        const isActive = section.id === active;
        return (
          <button
            key={section.id}
            role="tab"
            aria-selected={isActive}
            data-testid={`tab-${section.id}`}
            onClick={() => onSelect(section.id)}
            className={`px-4 py-2 text-sm font-medium whitespace-nowrap border-b-2 -mb-px transition-colors ${
              isActive
                ? "border-ili-blue text-foreground"
                : "border-transparent text-muted hover:text-foreground"
            }`}
          >
            {section.label}
          </button>
        );
      })}
    </div>
  );
}
