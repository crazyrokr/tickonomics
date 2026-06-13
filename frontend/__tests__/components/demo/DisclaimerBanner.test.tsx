import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DisclaimerBanner } from "@/components/demo/DisclaimerBanner";

describe("DisclaimerBanner", () => {
  it("renders the virtual-funds disclaimer", () => {
    render(<DisclaimerBanner />);
    expect(screen.getByText(/simulated portfolio using virtual funds/i)).toBeInTheDocument();
  });

  it("states it is not financial advice", () => {
    render(<DisclaimerBanner />);
    expect(screen.getByText(/Not financial advice/i)).toBeInTheDocument();
  });
});
