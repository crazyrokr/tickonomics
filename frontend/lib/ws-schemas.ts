import { z } from "zod";

/**
 * Runtime-validated schemas for inbound WebSocket frames. The backend broadcasts JSON over
 * /ws/signals; these schemas replace blind casts so a malformed frame is discarded instead of
 * crashing the render path or producing a phantom signal.
 */
export const signalStatusCodeSchema = z.enum([
  "ACTIONABLE",
  "SPECULATIVE_STALE_MACRO",
  "COST_EXCEEDS_EXPECTED_MOVE",
  "COOLDOWN",
  "INSUFFICIENT_DATA",
]);

export const signalDirectionSchema = z.enum(["LONG", "SHORT", "NEUTRAL"]);

export const signalMarkerSchema = z.object({
  id: z.string(),
  timestamp: z.string(),
  statusCode: signalStatusCodeSchema,
  direction: signalDirectionSchema,
  symbol: z.string(),
  iliValue: z.number(),
});

/** Frame shape pushed by the backend on the /ws/signals channel. */
export const signalFrameSchema = z.object({
  signal: signalMarkerSchema,
});
