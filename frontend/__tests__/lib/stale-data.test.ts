import { describe, it, expect } from "vitest";
import { parseDataAgeHeader, getElapsedMinutes, formatElapsedTime } from "@/lib/stale-data";

describe("parseDataAgeHeader", () => {
  it("returns not stale when header is null", () => {
    expect(parseDataAgeHeader(null).isStale).toBe(false);
  });

  it("detects STALE header", () => {
    expect(parseDataAgeHeader("STALE").isStale).toBe(true);
  });

  it("detects stale header case-insensitively", () => {
    expect(parseDataAgeHeader("stale").isStale).toBe(true);
  });

  it("returns not stale for FRESH header", () => {
    expect(parseDataAgeHeader("FRESH").isStale).toBe(false);
  });
});

describe("getElapsedMinutes", () => {
  it("returns null for null timestamp", () => {
    expect(getElapsedMinutes(null)).toBeNull();
  });

  it("returns elapsed minutes from timestamp", () => {
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60000).toISOString();
    expect(getElapsedMinutes(fiveMinutesAgo)).toBeGreaterThanOrEqual(4);
    expect(getElapsedMinutes(fiveMinutesAgo)).toBeLessThanOrEqual(6);
  });
});

describe("formatElapsedTime", () => {
  it("formats 'just now' for < 1 minute", () => {
    expect(formatElapsedTime(0)).toBe("just now");
  });

  it("formats minutes", () => {
    expect(formatElapsedTime(5)).toBe("5m ago");
  });

  it("formats hours and minutes", () => {
    expect(formatElapsedTime(125)).toBe("2h 5m ago");
  });

  it("formats hours only", () => {
    expect(formatElapsedTime(120)).toBe("2h ago");
  });
});
