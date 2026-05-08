import { describe, it, expect } from "vitest";
import { disasterEventsToMarkers, type DisasterEvent } from "@/lib/disaster-overlays";

describe("disasterEventsToMarkers", () => {
  const events: DisasterEvent[] = [
    { id: "evt-1", timestamp: "2026-05-30T12:00:00Z", type: "Earthquake", severity: 9, magnitude: "7.2", location: "Tokyo" },
    { id: "evt-2", timestamp: "2026-05-29T08:00:00Z", type: "Hurricane", severity: 5, location: "Gulf Coast" },
  ];

  it("converts events to markers when visible", () => {
    const markers = disasterEventsToMarkers(events, true);
    expect(markers).toHaveLength(2);
    expect(markers[0].text).toContain("Earthquake");
    expect(markers[0].disaster.severity).toBe(9);
  });

  it("returns empty array when not visible", () => {
    const markers = disasterEventsToMarkers(events, false);
    expect(markers).toHaveLength(0);
  });

  it("assigns red color for high severity events", () => {
    const markers = disasterEventsToMarkers(events, true);
    expect(markers[0].color).toContain("ili-red");
  });

  it("assigns amber color for moderate severity events", () => {
    const markers = disasterEventsToMarkers(events, true);
    expect(markers[1].color).toContain("ili-amber");
  });

  it("preserves disaster data in marker", () => {
    const markers = disasterEventsToMarkers(events, true);
    expect(markers[0].disaster.magnitude).toBe("7.2");
    expect(markers[0].disaster.location).toBe("Tokyo");
  });
});
