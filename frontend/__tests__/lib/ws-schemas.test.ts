import { describe, it, expect } from "vitest";
import { signalFrameSchema } from "@/lib/ws-schemas";

describe("signalFrameSchema", () => {
  const validFrame = {
    signal: {
      id: "sig-1",
      timestamp: "2026-05-30T12:00:00Z",
      statusCode: "ACTIONABLE",
      direction: "LONG",
      symbol: "SPY",
      iliValue: 0.8,
    },
  };

  it("parses a well-formed signal frame", () => {
    const parsed = signalFrameSchema.safeParse(validFrame);
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.data.signal.symbol).toBe("SPY");
    }
  });

  it("rejects a frame with an unknown status code", () => {
    const parsed = signalFrameSchema.safeParse({
      ...validFrame,
      signal: { ...validFrame.signal, statusCode: "BOGUS" },
    });
    expect(parsed.success).toBe(false);
  });

  it("rejects a malformed frame (missing signal wrapper)", () => {
    const parsed = signalFrameSchema.safeParse({ foo: "bar" });
    expect(parsed.success).toBe(false);
  });

  it("rejects a non-object payload", () => {
    const parsed = signalFrameSchema.safeParse("not-an-object");
    expect(parsed.success).toBe(false);
  });
});
