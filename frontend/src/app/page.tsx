import Link from "next/link";
import { LaneHero } from "@/components/home/LaneHero";
import { ProductGrid } from "@/components/product/ProductGrid";
import { serverGet } from "@/lib/server-api";
import type { Page, ProductSummary } from "@/lib/types";

export default async function HomePage() {
  const [newest, trail, race] = await Promise.all([
    serverGet<Page<ProductSummary>>("/products?sort=newest&size=4"),
    serverGet<Page<ProductSummary>>("/products?category=trail&size=4&sort=price_desc"),
    serverGet<Page<ProductSummary>>("/products?q=race&size=1"),
  ]);
  const featured = race.content[0] ?? newest.content[0];

  return (
    <>
      <LaneHero featuredSlug={featured?.slug} featuredName={featured?.name} />

      <section aria-labelledby="new-title" className="mx-auto max-w-7xl px-4 pt-28 sm:px-6">
        <div className="mb-8 flex items-end justify-between gap-4">
          <h2 id="new-title" className="display text-5xl">
            New this season
          </h2>
          <Link href="/shop?sort=newest" className="text-track font-medium whitespace-nowrap underline underline-offset-4">
            See all new shoes
          </Link>
        </div>
        <ProductGrid products={newest.content} />
      </section>

      <section aria-label="First order offer" className="mx-auto mt-24 max-w-7xl px-4 sm:px-6">
        <div className="bg-midsole flex flex-col gap-4 rounded-2xl px-6 py-8 sm:flex-row sm:items-center sm:justify-between sm:px-10">
          <p className="condensed max-w-xl text-2xl font-semibold leading-snug">
            New here? Take 10% off your first order with code <span className="text-track">FIRSTRUN</span> at checkout.
          </p>
          <Link href="/register" className="bg-track hover:bg-track-deep text-lane shrink-0 rounded-full px-6 py-3 font-semibold">
            Create an account
          </Link>
        </div>
      </section>

      <section aria-labelledby="trail-title" className="mx-auto max-w-7xl px-4 pt-24 sm:px-6">
        <div className="mb-8 flex items-end justify-between gap-4">
          <h2 id="trail-title" className="display text-5xl">
            Off the pavement
          </h2>
          <Link href="/shop?category=trail" className="text-track font-medium whitespace-nowrap underline underline-offset-4">
            Shop trail
          </Link>
        </div>
        <ProductGrid products={trail.content} />
      </section>

      <section aria-labelledby="story-title" className="mx-auto mt-28 grid max-w-7xl gap-8 px-4 sm:px-6 md:grid-cols-2">
        <h2 id="story-title" className="display text-6xl">
          Built lap by lap.
        </h2>
        <div className="text-mute max-w-prose space-y-4 text-lg">
          <p>
            We test every shoe on the same 400 metres of blue track and the same muddy loop before it reaches the
            shop. If it does not hold its shape at the end of a long run, it does not make the cut.
          </p>
          <p>
            Our shoes follow standard EU sizing. Between sizes? Go up for long runs, when feet swell, and stay true
            to size for spikes and racing flats.
          </p>
        </div>
      </section>
    </>
  );
}
