import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { ProductArt } from "@/components/product/ProductArt";
import { LANES } from "@/lib/nav";

const ART: Record<string, { color: string; kind: "shoe" | "top" }> = {
  road: { color: "Track Blue", kind: "shoe" },
  trail: { color: "Moss", kind: "shoe" },
  track: { color: "Signal Red", kind: "shoe" },
  apparel: { color: "Navy", kind: "top" },
};

export function CategoryCards() {
  return (
    <section aria-labelledby="cat-title" className="bg-surface py-24">
      <div className="mx-auto max-w-7xl px-4 sm:px-6">
        <h2 id="cat-title" className="display text-center text-4xl sm:text-5xl">
          Shop by where you run
        </h2>
        <ul className="mt-12 grid grid-cols-2 gap-4 lg:grid-cols-4">
          {LANES.map((lane) => (
            <li key={lane.slug}>
              <Link
                href={`/shop?category=${lane.slug}`}
                className="group border-line hover:border-brand/40 block rounded-2xl border bg-white p-5 transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_18px_40px_-24px_rgba(11,26,74,0.45)]"
              >
                <div className="bg-tile aspect-[4/3] rounded-xl">
                  <ProductArt kind={ART[lane.slug].kind} color={ART[lane.slug].color} label="" className="h-full w-full p-5 transition-transform duration-300 group-hover:scale-105" />
                </div>
                <div className="mt-4 flex items-start justify-between gap-2">
                  <div>
                    <p className="condensed text-lg font-bold">{lane.name}</p>
                    <p className="text-mute text-sm">{lane.blurb}</p>
                  </div>
                  <ArrowUpRight aria-hidden className="text-mute group-hover:text-brand h-5 w-5 shrink-0 transition-colors" />
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
