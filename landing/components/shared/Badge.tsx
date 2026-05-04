import type { ReactNode } from "react";

type BadgeVariant = "green" | "amber" | "red" | "blue" | "gray";

interface BadgeProps {
  variant?: BadgeVariant;
  children: ReactNode;
  "aria-label"?: string;
}

const variantClasses: Record<BadgeVariant, string> = {
  green: "bg-emerald-100 text-emerald-800",
  amber: "bg-amber-100 text-amber-800",
  red: "bg-red-100 text-red-800",
  blue: "bg-blue-100 text-blue-800",
  gray: "bg-zinc-100 text-zinc-700",
};

export default function Badge({
  variant = "gray",
  children,
  "aria-label": ariaLabel,
}: BadgeProps) {
  return (
    <span
      role="status"
      aria-label={ariaLabel}
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${variantClasses[variant]}`}
    >
      {children}
    </span>
  );
}
