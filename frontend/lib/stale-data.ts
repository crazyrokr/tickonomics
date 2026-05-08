export interface StaleDataStatus {
  isStale: boolean;
  lastFreshTimestamp: string | null;
  source: string;
}

export function parseDataAgeHeader(value: string | null): StaleDataStatus {
  if (!value) return { isStale: false, lastFreshTimestamp: null, source: "" };
  return {
    isStale: value.toUpperCase() === "STALE",
    lastFreshTimestamp: null,
    source: "",
  };
}

export function getElapsedMinutes(timestamp: string | null): number | null {
  if (!timestamp) return null;
  const then = new Date(timestamp).getTime();
  const now = Date.now();
  return Math.max(0, Math.floor((now - then) / 60000));
}

export function formatElapsedTime(minutes: number): string {
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return mins > 0 ? `${hours}h ${mins}m ago` : `${hours}h ago`;
}
