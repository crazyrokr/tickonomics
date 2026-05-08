import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SignalInformativenessPanel } from "@/components/panels/SignalInformativenessPanel";

const mockData = [
  { signalId: "SIG-001", pjr: 0.75, car: 0.62 },
  { signalId: "SIG-002", pjr: 0.35, car: 0.28 },
  { signalId: "SIG-003", pjr: 0.50, car: 0.48 },
];

describe("SignalInformativenessPanel", () => {
  it("Given loading state, When rendered, Then shows loading skeleton without content", () => {
    render(<SignalInformativenessPanel data={[]} isLoading />);

    expect(screen.queryByText("Signal Informativeness")).not.toBeInTheDocument();
    expect(screen.queryByTestId("signal-table")).not.toBeInTheDocument();
  });

  it("Given empty data, When rendered, Then shows empty state message", () => {
    render(<SignalInformativenessPanel data={[]} />);

    expect(screen.getByText("Signal Informativeness")).toBeInTheDocument();
    expect(screen.getByText("No signal data available")).toBeInTheDocument();
    expect(screen.queryByTestId("signal-table")).not.toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows the signal table with all rows", () => {
    render(<SignalInformativenessPanel data={mockData} />);

    expect(screen.getByTestId("signal-table")).toBeInTheDocument();
    expect(screen.getByTestId("signal-SIG-001")).toBeInTheDocument();
    expect(screen.getByTestId("signal-SIG-002")).toBeInTheDocument();
    expect(screen.getByTestId("signal-SIG-003")).toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then displays PJR and CAR values in correct format", () => {
    render(<SignalInformativenessPanel data={mockData} />);

    expect(screen.getByText("0.750")).toBeInTheDocument();
    expect(screen.getByText("0.620")).toBeInTheDocument();
    expect(screen.getByText("0.350")).toBeInTheDocument();
  });

  it("Given signal with high PJR (>0.6), When rendered, Then classifies as Informed", () => {
    render(<SignalInformativenessPanel data={[mockData[0]]} />);

    expect(screen.getByText("Informed")).toBeInTheDocument();
  });

  it("Given signal with low PJR (<0.4), When rendered, Then classifies as Noise", () => {
    render(<SignalInformativenessPanel data={[mockData[1]]} />);

    expect(screen.getByText("Noise")).toBeInTheDocument();
  });

  it("Given signal with mid PJR (0.4-0.6), When rendered, Then classifies as Mixed", () => {
    render(<SignalInformativenessPanel data={[mockData[2]]} />);

    expect(screen.getByText("Mixed")).toBeInTheDocument();
  });

  it("Given valid data, When rendered, Then shows column headers", () => {
    render(<SignalInformativenessPanel data={mockData} />);

    expect(screen.getByText("Signal")).toBeInTheDocument();
    expect(screen.getByText("PJR")).toBeInTheDocument();
    expect(screen.getByText("CAR")).toBeInTheDocument();
    expect(screen.getByText("Classification")).toBeInTheDocument();
  });
});
