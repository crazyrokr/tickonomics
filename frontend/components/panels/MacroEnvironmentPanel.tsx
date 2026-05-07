"use client";

import { useState } from "react";

type ClimateScenario = "baseline" | "carbon_tax" | "green_transition";

interface ClimateIndex {
  emissionCoefficient: number;
  renewablePotential: number;
  iliSensitivity: number;
}

interface MacroEnvironmentPanelProps {
  indices: ClimateIndex | undefined;
  onSimulate?: (scenario: ClimateScenario) => void;
  isLoading?: boolean;
}

const SCENARIOS: Record<ClimateScenario, { label: string; description: string }> = {
  baseline: { label: "Baseline", description: "Current trajectory" },
  carbon_tax: { label: "Carbon Tax", description: "Carbon pricing introduced" },
  green_transition: { label: "Green Transition", description: "Aggressive renewable adoption" },
};

export function MacroEnvironmentPanel({ indices, onSimulate, isLoading }: MacroEnvironmentPanelProps) {
  const [scenario, setScenario] = useState<ClimateScenario>("baseline");

  if (isLoading || !indices) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/2 mb-3" />
        <div className="space-y-2">
          <div className="h-8 bg-border rounded" />
          <div className="h-8 bg-border rounded" />
        </div>
      </div>
    );
  }

  const handleScenarioChange = (s: ClimateScenario) => {
    setScenario(s);
    onSimulate?.(s);
  };

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Macro Environment</h3>

      <div className="space-y-2 mb-4">
        <div className="flex justify-between text-sm">
          <span className="text-muted">Emission Coefficient</span>
          <span className="font-mono">{indices.emissionCoefficient.toFixed(4)}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-muted">Renewable Potential</span>
          <span className="font-mono">{indices.renewablePotential.toFixed(2)}%</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-muted">ILI Sensitivity</span>
          <span className="font-mono">{indices.iliSensitivity.toFixed(3)}</span>
        </div>
      </div>

      <div>
        <p className="text-xs text-muted mb-2">Climate Scenario</p>
        <div className="flex gap-2" data-testid="scenario-selector">
          {(Object.entries(SCENARIOS) as [ClimateScenario, typeof SCENARIOS.baseline][]).map(
            ([key, config]) => (
              <button
                key={key}
                onClick={() => handleScenarioChange(key)}
                className={`px-3 py-1.5 text-xs rounded border transition-colors ${
                  scenario === key
                    ? "border-ili-blue bg-ili-blue/10 text-ili-blue"
                    : "border-border text-muted hover:border-ili-blue/50"
                }`}
                data-testid={`scenario-${key}`}
              >
                {config.label}
              </button>
            ),
          )}
        </div>
        <p className="text-xs text-muted mt-1">
          {SCENARIOS[scenario].description}
        </p>
      </div>
    </div>
  );
}
