import HeroChart from "./HeroChart";

export default function Hero() {
  return (
    <section className="relative overflow-hidden bg-gradient-to-br from-zinc-900 via-zinc-800 to-zinc-900 text-white">
      <div className="absolute inset-0 opacity-20">
        <HeroChart />
      </div>

      <div className="relative mx-auto max-w-5xl px-6 py-24 text-center lg:py-32">
        <h1 className="text-4xl font-bold tracking-tight sm:text-5xl lg:text-6xl">
          Real-Time Funding Market Intelligence
        </h1>
        <p className="mx-auto mt-6 max-w-2xl text-lg text-zinc-300">
          Detect liquidity stress and equity price divergence before the market
          moves. Tickonomics fuses Fed facility data, reverse repo flows, and
          interbank indices into actionable alpha signals.
        </p>

        <div className="mt-10 flex flex-wrap items-center justify-center gap-4">
          <a
            href="https://app.tickonomics.io"
            target="_blank"
            rel="noopener noreferrer"
            className="rounded-lg bg-ili-blue px-6 py-3 text-sm font-semibold text-white shadow-lg transition hover:bg-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-400"
          >
            Start Demo
          </a>
          <a
            href="https://github.com/user/tickonomics"
            target="_blank"
            rel="noopener noreferrer"
            className="rounded-lg border border-zinc-500 px-6 py-3 text-sm font-semibold text-zinc-200 transition hover:border-white hover:text-white focus:outline-none focus:ring-2 focus:ring-zinc-400"
          >
            GitHub
          </a>
        </div>
      </div>
    </section>
  );
}
