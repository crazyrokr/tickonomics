import type { DemoTrade } from "@/lib/api";

interface TradeHistoryProps {
  trades: DemoTrade[];
  isLoading: boolean;
}

export function TradeHistory({ trades, isLoading }: TradeHistoryProps) {
  if (isLoading) {
    return <div className="text-sm text-slate-400">Loading trades…</div>;
  }
  if (trades.length === 0) {
    return <div className="text-sm text-slate-400">No trades recorded yet.</div>;
  }

  return (
    <div className="overflow-x-auto rounded-md border border-slate-700">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-900/70 text-left text-xs uppercase text-slate-400">
          <tr>
            <th className="px-3 py-2">Symbol</th>
            <th className="px-3 py-2">Direction</th>
            <th className="px-3 py-2">Quantity</th>
            <th className="px-3 py-2">Fill Price</th>
            <th className="px-3 py-2">Realized P&amp;L</th>
            <th className="px-3 py-2">Executed</th>
          </tr>
        </thead>
        <tbody>
          {trades.map((trade) => (
            <tr key={trade.id} className="border-t border-slate-800">
              <td className="px-3 py-2 font-medium">{trade.symbol}</td>
              <td className="px-3 py-2">{trade.direction}</td>
              <td className="px-3 py-2">{trade.quantity.toFixed(4)}</td>
              <td className="px-3 py-2">{trade.fillPrice.toFixed(2)}</td>
              <td className={`px-3 py-2 ${(trade.realizedPnl ?? 0) >= 0 ? "text-emerald-400" : "text-rose-400"}`}>
                {(trade.realizedPnl ?? 0).toFixed(2)}
              </td>
              <td className="px-3 py-2 text-slate-400">
                {new Date(trade.executedAt).toLocaleString()}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
