import Section from "@/components/shared/Section";

const steps = [
  {
    number: "1",
    title: "Ingest",
    description:
      "Real-time ingestion of Fed facility data, reverse repo flows, interbank indices, and equity fundamentals via scheduled pipelines.",
  },
  {
    number: "2",
    title: "Analyze",
    description:
      "Multi-factor quantitative engine computes the Interbank Liquidity Index (ILI), detects regime shifts, and scores anomalies.",
  },
  {
    number: "3",
    title: "Act",
    description:
      "Actionable alpha signals surface with confidence scores, cost analysis, and cooldown logic — ready for systematic execution.",
  },
];

export default function HowItWorks() {
  return (
    <Section
      id="how-it-works"
      className="mx-auto max-w-5xl px-6 py-20 bg-zinc-50"
    >
      <h2 className="text-center text-3xl font-bold tracking-tight text-zinc-900 sm:text-4xl">
        How It Works
      </h2>

      <div className="mt-12 grid gap-8 sm:grid-cols-3">
        {steps.map((step) => (
          <div key={step.number} className="flex flex-col items-center text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-ili-blue text-lg font-bold text-white">
              {step.number}
            </div>
            <h3 className="mt-4 text-lg font-semibold text-zinc-900">
              {step.title}
            </h3>
            <p className="mt-2 text-sm leading-relaxed text-zinc-600">
              {step.description}
            </p>
          </div>
        ))}
      </div>
    </Section>
  );
}
