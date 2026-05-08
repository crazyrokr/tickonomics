import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PerformanceDecompositionPanel } from "@/components/panels/PerformanceDecompositionPanel";

describe("PerformanceDecompositionPanel", () => {
  it("shows loading skeleton when isLoading is true", () => {
    // Given the panel is in a loading state
    render(<PerformanceDecompositionPanel data={undefined} isLoading />);

    // Then the decomposition bars should not be rendered
    expect(screen.queryByTestId("decomposition-bars")).not.toBeInTheDocument();
    // And the skeleton container should be visible
    const skeleton = document.querySelector(".animate-pulse");
    expect(skeleton).toBeInTheDocument();
  });

  it("renders bar chart with holdings return and return gap", () => {
    // Given valid performance data
    const data = { holdingsReturn: 5.5, returnGap: 1.2, timestamp: "2025-06-01" };
    render(<PerformanceDecompositionPanel data={data} />);

    // Then the decomposition bars should be present
    expect(screen.getByTestId("decomposition-bars")).toBeInTheDocument();
    // And the holdings bar should show the correct value
    expect(screen.getByTestId("holdings-bar")).toHaveTextContent("5.50%");
    // And the gap bar should show the correct value with a plus sign
    expect(screen.getByTestId("gap-bar")).toHaveTextContent("+1.20%");
  });

  it("shows total return as sum of holdings return and return gap", () => {
    // Given performance data with a holdings return of 5.50 and gap of 1.20
    const data = { holdingsReturn: 5.5, returnGap: 1.2, timestamp: "2025-06-01" };
    render(<PerformanceDecompositionPanel data={data} />);

    // Then the total should display 6.70%
    expect(screen.getByText("6.70%")).toBeInTheDocument();
    // And the "Total" label should be present
    expect(screen.getByText("Total")).toBeInTheDocument();
  });

  it("uses green style for positive return gap", () => {
    // Given performance data with a positive return gap
    const data = { holdingsReturn: 3.0, returnGap: 2.5, timestamp: "2025-06-01" };
    render(<PerformanceDecompositionPanel data={data} />);

    // Then the gap bar background color should be green
    const gapBar = screen.getByTestId("gap-bar");
    expect(gapBar.style.backgroundColor).toBe("var(--color-ili-green)");
  });

  it("uses red style for negative return gap", () => {
    // Given performance data with a negative return gap
    const data = { holdingsReturn: 3.0, returnGap: -1.8, timestamp: "2025-06-01" };
    render(<PerformanceDecompositionPanel data={data} />);

    // Then the gap bar background color should be red
    const gapBar = screen.getByTestId("gap-bar");
    expect(gapBar.style.backgroundColor).toBe("var(--color-ili-red)");
    // And the value should be displayed without a leading plus
    expect(gapBar).toHaveTextContent("-1.80%");
  });
});
