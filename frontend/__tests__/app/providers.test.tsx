import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { useQuery } from "@tanstack/react-query";
import { Providers } from "@/app/providers";

function ChildUsingQuery() {
  const { data, isLoading } = useQuery({
    queryKey: ["probe"],
    queryFn: () => Promise.resolve("resolved"),
  });
  return (
    <span data-testid="probe">{isLoading ? "loading" : data ?? "empty"}</span>
  );
}

describe("Providers", () => {
  it("provides a QueryClient so descendant useQuery hooks resolve without throwing", async () => {
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    render(
      <Providers>
        <ChildUsingQuery />
      </Providers>,
    );
    await waitFor(() => {
      expect(screen.getByTestId("probe")).toHaveTextContent("resolved");
    });
    expect(errorSpy).not.toHaveBeenCalled();
    errorSpy.mockRestore();
  });
});
