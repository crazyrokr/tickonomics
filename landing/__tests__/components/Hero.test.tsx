import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import Hero from "@/components/hero/Hero";

describe("Hero", () => {
  it("renders the tagline heading", () => {
    render(<Hero />);
    expect(
      screen.getByRole("heading", { level: 1 })
    ).toHaveTextContent("Real-Time Funding Market Intelligence");
  });

  it("renders the Start Demo CTA linking to app.tickonomics.io", () => {
    render(<Hero />);
    const link = screen.getByRole("link", { name: /start demo/i });
    expect(link).toHaveAttribute("href", "https://app.tickonomics.io");
    expect(link).toHaveAttribute("target", "_blank");
  });

  it("renders the GitHub CTA", () => {
    render(<Hero />);
    const link = screen.getByRole("link", { name: /github/i });
    expect(link).toHaveAttribute("href", "https://github.com/user/tickonomics");
  });
});
