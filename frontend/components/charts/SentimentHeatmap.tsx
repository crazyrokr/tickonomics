"use client";

interface SentimentRow {
  symbol: string;
  lexiconScore: number;
  finbertScore: number;
  source: string;
  timestamp: string;
}

interface SentimentHeatmapProps {
  data: SentimentRow[];
  isLoading?: boolean;
}

function getSentimentColor(score: number): string {
  if (score >= 0.3) return "var(--color-ili-green)";
  if (score <= -0.3) return "var(--color-ili-red)";
  return "var(--color-ili-amber)";
}

function getSentimentBg(score: number): string {
  if (score >= 0.3) return "bg-ili-green/15";
  if (score <= -0.3) return "bg-ili-red/15";
  return "bg-ili-amber/15";
}

export function SentimentHeatmap({ data, isLoading }: SentimentHeatmapProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-48 bg-border rounded" />
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <h3 className="text-sm font-medium text-muted mb-3">Sentiment Analysis</h3>
        <p className="text-sm text-muted text-center py-8">No sentiment data available</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">Sentiment Analysis</h3>

      <div className="overflow-x-auto">
        <table className="w-full text-sm" data-testid="sentiment-grid">
          <thead>
            <tr>
              <th className="text-left text-xs text-muted font-medium pb-2 pr-3">Symbol</th>
              <th className="text-center text-xs text-muted font-medium pb-2">
                Lexicon <span className="opacity-60">(fast)</span>
              </th>
              <th className="text-center text-xs text-muted font-medium pb-2">
                FinBERT <span className="opacity-60">(accurate)</span>
              </th>
              <th className="text-right text-xs text-muted font-medium pb-2">Source</th>
            </tr>
          </thead>
          <tbody>
            {data.map((row) => (
              <tr key={`${row.symbol}-${row.timestamp}`} className="border-t border-border/50" data-testid={`sentiment-${row.symbol}`}>
                <td className="py-2 pr-3 font-medium">{row.symbol}</td>
                <td className="py-2 text-center">
                  <span
                    className={`inline-block px-2 py-0.5 rounded text-xs font-mono ${getSentimentBg(row.lexiconScore)}`}
                    style={{ color: getSentimentColor(row.lexiconScore) }}
                  >
                    {row.lexiconScore.toFixed(2)}
                  </span>
                </td>
                <td className="py-2 text-center">
                  <span
                    className={`inline-block px-2 py-0.5 rounded text-xs font-mono ${getSentimentBg(row.finbertScore)}`}
                    style={{ color: getSentimentColor(row.finbertScore) }}
                  >
                    {row.finbertScore.toFixed(2)}
                  </span>
                </td>
                <td className="py-2 text-right text-xs text-muted">{row.source}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex items-center gap-4 mt-3 text-xs text-muted">
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded bg-ili-red/30" />
          <span>Negative</span>
        </div>
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded bg-ili-amber/30" />
          <span>Neutral</span>
        </div>
        <div className="flex items-center gap-1">
          <span className="w-3 h-3 rounded bg-ili-green/30" />
          <span>Positive</span>
        </div>
      </div>
    </div>
  );
}
