import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ConfigHistory } from "@/components/config/ConfigHistory";
import type { ConfigHistoryEntry } from "@/types/api";

const mockEntries: ConfigHistoryEntry[] = [
  {
    id: "hist-1",
    timestamp: "2026-05-30T12:00:00Z",
    author: "admin@tickonomics.io",
    auditReason: "Recalibration based on Q2 2026 ILI drift",
    diff: '- "threshold": 0.5\n+ "threshold": 0.7',
  },
  {
    id: "hist-2",
    timestamp: "2026-05-29T10:00:00Z",
    author: "system",
    auditReason: "Automated lookback window adjustment",
    diff: '- "lookback_days": 252\n+ "lookback_days": 180',
  },
];

describe("ConfigHistory", () => {
  it("shows loading skeleton", () => {
    render(<ConfigHistory entries={[]} isLoading />);
    expect(screen.queryByText("Configuration History")).not.toBeInTheDocument();
  });

  it("renders all history entries", () => {
    render(<ConfigHistory entries={mockEntries} />);
    expect(screen.getByText("Configuration History")).toBeInTheDocument();
    expect(screen.getByTestId("history-hist-1")).toBeInTheDocument();
    expect(screen.getByTestId("history-hist-2")).toBeInTheDocument();
  });

  it("displays audit_reason for each entry", () => {
    render(<ConfigHistory entries={mockEntries} />);
    expect(screen.getByTestId("audit-reason-hist-1")).toHaveTextContent(
      "Recalibration based on Q2 2026 ILI drift",
    );
    expect(screen.getByTestId("audit-reason-hist-2")).toHaveTextContent(
      "Automated lookback window adjustment",
    );
  });

  it("shows empty state when no entries", () => {
    render(<ConfigHistory entries={[]} />);
    expect(screen.getByText("No configuration changes recorded")).toBeInTheDocument();
  });

  it("renders diff content", () => {
    render(<ConfigHistory entries={mockEntries} />);
    expect(screen.getByText(/"threshold": 0.5/)).toBeInTheDocument();
    expect(screen.getByText(/"threshold": 0.7/)).toBeInTheDocument();
  });
});
