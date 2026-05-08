import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ModelTournamentPanel } from "@/components/panels/ModelTournamentPanel";

const mockModels = [
  { name: "ILI-XGB", sharpe: 1.45, bestRegime: "Trending" },
  { name: "ILI-LSTM", sharpe: 1.20, bestRegime: "Volatile" },
  { name: "ILI-Transformer", sharpe: 0.85, bestRegime: "Mean-Reverting" },
];

const mockShapData = [
  { feature: "bid_ask_spread", importance: 0.32 },
  { feature: "volume_imbalance", importance: 0.25 },
  { feature: "realized_vol", importance: 0.18 },
];

describe("ModelTournamentPanel", () => {
  const defaultProps = {
    models: mockModels,
    shapData: mockShapData,
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<ModelTournamentPanel {...defaultProps} isLoading />);

    expect(screen.queryByText("Model Tournament")).not.toBeInTheDocument();
    expect(screen.queryByTestId("model-cards")).not.toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows heading and model cards", () => {
    render(<ModelTournamentPanel {...defaultProps} />);

    expect(screen.getByText("Model Tournament")).toBeInTheDocument();
    expect(screen.getByTestId("model-cards")).toBeInTheDocument();
    expect(screen.getByTestId("model-card-ILI-XGB")).toBeInTheDocument();
    expect(screen.getByTestId("model-card-ILI-LSTM")).toBeInTheDocument();
    expect(screen.getByTestId("model-card-ILI-Transformer")).toBeInTheDocument();
  });

  it("Given valid models, When rendered, Then shows Sharpe values and best regime for each model", () => {
    render(<ModelTournamentPanel {...defaultProps} />);

    expect(screen.getByText("1.45")).toBeInTheDocument();
    expect(screen.getByText("1.20")).toBeInTheDocument();
    expect(screen.getByText("0.85")).toBeInTheDocument();
    expect(screen.getByText("Trending")).toBeInTheDocument();
    expect(screen.getByText("Volatile")).toBeInTheDocument();
    expect(screen.getByText("Mean-Reverting")).toBeInTheDocument();
  });

  it("Given model with highest Sharpe, When rendered, Then marks it as Best", () => {
    render(<ModelTournamentPanel {...defaultProps} />);

    const bestLabels = screen.getAllByText("Best");
    expect(bestLabels.length).toBe(1);
  });

  it("Given SHAP data, When rendered, Then shows SHAP bars section", () => {
    render(<ModelTournamentPanel {...defaultProps} />);

    expect(screen.getByTestId("shap-bars")).toBeInTheDocument();
    expect(screen.getByTestId("shap-bid_ask_spread")).toBeInTheDocument();
    expect(screen.getByTestId("shap-volume_imbalance")).toBeInTheDocument();
    expect(screen.getByTestId("shap-realized_vol")).toBeInTheDocument();
  });

  it("Given SHAP data, When rendered, Then shows feature importance values", () => {
    render(<ModelTournamentPanel {...defaultProps} />);

    expect(screen.getByText("0.320")).toBeInTheDocument();
    expect(screen.getByText("0.250")).toBeInTheDocument();
    expect(screen.getByText("0.180")).toBeInTheDocument();
  });

  it("Given empty SHAP data, When rendered, Then does not show SHAP bars section", () => {
    render(<ModelTournamentPanel models={mockModels} shapData={[]} />);

    expect(screen.queryByTestId("shap-bars")).not.toBeInTheDocument();
  });

  it("Given empty models, When rendered, Then still shows heading", () => {
    render(<ModelTournamentPanel models={[]} shapData={[]} />);

    expect(screen.getByText("Model Tournament")).toBeInTheDocument();
  });
});
