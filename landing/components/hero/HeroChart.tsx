export default function HeroChart() {
  const points = generateWavePoints(960, 400, 30);

  return (
    <svg
      viewBox="0 0 960 400"
      preserveAspectRatio="none"
      className="h-full w-full"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="heroFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.3" />
          <stop offset="100%" stopColor="#3b82f6" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path
        d={points}
        fill="url(#heroFill)"
        className="hero-chart-path"
      />
    </svg>
  );
}

function generateWavePoints(
  width: number,
  height: number,
  segments: number
): string {
  const step = width / segments;
  const mid = height / 2;
  const parts: string[] = [`M 0 ${mid}`];

  for (let i = 1; i <= segments; i++) {
    const x = i * step;
    const y = mid + Math.sin((i / segments) * Math.PI * 4) * (height * 0.25);
    parts.push(`L ${x.toFixed(1)} ${y.toFixed(1)}`);
  }

  parts.push(`L ${width} ${height}`, `L 0 ${height}`, "Z");
  return parts.join(" ");
}
