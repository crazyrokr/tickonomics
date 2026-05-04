import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { Sidebar } from "@/components/layout/Sidebar";

describe("Sidebar", () => {
  it("renders all navigation items", () => {
    render(<Sidebar />);
    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(screen.getByText("Charts")).toBeInTheDocument();
    expect(screen.getByText("KPIs")).toBeInTheDocument();
    expect(screen.getByText("Monitoring")).toBeInTheDocument();
    expect(screen.getByText("Config")).toBeInTheDocument();
  });

  it("renders Tickonomics brand", () => {
    render(<Sidebar />);
    expect(screen.getByText("Tickonomics")).toBeInTheDocument();
  });

  it("collapses when toggle button is clicked", () => {
    render(<Sidebar />);
    const button = screen.getByLabelText("Collapse sidebar");
    fireEvent.click(button);

    expect(screen.queryByText("Tickonomics")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Expand sidebar")).toBeInTheDocument();
  });

  it("expands after collapse", () => {
    render(<Sidebar />);
    fireEvent.click(screen.getByLabelText("Collapse sidebar"));
    fireEvent.click(screen.getByLabelText("Expand sidebar"));

    expect(screen.getByText("Tickonomics")).toBeInTheDocument();
    expect(screen.getByLabelText("Collapse sidebar")).toBeInTheDocument();
  });
});
