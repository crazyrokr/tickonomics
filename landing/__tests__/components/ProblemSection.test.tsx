import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import ProblemSection from "@/components/problem/ProblemSection";

describe("ProblemSection", () => {
  it("renders section heading", () => {
    render(<ProblemSection />);
    expect(
      screen.getByRole("heading", { level: 2, name: /the problem/i })
    ).toBeInTheDocument();
  });

  it("renders three pain-point cards", () => {
    render(<ProblemSection />);
    expect(
      screen.getByRole("heading", { level: 3, name: /liquidity blind spots/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 3, name: /lagging indicators/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 3, name: /manual correlation/i })
    ).toBeInTheDocument();
  });
});
