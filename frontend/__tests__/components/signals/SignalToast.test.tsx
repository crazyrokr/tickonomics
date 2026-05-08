import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SignalToast } from "@/components/signals/SignalToast";
import type { SignalMarker } from "@/types/api";

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
});
