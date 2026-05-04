import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import PricingSection from "@/components/pricing/PricingSection";

describe("PricingSection", () => {
  it("renders 'Open Source' heading", () => {
    render(<PricingSection />);
    expect(
      screen.getByRole("heading", { level: 2, name: /open source/i })
    ).toBeInTheDocument();
  });

  it("shows $0 price", () => {
    render(<PricingSection />);
    expect(screen.getByText("$0")).toBeInTheDocument();
  });

  it("has GitHub link", () => {
    render(<PricingSection />);
    const link = screen.getByRole("link", { name: /view on github/i });
    expect(link).toHaveAttribute("href", "https://github.com/user/tickonomics");
  });
});
