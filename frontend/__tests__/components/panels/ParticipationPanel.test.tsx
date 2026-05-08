import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ParticipationPanel } from "@/components/panels/ParticipationPanel";

describe("ParticipationPanel", () => {
  const statuses = [
    {
      source: "FRED",
      admissible: true,
      suppressionReasons: [],
      history: [],
    },
    {
      source: "ILI Composite",
      admissible: false,
      suppressionReasons: ["stale data", "anomaly detected"],
      history: [
        { source: "ILI Composite", reason: "Data staleness", timestamp: "2026-05-30T10:00:00Z", triggeringCondition: "last_update > 3600s" },
      ],
    },
  ];

  it("shows loading skeleton", () => {
    render(<ParticipationPanel statuses={[]} isLoading />);
    expect(screen.queryByText("Participation Governance")).not.toBeInTheDocument();
  });

  it("renders all sources", () => {
    render(<ParticipationPanel statuses={statuses} />);
    expect(screen.getByText("FRED")).toBeInTheDocument();
    expect(screen.getByText("ILI Composite")).toBeInTheDocument();
  });

  it("shows admissible/suppressed status", () => {
    render(<ParticipationPanel statuses={statuses} />);
    expect(screen.getByText("Admissible")).toBeInTheDocument();
    expect(screen.getByText("Suppressed")).toBeInTheDocument();
  });

  it("expands to show suppression reasons", () => {
    render(<ParticipationPanel statuses={statuses} />);
    fireEvent.click(screen.getByTestId("participation-toggle-ILI Composite"));
    expect(screen.getByTestId("participation-detail-ILI Composite")).toBeInTheDocument();
    expect(screen.getByText(/stale data/)).toBeInTheDocument();
    expect(screen.getByText(/anomaly detected/)).toBeInTheDocument();
  });

  it("shows history events when expanded", () => {
    render(<ParticipationPanel statuses={statuses} />);
    fireEvent.click(screen.getByTestId("participation-toggle-ILI Composite"));
    expect(screen.getByText(/Data staleness/)).toBeInTheDocument();
  });

  it("collapses when clicked again", () => {
    render(<ParticipationPanel statuses={statuses} />);
    fireEvent.click(screen.getByTestId("participation-toggle-ILI Composite"));
    fireEvent.click(screen.getByTestId("participation-toggle-ILI Composite"));
    expect(screen.queryByTestId("participation-detail-ILI Composite")).not.toBeInTheDocument();
  });
});
