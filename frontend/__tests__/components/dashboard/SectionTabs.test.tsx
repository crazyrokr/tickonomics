import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SectionTabs } from "@/components/dashboard/SectionTabs";
import { SECTIONS } from "@/lib/dashboard/sections";

describe("SectionTabs", () => {
  it("renders one tab per defined section", () => {
    render(<SectionTabs active="overview" onSelect={() => {}} />);
    for (const section of SECTIONS) {
      expect(screen.getByTestId(`tab-${section.id}`)).toBeInTheDocument();
    }
  });

  it("marks only the active tab as selected", () => {
    render(<SectionTabs active="charts" onSelect={() => {}} />);
    expect(screen.getByTestId("tab-charts")).toHaveAttribute("aria-selected", "true");
    expect(screen.getByTestId("tab-overview")).toHaveAttribute("aria-selected", "false");
  });

  it("calls onSelect with the clicked section id", () => {
    const onSelect = vi.fn();
    render(<SectionTabs active="overview" onSelect={onSelect} />);
    fireEvent.click(screen.getByTestId("tab-config"));
    expect(onSelect).toHaveBeenCalledWith("config");
    expect(onSelect).toHaveBeenCalledTimes(1);
  });
});
