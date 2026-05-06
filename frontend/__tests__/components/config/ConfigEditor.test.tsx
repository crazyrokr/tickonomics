import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ConfigEditor } from "@/components/config/ConfigEditor";
import type { ConfigEntry } from "@/types/api";

const initialConfig: ConfigEntry[] = [
  { key: "threshold", value: 0.5 },
  { key: "lookback_days", value: 252 },
];

describe("ConfigEditor", () => {
  it("renders read-only view for non-admin", () => {
    render(
      <ConfigEditor initialConfig={initialConfig} onSubmit={vi.fn()} isAdmin={false} />,
    );
    expect(screen.getByTestId("config-readonly")).toBeInTheDocument();
    expect(screen.queryByTestId("config-textarea")).not.toBeInTheDocument();
    expect(screen.getByText(/Read-only view/)).toBeInTheDocument();
  });

  it("renders textarea for admin", () => {
    render(
      <ConfigEditor initialConfig={initialConfig} onSubmit={vi.fn()} isAdmin={true} />,
    );
    expect(screen.getByTestId("config-textarea")).toBeInTheDocument();
    expect(screen.getByTestId("config-submit")).toBeInTheDocument();
  });

  it("pre-fills textarea with initial config as JSON", () => {
    render(
      <ConfigEditor initialConfig={initialConfig} onSubmit={vi.fn()} isAdmin={true} />,
    );
    const textarea = screen.getByTestId("config-textarea") as HTMLTextAreaElement;
    const parsed = JSON.parse(textarea.value);
    expect(parsed.threshold).toBe(0.5);
    expect(parsed.lookback_days).toBe(252);
  });

  it("disables submit when no changes", () => {
    render(
      <ConfigEditor initialConfig={initialConfig} onSubmit={vi.fn()} isAdmin={true} />,
    );
    expect(screen.getByTestId("config-submit")).toBeDisabled();
  });

  it("enables submit when text is modified", () => {
    render(
      <ConfigEditor initialConfig={initialConfig} onSubmit={vi.fn()} isAdmin={true} />,
    );
    fireEvent.change(screen.getByTestId("config-textarea"), {
      target: { value: '{"threshold": 0.7, "lookback_days": 252}' },
    });
    expect(screen.getByTestId("config-submit")).not.toBeDisabled();
  });

  it("shows Invalid JSON for malformed input", () => {
    render(
      <ConfigEditor initialConfig={initialConfig} onSubmit={vi.fn()} isAdmin={true} />,
    );
    fireEvent.change(screen.getByTestId("config-textarea"), {
      target: { value: "not json" },
    });
    expect(screen.getByText("Invalid JSON")).toBeInTheDocument();
    expect(screen.getByTestId("config-submit")).toBeDisabled();
  });

  it("calls onSubmit with parsed entries", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ConfigEditor initialConfig={initialConfig} onSubmit={onSubmit} isAdmin={true} />,
    );
    fireEvent.change(screen.getByTestId("config-textarea"), {
      target: { value: '{"threshold": 0.8, "lookback_days": 100}' },
    });
    fireEvent.click(screen.getByTestId("config-submit"));

    await vi.waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith([
        { key: "threshold", value: 0.8 },
        { key: "lookback_days", value: 100 },
      ]);
    });
  });

  it("shows diff view when toggled", () => {
    render(
      <ConfigEditor initialConfig={initialConfig} onSubmit={vi.fn()} isAdmin={true} />,
    );
    fireEvent.change(screen.getByTestId("config-textarea"), {
      target: { value: '{"threshold": 0.7, "lookback_days": 252}' },
    });
    fireEvent.click(screen.getByTestId("toggle-diff"));
    expect(screen.getByTestId("config-diff")).toBeInTheDocument();
  });
});
