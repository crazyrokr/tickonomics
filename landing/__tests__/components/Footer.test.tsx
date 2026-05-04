import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import Footer from "@/components/footer/Footer";

describe("Footer", () => {
  it("renders GitHub link", () => {
    render(<Footer />);
    const link = screen.getByRole("link", { name: /github/i });
    expect(link).toHaveAttribute("href", "https://github.com/user/tickonomics");
  });

  it("renders Documentation link", () => {
    render(<Footer />);
    const link = screen.getByRole("link", { name: /documentation/i });
    expect(link).toHaveAttribute("href", "https://docs.tickonomics.io");
  });

  it("renders copyright with current year", () => {
    render(<Footer />);
    const year = new Date().getFullYear().toString();
    expect(screen.getByText(new RegExp(year))).toBeInTheDocument();
  });

  it("renders disclaimer text", () => {
    render(<Footer />);
    expect(screen.getByText(/not financial advice/i)).toBeInTheDocument();
  });
});
