import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MonetaryPolicyPanel } from "@/components/panels/MonetaryPolicyPanel";

describe("MonetaryPolicyPanel", () => {
  const defaultProps = {
    balanceSheet: { expanding: true, trend: 15.3 },
    policyRates: { fedFunds: 5.25, iorb: 5.15, sofrSpread: 0.08 },
    yieldCurveShape: "normal",
  };

  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<MonetaryPolicyPanel {...defaultProps} isLoading />);

    expect(screen.queryByText("Monetary Policy Overview")).not.toBeInTheDocument();
    expect(screen.queryByTestId("balance-sheet")).not.toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then shows heading and all sections", () => {
    render(<MonetaryPolicyPanel {...defaultProps} />);

    expect(screen.getByText("Monetary Policy Overview")).toBeInTheDocument();
    expect(screen.getByTestId("balance-sheet")).toBeInTheDocument();
    expect(screen.getByTestId("policy-rates")).toBeInTheDocument();
    expect(screen.getByTestId("yield-curve")).toBeInTheDocument();
  });

  it("Given expanding balance sheet, When rendered, Then shows Expanding label", () => {
    render(<MonetaryPolicyPanel {...defaultProps} />);

    expect(screen.getByTestId("bs-direction")).toHaveTextContent("Expanding");
  });

  it("Given contracting balance sheet, When rendered, Then shows Contracting label", () => {
    render(<MonetaryPolicyPanel {...defaultProps} balanceSheet={{ expanding: false, trend: -12.5 }} />);

    expect(screen.getByTestId("bs-direction")).toHaveTextContent("Contracting");
  });

  it("Given positive trend, When rendered, Then shows trend with plus sign", () => {
    render(<MonetaryPolicyPanel {...defaultProps} />);

    expect(screen.getByText("+15.30B")).toBeInTheDocument();
  });

  it("Given negative trend, When rendered, Then shows trend with minus sign", () => {
    render(<MonetaryPolicyPanel {...defaultProps} balanceSheet={{ expanding: false, trend: -8.5 }} />);

    expect(screen.getByText("-8.50B")).toBeInTheDocument();
  });

  it("Given valid props, When rendered, Then displays policy rates", () => {
    render(<MonetaryPolicyPanel {...defaultProps} />);

    expect(screen.getByText("Policy Rates")).toBeInTheDocument();
    expect(screen.getByText("Fed Funds Rate")).toBeInTheDocument();
    expect(screen.getByText("IORB")).toBeInTheDocument();
    expect(screen.getByText("SOFR Spread")).toBeInTheDocument();
    expect(screen.getByText("5.25%")).toBeInTheDocument();
    expect(screen.getByText("5.15%")).toBeInTheDocument();
    expect(screen.getByText("0.08%")).toBeInTheDocument();
  });

  it("Given normal yield curve, When rendered, Then shows Normal label", () => {
    render(<MonetaryPolicyPanel {...defaultProps} />);

    expect(screen.getByTestId("curve-shape")).toHaveTextContent("Normal");
  });

  it("Given inverted yield curve, When rendered, Then shows Inverted label", () => {
    render(<MonetaryPolicyPanel {...defaultProps} yieldCurveShape="inverted" />);

    expect(screen.getByTestId("curve-shape")).toHaveTextContent("Inverted");
  });

  it("Given flat yield curve, When rendered, Then shows Flat label", () => {
    render(<MonetaryPolicyPanel {...defaultProps} yieldCurveShape="flat" />);

    expect(screen.getByTestId("curve-shape")).toHaveTextContent("Flat");
  });
});
