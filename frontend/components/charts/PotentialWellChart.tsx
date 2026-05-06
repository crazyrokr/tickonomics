"use client";

interface PotentialWellPoint {
  price: number;
  potential: number;
}

interface PotentialWellChartProps {
  data: PotentialWellPoint[];
  currentPrice: number;
  minima: number[];
  isLoading?: boolean;
}

export function PotentialWellChart({
  data,
  currentPrice,
  minima = [],
  isLoading,
}: PotentialWellChartProps) {
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface animate-pulse">
        <div className="h-4 bg-border rounded w-1/3 mb-3" />
        <div className="h-48 bg-border rounded" />
      </div>
    );
  }

  if (data.length < 2) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <h3 className="text-sm font-medium text-muted mb-3">QED Potential Well</h3>
        <p className="text-sm text-muted text-center py-8">Insufficient data</p>
      </div>
    );
  }

  const width = 400;
  const height = 200;
  const padding = 20;

  const prices = data.map((d) => d.price);
  const potentials = data.map((d) => d.potential);
  const minPrice = Math.min(...prices);
  const maxPrice = Math.max(...prices);
  const minPotential = Math.min(...potentials);
  const maxPotential = Math.max(...potentials);
  const priceRange = maxPrice - minPrice || 1;
  const potentialRange = maxPotential - minPotential || 1;

  const toX = (p: number) => padding + ((p - minPrice) / priceRange) * (width - 2 * padding);
  const toY = (p: number) => height - padding - ((p - minPotential) / potentialRange) * (height - 2 * padding);

  const linePoints = data.map((d) => `${toX(d.price)},${toY(d.potential)}`).join(" ");

  const wellDepth = maxPotential - minPotential;
  const shallowThreshold = wellDepth * 0.3;

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <h3 className="text-sm font-medium text-muted mb-3">QED Potential Well</h3>

      <svg width={width} height={height} data-testid="potential-well-svg" className="w-full">
        {/* Potential curve */}
        <polyline
          points={linePoints}
          fill="none"
          stroke="var(--color-ili-blue)"
          strokeWidth="2"
        />

        {/* Well minima */}
        {minima.map((m, i) => {
          const minPotentialHere = data.reduce((closest, d) =>
            Math.abs(d.price - m) < Math.abs(closest.price - m) ? d : closest,
          ).potential;
          const depth = maxPotential - minPotentialHere;
          const isShallow = depth < shallowThreshold;

          return (
            <g key={`min-${i}`}>
              <circle
                cx={toX(m)}
                cy={toY(minPotentialHere)}
                r="4"
                fill={isShallow ? "var(--color-ili-amber)" : "var(--color-ili-green)"}
                data-testid={`minimum-${i}`}
              />
              {isShallow && (
                <text
                  x={toX(m) + 8}
                  y={toY(minPotentialHere) - 8}
                  fontSize="10"
                  fill="var(--color-ili-amber)"
                >
                  transition risk
                </text>
              )}
            </g>
          );
        })}

        {/* Current price marker */}
        <circle
          cx={toX(currentPrice)}
          cy={toY(data.reduce((closest, d) =>
            Math.abs(d.price - currentPrice) < Math.abs(closest.price - currentPrice) ? d : closest,
          ).potential)}
          r="6"
          fill="var(--color-foreground)"
          stroke="white"
          strokeWidth="2"
          data-testid="current-price-marker"
        />
      </svg>

      <p className="text-xs text-muted mt-1">
        Price position: {currentPrice.toFixed(2)} | Wells: {minima.length}
      </p>
    </div>
  );
}
