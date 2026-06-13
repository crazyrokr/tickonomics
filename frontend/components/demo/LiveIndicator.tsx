interface LiveIndicatorProps {
  live: boolean;
}

export function LiveIndicator({ live }: LiveIndicatorProps) {
  return (
    <span className="inline-flex items-center gap-2 text-xs font-medium">
      <span
        aria-label={live ? "system live" : "system idle"}
        className={`h-2 w-2 rounded-full ${live ? "animate-pulse bg-emerald-400" : "bg-slate-500"}`}
      />
      {live ? "Live" : "Idle"}
    </span>
  );
}
