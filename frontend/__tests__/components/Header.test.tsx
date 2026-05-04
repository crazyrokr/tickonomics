import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Header } from "@/components/layout/Header";

describe("Header", () => {
  it("renders dashboard title", () => {
    render(<Header />);
    expect(screen.getByText("Tickonomics Dashboard")).toBeInTheDocument();
  });

  it("renders ConnectionStatus component", () => {
    render(<Header />);
    expect(screen.getByText("Disconnected")).toBeInTheDocument();
  });
});
