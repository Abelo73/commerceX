import Link from "next/link";
import { CategoryCards } from "@/components/home/CategoryCards";
import { HeroPhoto } from "@/components/home/HeroPhoto";
import { Steps } from "@/components/home/Steps";
import { WhyUs } from "@/components/home/WhyUs";
import { ProductGrid } from "@/components/product/ProductGrid";
import { serverGet } from "@/lib/server-api";
import type { Page, ProductSummary } from "@/lib/types";

export default async function HomePage() {
  const newest = await serverGet<Page<ProductSummary>>("/products?sort=newest&size=4");

  return (
    <>
      <HeroPhoto />
      <WhyUs />
      <CategoryCards />

      <section aria-labelledby="new-title" className="mx-auto max-w-7xl px-4 py-24 sm:px-6">
        <div className="mb-10 flex flex-wrap items-end justify-between gap-4">
          <h2 id="new-title" className="display text-4xl sm:text-5xl">
            New this season
          </h2>
          <Link href="/shop?sort=newest" className="text-brand font-semibold whitespace-nowrap hover:underline">
            See all new arrivals
          </Link>
        </div>
        <ProductGrid products={newest.content} />
      </section>

      <Steps />

      <section aria-label="First order offer" className="mx-auto max-w-7xl px-4 sm:px-6">
        <div className="bg-navy relative overflow-hidden rounded-3xl px-6 py-12 text-white sm:px-12">
          <span aria-hidden className="absolute -top-24 -right-24 h-72 w-72 rounded-full border border-white/10" />
          <span aria-hidden className="absolute -top-12 -right-12 h-48 w-48 rounded-full border border-white/10" />
          <div className="relative flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="display text-3xl sm:text-4xl">10% off your first order</p>
              <p className="mt-2 text-white/75">
                Use code <span className="rounded-md bg-white/10 px-2 py-0.5 font-semibold text-white">FIRSTRUN</span> at checkout.
              </p>
            </div>
            <Link href="/register" className="text-navy inline-flex min-h-12 items-center self-start rounded-lg bg-white px-6 font-semibold transition-colors hover:bg-white/90 md:self-auto">
              Create an account
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}
