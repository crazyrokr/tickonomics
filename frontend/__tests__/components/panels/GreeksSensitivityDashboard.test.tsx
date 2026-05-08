import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { GreeksSensitivityDashboard } from "@/components/panels/GreeksSensitivityDashboard";

describe("GreeksSensitivityDashboard", () => {
  const defaultProps = {
    greeks: {
      repoDelta: 0.15,
      rateDelta: -0.32,
      rateGamma: 0.08,
      spreadDelta: 0.55,
      volga: -0.12,
    },
    dv01: 1.25,
    convexity: 0.45,
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<GreeksSensitivityDashboard {...defaultProps} isLoading />);

    expect(screen.queryByText("Greeks Sensitivity")).not.toBeInTheDocument();
    expect(screen.queryByTestId("greeks-grid")).not.toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows heading and greeks grid", () => {
    render(<GreeksSensitivityDashboard {...defaultProps} />);

    expect(screen.getByText("Greeks Sensitivity")).toBeInTheDocument();
    expect(screen.getByTestId("greeks-grid")).toBeInTheDocument();
    expect(screen.getByTestId("risk-gauges")).toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows all Greek cards with correct testids", () => {
    render(<GreeksSensitivityDashboard {...defaultProps} />);

    expect(screen.getByTestId("greek-repo-delta")).toBeInTheDocument();
    expect(screen.getByTestId("greek-rate-delta")).toBeInTheDocument();
    expect(screen.getByTestId("greek-rate-gamma")).toBeInTheDocument();
    expect(screen.getByTestId("greek-spread-delta")).toBeInTheDocument();
    expect(screen.getByTestId("greek-volga")).toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then displays Greek values with correct formatting", () => {
    render(<GreeksSensitivityDashboard {...defaultProps} />);

    expect(screen.getByText("+0.1500")).toBeInTheDocument();
    expect(screen.getByText("-0.3200")).toBeInTheDocument();
    expect(screen.getByText("+0.0800")).toBeInTheDocument();
    expect(screen.getByText("+0.5500")).toBeInTheDocument();
    expect(screen.getByText("-0.1200")).toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows DV01 and Convexity gauge bars", () => {
    render(<GreeksSensitivityDashboard {...defaultProps} />);

    expect(screen.getByTestId("gauge-dv01")).toBeInTheDocument();
    expect(screen.getByTestId("gauge-convexity")).toBeInTheDocument();
    expect(screen.getByTestId("gauge-fill-dv01")).toBeInTheDocument();
    expect(screen.getByTestId("gauge-fill-convexity")).toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then displays DV01 and Convexity numeric values", () => {
    render(<GreeksSensitivityDashboard {...defaultProps} />);

    expect(screen.getByText("1.25")).toBeInTheDocument();
    expect(screen.getByText("0.45")).toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows magnitude legend with Low, Medium, High", () => {
    render(<GreeksSensitivityDashboard {...defaultProps} />);

    expect(screen.getByText("Low")).toBeInTheDocument();
    expect(screen.getByText("Medium")).toBeInTheDocument();
    expect(screen.getByText("High")).toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows all Greek card labels", () => {
    render(<GreeksSensitivityDashboard {...defaultProps} />);

    expect(screen.getByText("Repo Delta")).toBeInTheDocument();
    expect(screen.getByText("Rate Delta")).toBeInTheDocument();
    expect(screen.getByText("Rate Gamma")).toBeInTheDocument();
    expect(screen.getByText("Spread Delta")).toBeInTheDocument();
    expect(screen.getByText("Volga")).toBeInTheDocument();
  });
});
