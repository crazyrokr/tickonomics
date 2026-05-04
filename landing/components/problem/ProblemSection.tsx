import Section from "@/components/shared/Section";

const painPoints = [
  {
    title: "Liquidity Blind Spots",
    description:
      "Fed facility usage and reverse repo flows are scattered across FRED, H.4.1, and proprietary terminals. Most traders see them hours or days late.",
    icon: "🔍",
  },
  {
    title: "Lagging Indicators",
    description:
      "Traditional liquidity indices update weekly. By the time a stress signal appears, the move has already happened.",
    icon: "⏱️",
  },
  {
    title: "Manual Correlation Analysis",
    description:
      "Connecting funding market shifts to equity price action requires stitching dozens of data sources manually — error-prone and slow.",
    icon: "🔗",
  },
];

export default function ProblemSection() {
  return (
    <Section id="problem" className="mx-auto max-w-5xl px-6 py-20">
      <h2 className="text-center text-3xl font-bold tracking-tight text-zinc-900 sm:text-4xl">
        The Problem
      </h2>
      <p className="mx-auto mt-4 max-w-2xl text-center text-zinc-600">
        Funding market data is fragmented, delayed, and disconnected from
        equity signals.
      </p>

      <div className="mt-12 grid gap-8 sm:grid-cols-3">
        {painPoints.map((point) => (
          <div
            key={point.title}
            className="rounded-xl border border-zinc-200 bg-white p-6 shadow-sm transition hover:shadow-md"
          >
            <span className="text-3xl" role="img" aria-hidden="true">
              {point.icon}
            </span>
            <h3 className="mt-4 text-lg font-semibold text-zinc-900">
              {point.title}
            </h3>
            <p className="mt-2 text-sm leading-relaxed text-zinc-600">
              {point.description}
            </p>
          </div>
        ))}
      </div>
    </Section>
  );
}
