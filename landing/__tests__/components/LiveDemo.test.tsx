import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import LiveDemo from "@/components/live-demo/LiveDemo";
import { mockIliHistory } from "@/lib/mock-data";

const mockUseApiData = vi.fn();

vi.mock("@/hooks/useApiData", () => ({
  get useApiData() {
    return mockUseApiData;
  },
}));

vi.mock("next/dynamic", () => ({
  __esModule: true,
  default: (...args: unknown[]) => {
    const loader = args[0] as { render?: (mod: unknown) => unknown };
    if (typeof loader === "object" && loader.render) {
      return loader.render({ default: () => null });
    }
    const Component = () => <div data-testid="ili-chart">Chart</div>;
    Component.displayName = "DynamicIliChart";
    return Component;
  },
}));

describe("LiveDemo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders section heading", () => {
    mockUseApiData.mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: false,
    });

    render(<LiveDemo />);
    expect(
      screen.getByRole("heading", { level: 2, name: /live demo/i })
    ).toBeInTheDocument();
  });

  it("renders chart when data is available", () => {
    mockUseApiData
      .mockReturnValueOnce({
        data: mockIliHistory,
        error: undefined,
        isLoading: false,
      })
      .mockReturnValue({
        data: undefined,
        error: undefined,
        isLoading: false,
      });

    render(<LiveDemo />);
    expect(screen.getByTestId("ili-chart")).toBeInTheDocument();
  });

  it("renders loading placeholder when no data", () => {
    mockUseApiData.mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: false,
    });

    render(<LiveDemo />);
    expect(screen.getByText(/loading chart data/i)).toBeInTheDocument();
  });
});
