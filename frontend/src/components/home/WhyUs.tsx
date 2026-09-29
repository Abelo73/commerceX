import { Check, Minus } from "lucide-react";

const ROWS = [
  { topic: "Stock", ours: "Shown live for every size", usual: "Found out at checkout" },
  { topic: "Price", ours: "The total you see is the total you pay", usual: "Changes at the last step" },
  { topic: "Reviews", ours: "Only from runners who received the shoe", usual: "Anyone can post one" },
  { topic: "Tracking", ours: "Every step from paid to delivered", usual: "A tracking number, eventually" },
];

/** Side-by-side comparison, built only on what the store actually does. */
export function WhyUs() {
  return (
    <section aria-labelledby="why-title" className="mx-auto max-w-4xl px-4 py-24 sm:px-6">
      <h2 id="why-title" className="display text-center text-4xl sm:text-5xl">
        The pair you pick is the pair you get
      </h2>
      <p className="text-mute mx-auto mt-3 max-w-lg text-center">Same shoes, same prices. The difference is how we tell you what’s true.</p>

      <div className="border-line mt-12 overflow-hidden rounded-2xl border">
        <div className="bg-navy hidden grid-cols-[8rem_1fr_1fr] text-sm font-semibold text-white sm:grid">
          <span />
          <span className="flex items-center gap-2 px-5 py-4">
            <span aria-hidden className="bg-ok h-2 w-2 rounded-full" /> Shopping at CommerceX
          </span>
          <span className="flex items-center gap-2 border-l border-white/10 px-5 py-4 text-white/70">
            <span aria-hidden className="h-2 w-2 rounded-full border border-white/50" /> The usual way
          </span>
        </div>
        <dl className="divide-line divide-y">
          {ROWS.map((row) => (
            <div key={row.topic} className="grid gap-2 p-4 sm:grid-cols-[8rem_1fr_1fr] sm:gap-0 sm:p-0">
              <dt className="text-mute text-xs font-semibold tracking-wide sm:px-5 sm:py-4">{row.topic}</dt>
              <dd className="flex items-center gap-3 text-sm font-medium sm:px-5 sm:py-4">
                <span className="bg-navy grid h-5 w-5 shrink-0 place-items-center rounded-full text-white">
                  <Check aria-hidden className="h-3 w-3" strokeWidth={3} />
                </span>
                {row.ours}
              </dd>
              <dd className="bg-surface text-mute flex items-center gap-3 rounded-lg px-3 py-2 text-sm sm:rounded-none sm:px-5 sm:py-4">
                <span className="border-line grid h-5 w-5 shrink-0 place-items-center rounded-full border bg-white">
                  <Minus aria-hidden className="h-3 w-3" />
                </span>
                {row.usual}
              </dd>
            </div>
          ))}
        </dl>
      </div>
      <p className="bg-tile text-ink mt-4 flex items-start gap-3 rounded-xl px-5 py-4 text-sm">
        <span aria-hidden className="bg-navy mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full" />
        If a price or stock level changes while you check out, we stop and show you before you pay.
      </p>
    </section>
  );
}
