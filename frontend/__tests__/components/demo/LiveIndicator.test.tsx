import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { LiveIndicator } from "@/components/demo/LiveIndicator";

describe("LiveIndicator", () => {
  it("shows Live label when live", () => {
    render(<LiveIndicator live={true} />);
    expect(screen.getByText("Live")).toBeInTheDocument();
  });

  it("shows Idle label when not live", () => {
    render(<LiveIndicator live={false} />);
    expect(screen.getByText("Idle")).toBeInTheDocument();
  });

  it("labels the status for assistive technology", () => {
    render(<LiveIndicator live={true} />);
    expect(screen.getByLabelText("system live")).toBeInTheDocument();
  });
});
