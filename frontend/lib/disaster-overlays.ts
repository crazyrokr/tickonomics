export interface DisasterEvent {
  id: string;
  timestamp: string;
  type: string;
  severity: number;
  magnitude?: string;
  location?: string;
}

export interface DisasterMarker {
  time: string;
  position: "aboveBar";
  color: string;
  shape: "square";
  text: string;
  disaster: DisasterEvent;
}

export function disasterEventsToMarkers(
  events: DisasterEvent[],
  visible: boolean,
): DisasterMarker[] {
  if (!visible) return [];
  return events.map((e) => ({
    time: e.timestamp,
    position: "aboveBar" as const,
    color: e.severity >= 8 ? "var(--color-ili-red)" : "var(--color-ili-amber)",
    shape: "square" as const,
    text: `⚠ ${e.type}`,
    disaster: e,
  }));
}
