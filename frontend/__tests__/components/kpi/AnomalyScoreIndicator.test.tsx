import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AnomalyScoreIndicator } from "@/components/kpi/AnomalyScoreIndicator";

describe("AnomalyScoreIndicator", () => {
  it("shows loading skeleton", () => {
    render(<AnomalyScoreIndicator mse={undefined} threshold={0.5} suspectAnomaly={false} isLoading />);
    expect(screen.queryByTestId("anomaly-indicator")).not.toBeInTheDocument();
  });

  it("renders MSE value and threshold", () => {
    render(<AnomalyScoreIndicator mse={0.2345} threshold={0.5} suspectAnomaly={false} />);
    expect(screen.getByText("0.2345")).toBeInTheDocument();
    expect(screen.getByText(/0.50 threshold/)).toBeInTheDocument();
  });

  it("shows SUSPECT badge when anomaly detected", () => {
    render(<AnomalyScoreIndicator mse={0.8} threshold={0.5} suspectAnomaly={true} />);
    expect(screen.getByTestId("anomaly-badge")).toHaveTextContent("SUSPECT");
  });

  it("hides SUSPECT badge when no anomaly", () => {
    render(<AnomalyScoreIndicator mse={0.2} threshold={0.5} suspectAnomaly={false} />);
    expect(screen.queryByTestId("anomaly-badge")).not.toBeInTheDocument();
  });

  it("shows amber border when anomaly suspected", () => {
    render(<AnomalyScoreIndicator mse={0.8} threshold={0.5} suspectAnomaly={true} />);
    expect(screen.getByTestId("anomaly-indicator").className).toContain("border-ili-amber");
  });

  it("renders sparkline from history", () => {
    const { container } = render(
      <AnomalyScoreIndicator mse={0.3} threshold={0.5} suspectAnomaly={false} history={[0.1, 0.2, 0.3, 0.4, 0.3]} />,
    );
    expect(container.querySelector("polyline")).toBeInTheDocument();
  });
});
