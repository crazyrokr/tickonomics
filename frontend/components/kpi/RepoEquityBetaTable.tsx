"use client";

import type { RepoEquityBeta } from "@/types/api";

interface RepoEquityBetaTableProps {
  data?: RepoEquityBeta[];
  isLoading?: boolean;
}

function getBetaColorClass(beta: number): string {
  if (beta > 1) return "text-ili-red";
  if (beta < 1) return "text-ili-green";
  return "text-muted";
}

export function RepoEquityBetaTable({ data, isLoading }: RepoEquityBetaTableProps) {
  if (isLoading || !data) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/2 mb-3" />
        <div className="space-y-2">
          <div className="h-6 bg-border rounded" />
          <div className="h-6 bg-border rounded" />
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Repo / Equity Beta</h3>
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border">
            <th className="text-left py-1 text-muted font-medium">Symbol</th>
            <th className="text-right py-1 text-muted font-medium">Beta</th>
          </tr>
        </thead>
        <tbody>
          {data.map((row) => (
            <tr key={row.symbol} className="border-b border-border last:border-0">
              <td className="py-1.5">{row.symbol}</td>
              <td
                className={`py-1.5 text-right font-mono ${getBetaColorClass(row.beta)}`}
                data-testid={`beta-${row.symbol}`}
              >
                {row.beta.toFixed(3)}
              </td>
            </tr>
          ))}
          {data.length === 0 && (
            <tr>
              <td colSpan={2} className="py-2 text-center text-muted">
                No data available
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
