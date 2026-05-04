import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RepoEquityBetaTable } from "@/components/kpi/RepoEquityBetaTable";
import type { RepoEquityBeta } from "@/types/api";

describe("RepoEquityBetaTable", () => {
  it("shows loading skeleton", () => {
    render(<RepoEquityBetaTable isLoading />);
    expect(screen.queryByText("Repo / Equity Beta")).not.toBeInTheDocument();
  });

  it("renders table with beta data", () => {
    const data: RepoEquityBeta[] = [
      { symbol: "SPY", beta: 1.23, lastUpdate: "2026-05-30T12:00:00Z" },
      { symbol: "QQQ", beta: 0.87, lastUpdate: "2026-05-30T12:00:00Z" },
    ];
    render(<RepoEquityBetaTable data={data} />);
    expect(screen.getByText("SPY")).toBeInTheDocument();
    expect(screen.getByText("QQQ")).toBeInTheDocument();
  });

  it("shows red color for beta > 1 (amplifying)", () => {
    const data: RepoEquityBeta[] = [
      { symbol: "SPY", beta: 1.5, lastUpdate: "2026-05-30T12:00:00Z" },
    ];
    render(<RepoEquityBetaTable data={data} />);
    const cell = screen.getByTestId("beta-SPY");
    expect(cell.className).toContain("text-ili-red");
  });

  it("shows green color for beta < 1 (dampening)", () => {
    const data: RepoEquityBeta[] = [
      { symbol: "TLT", beta: 0.5, lastUpdate: "2026-05-30T12:00:00Z" },
    ];
    render(<RepoEquityBetaTable data={data} />);
    const cell = screen.getByTestId("beta-TLT");
    expect(cell.className).toContain("text-ili-green");
  });

  it("shows empty state when no data", () => {
    render(<RepoEquityBetaTable data={[]} />);
    expect(screen.getByText("No data available")).toBeInTheDocument();
  });
});
