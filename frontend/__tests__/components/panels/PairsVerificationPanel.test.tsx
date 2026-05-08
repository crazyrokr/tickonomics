import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PairsVerificationPanel } from "@/components/panels/PairsVerificationPanel";

const mockSpreadData = [
  { timestamp: "2026-05-28T10:00:00Z", spread: 0.5 },
  { timestamp: "2026-05-29T10:00:00Z", spread: -0.3 },
  { timestamp: "2026-05-30T10:00:00Z", spread: 0.8 },
];

describe("PairsVerificationPanel", () => {
  const defaultProps = {
    pair: "AAPL/MSFT",
    distance: 1.2345,
    iliSignal: "LONG",
    pairsSignal: "LONG",
    formationEnd: "2026-06-15T00:00:00Z",
    data: mockSpreadData,
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<PairsVerificationPanel {...defaultProps} isLoading />);

    expect(screen.queryByText("Pairs Verification")).not.toBeInTheDocument();
    expect(screen.queryByTestId("pair-name")).not.toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows heading and pair name", () => {
    render(<PairsVerificationPanel {...defaultProps} />);

    expect(screen.getByText("Pairs Verification")).toBeInTheDocument();
    expect(screen.getByTestId("pair-name")).toHaveTextContent("AAPL/MSFT");
  });

  it("Given valid props, When rendered, Then displays the distance value", () => {
    render(<PairsVerificationPanel {...defaultProps} />);

    expect(screen.getByText("1.2345")).toBeInTheDocument();
  });

  it("Given matching signals, When rendered, Then does not show divergence badge", () => {
    render(<PairsVerificationPanel {...defaultProps} />);

    expect(screen.queryByTestId("divergence-badge")).not.toBeInTheDocument();
  });

  it("Given diverging signals, When rendered, Then shows the divergence badge", () => {
    render(<PairsVerificationPanel {...defaultProps} iliSignal="LONG" pairsSignal="SHORT" />);

    expect(screen.getByTestId("divergence-badge")).toHaveTextContent("Signals Diverge");
  });

  it("Given valid props with formationEnd, When rendered, Then shows formation end date", () => {
    render(<PairsVerificationPanel {...defaultProps} />);

    expect(screen.getByText("Formation End")).toBeInTheDocument();
    expect(screen.getByText("ILI Signal")).toBeInTheDocument();
    expect(screen.getByText("Pairs Signal")).toBeInTheDocument();
  });

  it("Given null formationEnd, When rendered, Then shows Active label", () => {
    render(<PairsVerificationPanel {...defaultProps} formationEnd={null} />);

    expect(screen.getByText("Active")).toBeInTheDocument();
  });

  it("Given spread data, When rendered, Then shows the spread bars container", () => {
    render(<PairsVerificationPanel {...defaultProps} />);

    expect(screen.getByTestId("spread-bars")).toBeInTheDocument();
  });

  it("Given empty spread data, When rendered, Then does not show spread bars section", () => {
    render(<PairsVerificationPanel {...defaultProps} data={[]} />);

    expect(screen.queryByTestId("spread-bars")).not.toBeInTheDocument();
  });
});
