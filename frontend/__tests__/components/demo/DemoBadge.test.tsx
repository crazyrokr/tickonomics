import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DemoBadge } from "@/components/demo/DemoBadge";

describe("DemoBadge", () => {
  it("renders the Virtual Trading label", () => {
    render(<DemoBadge variant="virtual" />);
    expect(screen.getByText("Virtual Trading")).toBeInTheDocument();
  });

  it("renders the Proxy Dislocated label", () => {
    render(<DemoBadge variant="dislocated" />);
    expect(screen.getByText("Proxy Dislocated")).toBeInTheDocument();
  });

  it("renders nothing when inactive", () => {
    const { container } = render(<DemoBadge variant="degraded" active={false} />);
    expect(container.firstChild).toBeNull();
  });
});
