import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { IliCard } from "@/components/kpi/IliCard";
import type { IliValue } from "@/types/api";

const validIli: IliValue = {
  value: 0.75,
  status: "VALID",
  activeWeights: { rrp: 0.4, spread: 0.35, vol: 0.25 },
  excludedComponents: [],
  timestamp: "2026-05-30T12:00:00Z",
};

const degradedIli: IliValue = {
  value: -0.3,
  status: "DEGRADED_COMPONENT_STALE",
  activeWeights: { rrp: 0.6, vol: 0.4 },
  excludedComponents: ["spread"],
  timestamp: "2026-05-30T12:00:00Z",
};

const dislocatedIli: IliValue = {
  value: -1.2,
  status: "DISLOCATED",
  activeWeights: { rrp: 1.0 },
  excludedComponents: ["spread", "vol"],
  timestamp: "2026-05-30T12:00:00Z",
};

describe("IliCard", () => {
  it("shows loading skeleton", () => {
    render(<IliCard isLoading />);
    expect(screen.queryByText("ILI")).not.toBeInTheDocument();
  });

  it("renders ILI value with VALID status badge", () => {
    render(<IliCard data={validIli} />);
    expect(screen.getByText("0.750")).toBeInTheDocument();
    expect(screen.getByTestId("ili-status-badge")).toHaveTextContent("VALID");
  });

  it("renders DEGRADED status badge", () => {
    render(<IliCard data={degradedIli} />);
    expect(screen.getByTestId("ili-status-badge")).toHaveTextContent("DEGRADED");
  });

  it("renders DISLOCATED status badge", () => {
    render(<IliCard data={dislocatedIli} />);
    expect(screen.getByTestId("ili-status-badge")).toHaveTextContent("DISLOCATED");
  });

  it("shows active weights when degraded", () => {
    render(<IliCard data={degradedIli} />);
    expect(screen.getByTestId("ili-degraded-info")).toBeInTheDocument();
    expect(screen.getByText(/rrp: 0\.60/)).toBeInTheDocument();
  });

  it("shows excluded components when degraded", () => {
    render(<IliCard data={degradedIli} />);
    expect(screen.getByText(/Excluded: spread/)).toBeInTheDocument();
  });

  it("hides degraded info when status is VALID", () => {
    render(<IliCard data={validIli} />);
    expect(screen.queryByTestId("ili-degraded-info")).not.toBeInTheDocument();
  });

  it("renders sparkline when history provided", () => {
    const { container } = render(
      <IliCard data={validIli} history={[0.1, 0.3, 0.5, 0.75]} />,
    );
    expect(container.querySelector("polyline")).toBeInTheDocument();
  });
});
