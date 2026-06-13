interface DemoBadgeProps {
  variant: "virtual" | "dislocated" | "degraded";
  active?: boolean;
}

const VARIANT_STYLES: Record<DemoBadgeProps["variant"], { label: string; className: string }> = {
  virtual: {
    label: "Virtual Trading",
    className: "border-sky-500/40 bg-sky-500/10 text-sky-200",
  },
  dislocated: {
    label: "Proxy Dislocated",
    className: "border-rose-500/40 bg-rose-500/10 text-rose-200",
  },
  degraded: {
    label: "Degraded Data",
    className: "border-orange-500/40 bg-orange-500/10 text-orange-200",
  },
};

export function DemoBadge({ variant, active = true }: DemoBadgeProps) {
  if (!active) {
    return null;
  }
  const style = VARIANT_STYLES[variant];
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs ${style.className}`}
    >
      {style.label}
    </span>
  );
}
