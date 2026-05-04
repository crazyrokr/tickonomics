import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ConnectionStatus } from "@/components/layout/ConnectionStatus";

describe("ConnectionStatus", () => {
  it("shows Connected state with green dot", () => {
    render(<ConnectionStatus state="connected" />);
    expect(screen.getByText("Connected")).toBeInTheDocument();
    const dot = screen.getByText("Connected").previousElementSibling;
    expect(dot?.className).toContain("bg-ili-green");
  });

  it("shows Connecting state with amber dot", () => {
    render(<ConnectionStatus state="connecting" />);
    expect(screen.getByText("Connecting")).toBeInTheDocument();
    const dot = screen.getByText("Connecting").previousElementSibling;
    expect(dot?.className).toContain("bg-ili-amber");
  });

  it("shows Disconnected state with red dot", () => {
    render(<ConnectionStatus state="disconnected" />);
    expect(screen.getByText("Disconnected")).toBeInTheDocument();
    const dot = screen.getByText("Disconnected").previousElementSibling;
    expect(dot?.className).toContain("bg-ili-red");
  });

  it("defaults to disconnected when no state provided", () => {
    render(<ConnectionStatus />);
    expect(screen.getByText("Disconnected")).toBeInTheDocument();
  });
});
