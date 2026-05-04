import Section from "@/components/shared/Section";

export default function PricingSection() {
  return (
    <Section id="pricing" className="mx-auto max-w-5xl px-6 py-20">
      <h2 className="text-center text-3xl font-bold tracking-tight text-zinc-900 sm:text-4xl">
        Open Source
      </h2>
      <p className="mx-auto mt-4 max-w-xl text-center text-zinc-600">
        Tickonomics is fully open source. Self-host it, inspect the code, and
        contribute.
      </p>

      <div className="mx-auto mt-10 max-w-md rounded-xl border border-zinc-200 bg-white p-8 text-center shadow-sm">
        <span className="inline-flex items-center rounded-full bg-emerald-100 px-3 py-1 text-sm font-semibold text-emerald-800">
          Free &amp; Open Source
        </span>
        <p className="mt-6 text-4xl font-bold text-zinc-900">$0</p>
        <p className="mt-1 text-sm text-zinc-500">forever</p>

        <ul className="mt-6 space-y-3 text-left text-sm text-zinc-600">
          <li className="flex items-start gap-2">
            <span className="mt-0.5 text-emerald-500">&#10003;</span>
            Full source code on GitHub
          </li>
          <li className="flex items-start gap-2">
            <span className="mt-0.5 text-emerald-500">&#10003;</span>
            Self-host with Docker Compose
          </li>
          <li className="flex items-start gap-2">
            <span className="mt-0.5 text-emerald-500">&#10003;</span>
            All signals, strategies, and indices
          </li>
          <li className="flex items-start gap-2">
            <span className="mt-0.5 text-emerald-500">&#10003;</span>
            Community support
          </li>
        </ul>

        <a
          href="https://github.com/user/tickonomics"
          target="_blank"
          rel="noopener noreferrer"
          className="mt-8 inline-block w-full rounded-lg bg-zinc-900 py-3 text-center text-sm font-semibold text-white transition hover:bg-zinc-800"
        >
          View on GitHub
        </a>
      </div>
    </Section>
  );
}
