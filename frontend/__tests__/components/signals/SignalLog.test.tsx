import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { SignalLog } from "@/components/signals/SignalLog";
import type { SignalMarker } from "@/types/api";

vi.mock("@finos/perspective", () => ({
  worker: vi.fn(() => Promise.resolve({
    table: vi.fn(() => "mock-table"),
  })),
}));

vi.mock("@finos/perspective-viewer", () => ({}));
vi.mock("@finos/perspective-viewer-datagrid", () => ({}));

describe("SignalLog", () => {
  const mockSignals: SignalMarker[] = [
    { id: "sig-1", timestamp: "2026-05-30T12:00:00Z", statusCode: "ACTIONABLE", direction: "LONG", symbol: "SPY", iliValue: 0.8 },
    { id: "sig-2", timestamp: "2026-05-30T11:00:00Z", statusCode: "COOLDOWN", direction: "SHORT", symbol: "QQQ", iliValue: 0.2 },
  ];

  it("shows loading skeleton", () => {
    render(<SignalLog signals={[]} isLoading />);
    expect(screen.queryByText("Signal Log")).not.toBeInTheDocument();
  });

  it("renders panel with data", () => {
    render(<SignalLog signals={mockSignals} />);
    expect(screen.getByText("Signal Log")).toBeInTheDocument();
    expect(screen.getByTestId("signal-log")).toBeInTheDocument();
  });

  it("shows empty state when no signals", () => {
    render(<SignalLog signals={[]} />);
    expect(screen.getByText("No signals recorded")).toBeInTheDocument();
  });
});
