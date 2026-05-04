import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import SignalShowcase from "@/components/signal-showcase/SignalShowcase";
import type { AlphaSignal } from "@/lib/types";

const mockSignals: AlphaSignal[] = [
  {
    timestamp: new Date().toISOString(),
    strategyId: "s1",
    symbol: "SPY",
    direction: "LONG",
    strength: 0.82,
    confidence: 0.74,
    expected_move: 1.35,
    metrics: {},
    status: "ACTIONABLE",
  },
  {
    timestamp: new Date().toISOString(),
    strategyId: "s2",
    symbol: "QQQ",
    direction: "SHORT",
    strength: 0.65,
    confidence: 0.58,
    expected_move: -0.92,
    metrics: {},
    status: "SPECULATIVE_STALE_MACRO",
  },
];

const mockUseApiData = vi.fn();

vi.mock("@/hooks/useApiData", () => ({
  get useApiData() {
    return mockUseApiData;
  },
}));

describe("SignalShowcase", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows loading state", () => {
    mockUseApiData.mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: true,
    });

    render(<SignalShowcase />);
    expect(screen.getByText(/loading signals/i)).toBeInTheDocument();
  });

  it("renders signal table with data", () => {
    mockUseApiData.mockReturnValue({
      data: mockSignals,
      error: undefined,
      isLoading: false,
    });

    render(<SignalShowcase />);
    expect(screen.getByText("SPY")).toBeInTheDocument();
    expect(screen.getByText("QQQ")).toBeInTheDocument();
    expect(screen.getByText("LONG")).toBeInTheDocument();
    expect(screen.getByText("SHORT")).toBeInTheDocument();
    expect(screen.getByText("Actionable")).toBeInTheDocument();
    expect(screen.getByText("Stale Macro")).toBeInTheDocument();
  });

  it("shows empty state when no signals", () => {
    mockUseApiData.mockReturnValue({
      data: [],
      error: undefined,
      isLoading: false,
    });

    render(<SignalShowcase />);
    expect(screen.getByText(/no signals available/i)).toBeInTheDocument();
  });

  it("shows fallback message on error", () => {
    mockUseApiData.mockReturnValue({
      data: undefined,
      error: new Error("Network error"),
      isLoading: false,
    });

    render(<SignalShowcase />);
    expect(
      screen.getByText(/showing demo data/i)
    ).toBeInTheDocument();
  });
});
