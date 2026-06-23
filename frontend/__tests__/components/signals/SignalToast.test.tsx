import { describe, it, expect, vi } from "vitest";
import { render, screen, act } from "@testing-library/react";
import { SignalToast } from "@/components/signals/SignalToast";
import type { SignalMarker } from "@/types/api";

const actionable = (id: string, symbol = "SPY"): SignalMarker => ({
  id,
  timestamp: "2026-05-30T12:00:00Z",
  statusCode: "ACTIONABLE",
  direction: "LONG",
  symbol,
  iliValue: 0.8,
});

describe("SignalToast", () => {
  it("renders nothing when no signals", () => {
    render(<SignalToast signals={[]} />);
    expect(screen.queryByTestId("signal-toast-container")).not.toBeInTheDocument();
  });

  it("renders toast for each signal", () => {
    const signals: SignalMarker[] = [
      { id: "sig-1", timestamp: "2026-05-30T12:00:00Z", statusCode: "ACTIONABLE", direction: "LONG", symbol: "SPY", iliValue: 0.8 },
    ];
    render(<SignalToast signals={signals} />);
    expect(screen.getByTestId("signal-toast-container")).toBeInTheDocument();
    expect(screen.getByTestId("toast-sig-1")).toBeInTheDocument();
    expect(screen.getByText("ACTIONABLE")).toBeInTheDocument();
    expect(screen.getByText("LONG")).toBeInTheDocument();
  });

  it("renders multiple toasts", () => {
    const signals: SignalMarker[] = [
      { id: "sig-1", timestamp: "2026-05-30T12:00:00Z", statusCode: "ACTIONABLE", direction: "LONG", symbol: "SPY", iliValue: 0.8 },
      { id: "sig-2", timestamp: "2026-05-30T11:00:00Z", statusCode: "COOLDOWN", direction: "SHORT", symbol: "QQQ", iliValue: 0.2 },
    ];
    render(<SignalToast signals={signals} />);
    expect(screen.getByTestId("toast-sig-1")).toBeInTheDocument();
    expect(screen.getByTestId("toast-sig-2")).toBeInTheDocument();
  });

  it("shows ILI value and symbol", () => {
    const signals: SignalMarker[] = [
      { id: "sig-1", timestamp: "2026-05-30T12:00:00Z", statusCode: "ACTIONABLE", direction: "LONG", symbol: "SPY", iliValue: 0.8 },
    ];
    render(<SignalToast signals={signals} />);
    expect(screen.getByText(/SPY @ ILI 0\.800/)).toBeInTheDocument();
  });

  it("dismisses a signal after its own autoDismissMs countdown", () => {
    vi.useFakeTimers();
    try {
      render(<SignalToast signals={[actionable("sig-1")]} autoDismissMs={1000} />);
      expect(screen.getByTestId("toast-sig-1")).toBeInTheDocument();

      act(() => {
        vi.advanceTimersByTime(1000);
      });

      expect(screen.queryByTestId("toast-sig-1")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it("does not reset an existing signal's timer when a new signal arrives", () => {
    vi.useFakeTimers();
    try {
      const { rerender } = render(
        <SignalToast signals={[actionable("sig-1")]} autoDismissMs={1000} />,
      );
      // Advance 700ms so sig-1 is most of the way through its 1000ms countdown.
      act(() => {
        vi.advanceTimersByTime(700);
      });

      // A second signal arrives — sig-1's countdown must NOT restart.
      rerender(
        <SignalToast
          signals={[actionable("sig-1"), actionable("sig-2", "QQQ")]}
          autoDismissMs={1000}
        />,
      );

      // 400ms more: sig-1 reaches its original 1000ms and is dismissed;
      // sig-2 (scheduled at +700ms) still has ~600ms left and stays visible.
      act(() => {
        vi.advanceTimersByTime(400);
      });

      expect(screen.queryByTestId("toast-sig-1")).not.toBeInTheDocument();
      expect(screen.getByTestId("toast-sig-2")).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});
