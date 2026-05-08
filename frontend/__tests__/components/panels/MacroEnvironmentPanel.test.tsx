import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MacroEnvironmentPanel } from "@/components/panels/MacroEnvironmentPanel";

describe("MacroEnvironmentPanel", () => {
  const indices = { emissionCoefficient: 0.0042, renewablePotential: 34.5, iliSensitivity: 0.127 };

  it("shows loading skeleton", () => {
    render(<MacroEnvironmentPanel indices={undefined} isLoading />);
    expect(screen.queryByText("Macro Environment")).not.toBeInTheDocument();
  });

  it("renders climate indices", () => {
    render(<MacroEnvironmentPanel indices={indices} />);
    expect(screen.getByText("0.0042")).toBeInTheDocument();
    expect(screen.getByText("34.50%")).toBeInTheDocument();
    expect(screen.getByText("0.127")).toBeInTheDocument();
  });

  it("renders scenario selector buttons", () => {
    render(<MacroEnvironmentPanel indices={indices} />);
    expect(screen.getByTestId("scenario-baseline")).toBeInTheDocument();
    expect(screen.getByTestId("scenario-carbon_tax")).toBeInTheDocument();
    expect(screen.getByTestId("scenario-green_transition")).toBeInTheDocument();
  });

  it("selects scenario on click", () => {
    const onSimulate = vi.fn();
    render(<MacroEnvironmentPanel indices={indices} onSimulate={onSimulate} />);
    fireEvent.click(screen.getByTestId("scenario-carbon_tax"));
    expect(onSimulate).toHaveBeenCalledWith("carbon_tax");
  });

  it("shows scenario description", () => {
    render(<MacroEnvironmentPanel indices={indices} />);
    expect(screen.getByText("Current trajectory")).toBeInTheDocument();
  });
});
