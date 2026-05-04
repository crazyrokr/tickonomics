import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import StatusBadges from "@/components/live-demo/StatusBadges";
import type { IliHistoryPoint } from "@/lib/types";

function makeIliPoint(
  status: IliHistoryPoint["data_status"]
): IliHistoryPoint {
  return {
    time: "2026-05-30",
    value: 0.45,
    data_status: status,
    is_suspect_anomaly: false,
    anomaly_score: 0.02,
  };
}

describe("StatusBadges", () => {
  it("renders VALID ILI badge with green variant", () => {
    render(
      <StatusBadges iliData={[makeIliPoint("VALID")]} regime="NORMAL" />
    );
    const iliBadge = screen.getByLabelText(/ILI status: VALID/);
    expect(iliBadge).toHaveTextContent("ILI: VALID");
  });

  it("renders DEGRADED_COMPONENT_STALE ILI badge", () => {
    render(
      <StatusBadges
        iliData={[makeIliPoint("DEGRADED_COMPONENT_STALE")]}
        regime="NORMAL"
      />
    );
    const badge = screen.getByLabelText(/ILI status: DEGRADED/);
    expect(badge).toHaveTextContent("DEGRADED COMPONENT STALE");
  });

  it("renders DISLOCATED ILI badge", () => {
    render(
      <StatusBadges iliData={[makeIliPoint("DISLOCATED")]} regime="NORMAL" />
    );
    const badge = screen.getByLabelText(/ILI status: DISLOCATED/);
    expect(badge).toHaveTextContent("DISLOCATED");
  });

  it("renders all four regime states with correct labels", () => {
    const regimes = [
      "LOW_VOL",
      "NORMAL",
      "HIGH_VOL",
      "EXOGENOUS_SHOCK",
    ] as const;

    for (const regime of regimes) {
      const { unmount } = render(<StatusBadges regime={regime} />);
      expect(
        screen.getByLabelText(`Regime: ${regime}`)
      ).toBeInTheDocument();
      unmount();
    }
  });

  it("renders anomaly detected badge when anomalyDetected is true", () => {
    render(<StatusBadges anomalyDetected regime="NORMAL" />);
    expect(screen.getByLabelText(/Anomaly: detected/)).toHaveTextContent(
      "Anomaly: Detected"
    );
  });

  it("renders no anomaly badge by default", () => {
    render(<StatusBadges />);
    expect(screen.getByLabelText(/Anomaly: none/)).toHaveTextContent(
      "Anomaly: None"
    );
  });

  it("renders ADMISSIBLE participation badge", () => {
    render(<StatusBadges participation="ADMISSIBLE" />);
    expect(
      screen.getByLabelText("Participation: ADMISSIBLE")
    ).toHaveTextContent("Participation: ADMISSIBLE");
  });

  it("renders SUPPRESSED participation badge", () => {
    render(<StatusBadges participation="SUPPRESSED" />);
    expect(
      screen.getByLabelText("Participation: SUPPRESSED")
    ).toHaveTextContent("Participation: SUPPRESSED");
  });
});
