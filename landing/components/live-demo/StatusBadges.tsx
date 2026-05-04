import Badge from "@/components/shared/Badge";
import Tooltip from "@/components/shared/Tooltip";
import type {
  IliHistoryPoint,
  RegimeStatus,
  ParticipationStatus,
} from "@/lib/types";

interface StatusBadgesProps {
  iliData?: IliHistoryPoint[];
  regime?: RegimeStatus;
  anomalyDetected?: boolean;
  participation?: ParticipationStatus;
}

function iliVariant(
  status: string
): "green" | "amber" | "red" {
  if (status === "VALID") return "green";
  if (status === "DEGRADED_COMPONENT_STALE") return "amber";
  return "red";
}

function regimeVariant(
  r: RegimeStatus
): "green" | "amber" | "red" | "gray" {
  const map: Record<RegimeStatus, "green" | "amber" | "red" | "gray"> = {
    LOW_VOL: "green",
    NORMAL: "green",
    HIGH_VOL: "amber",
    EXOGENOUS_SHOCK: "red",
  };
  return map[r];
}

export default function StatusBadges({
  iliData,
  regime,
  anomalyDetected,
  participation,
}: StatusBadgesProps) {
  const latestIli = iliData?.[iliData.length - 1];
  const iliStatus = latestIli?.data_status ?? "VALID";
  const regimeLabel = regime ?? "NORMAL";
  const participationLabel = participation ?? "ADMISSIBLE";

  return (
    <div className="flex flex-wrap gap-3">
      <Tooltip text="Interbank Liquidity Index status">
        <Badge variant={iliVariant(iliStatus)} aria-label={`ILI status: ${iliStatus}`}>
          ILI: {iliStatus.replace(/_/g, " ")}
        </Badge>
      </Tooltip>

      <Tooltip text="Current market volatility regime">
        <Badge variant={regimeVariant(regimeLabel)} aria-label={`Regime: ${regimeLabel}`}>
          Regime: {regimeLabel.replace(/_/g, " ")}
        </Badge>
      </Tooltip>

      <Tooltip text="Whether an anomaly was detected in recent data">
        <Badge variant={anomalyDetected ? "red" : "green"} aria-label={`Anomaly: ${anomalyDetected ? "detected" : "none"}`}>
          Anomaly: {anomalyDetected ? "Detected" : "None"}
        </Badge>
      </Tooltip>

      <Tooltip text="Whether the system is admissible for signal generation">
        <Badge variant={participationLabel === "ADMISSIBLE" ? "green" : "amber"} aria-label={`Participation: ${participationLabel}`}>
          Participation: {participationLabel}
        </Badge>
      </Tooltip>
    </div>
  );
}
