export default function Footer() {
  const year = new Date().getFullYear();

  return (
    <footer className="border-t border-zinc-200 bg-zinc-50 px-6 py-12">
      <div className="mx-auto flex max-w-5xl flex-col items-center gap-6 sm:flex-row sm:justify-between">
        <nav aria-label="Footer" className="flex gap-6 text-sm text-zinc-500">
          <a
            href="https://github.com/user/tickonomics"
            target="_blank"
            rel="noopener noreferrer"
            className="transition hover:text-zinc-900"
          >
            GitHub
          </a>
          <a
            href="https://docs.tickonomics.io"
            target="_blank"
            rel="noopener noreferrer"
            className="transition hover:text-zinc-900"
          >
            Documentation
          </a>
        </nav>

        <p className="text-xs text-zinc-400">
          &copy; {year} Tickonomics. For educational and research purposes only.
          Not financial advice.
        </p>
      </div>
    </footer>
  );
}
