import Badge from "@/components/shared/Badge";
import type { SignalStatusCode } from "@/lib/types";

interface SignalStatusBadgeProps {
  status: SignalStatusCode;
}

const statusConfig: Record<
  SignalStatusCode,
  { variant: "green" | "amber" | "red" | "gray"; label: string }
> = {
  ACTIONABLE: { variant: "green", label: "Actionable" },
  SPECULATIVE_STALE_MACRO: { variant: "amber", label: "Stale Macro" },
  COST_EXCEEDS_EXPECTED_MOVE: { variant: "red", label: "Cost > Move" },
  COOLDOWN: { variant: "gray", label: "Cooldown" },
  INSUFFICIENT_DATA: { variant: "gray", label: "Insufficient Data" },
};

export default function SignalStatusBadge({ status }: SignalStatusBadgeProps) {
  const config = statusConfig[status];

  return (
    <Badge variant={config.variant} aria-label={`Signal status: ${status}`}>
      {config.label}
    </Badge>
  );
}
