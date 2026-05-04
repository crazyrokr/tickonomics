import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import HowItWorks from "@/components/how-it-works/HowItWorks";

describe("HowItWorks", () => {
  it("renders section heading", () => {
    render(<HowItWorks />);
    expect(
      screen.getByRole("heading", { level: 2, name: /how it works/i })
    ).toBeInTheDocument();
  });

  it("renders three steps: Ingest, Analyze, Act", () => {
    render(<HowItWorks />);
    expect(screen.getByText("Ingest")).toBeInTheDocument();
    expect(screen.getByText("Analyze")).toBeInTheDocument();
    expect(screen.getByText("Act")).toBeInTheDocument();
  });

  it("renders step numbers 1, 2, 3", () => {
    render(<HowItWorks />);
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
  });
});
