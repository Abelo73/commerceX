import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { ProductGrid } from "@/components/product/ProductGrid";
import { ShopFilters } from "@/components/shop/ShopFilters";
import { LANES } from "@/lib/nav";
import { serverGet } from "@/lib/server-api";
import type { CategoryNode, Page, ProductSummary } from "@/lib/types";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const PASSTHROUGH = ["q", "category", "brand", "minPrice", "maxPrice", "inStock", "sort", "page"];

function query(params: Record<string, string | string[] | undefined>) {
  const out = new URLSearchParams();
  for (const key of PASSTHROUGH) {
    const value = params[key];
    if (typeof value === "string" && value) out.set(key, value);
  }
  return out;
}

export async function generateMetadata({ searchParams }: { searchParams: SearchParams }): Promise<Metadata> {
  const params = await searchParams;
  const lane = LANES.find((l) => l.slug === params.category);
  return { title: typeof params.q === "string" ? `Results for “${params.q}”` : lane ? `${lane.name} running` : "Shop" };
}

export default async function ShopPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const q = query(params);
  q.set("size", "24");
  const [results, categories] = await Promise.all([
    serverGet<Page<ProductSummary>>(`/products?${q.toString()}`, 30),
    serverGet<CategoryNode[]>("/categories", 300),
  ]);
  const lane = LANES.find((l) => l.slug === params.category);
  const search = typeof params.q === "string" ? params.q : null;
  const heading = search ? `Results for “${search}”` : lane ? lane.name : "All shoes and gear";

  const pageHref = (page: number) => {
    const next = query(params);
    next.set("page", String(page));
    return `/shop?${next.toString()}`;
  };

  return (
    <div className="mx-auto max-w-7xl px-4 pt-10 sm:px-6">
      <h1 className="display text-6xl">{heading}</h1>
      <p className="text-mute mt-2" aria-live="polite">
        {results.totalElements} {results.totalElements === 1 ? "product" : "products"}
        {lane ? `: ${lane.blurb.toLowerCase()}` : ""}
      </p>

      <div className="border-line my-8 border-y py-4">
        <Suspense>
          <ShopFilters categories={categories} />
        </Suspense>
      </div>

      {results.content.length === 0 ? (
        <div className="py-16">
          <p className="text-xl">No products match these filters.</p>
          <Link href="/shop" className="text-brand mt-3 inline-block font-medium underline underline-offset-4">
            Clear all filters
          </Link>
        </div>
      ) : (
        <ProductGrid products={results.content} />
      )}

      {results.totalPages > 1 && (
        <nav aria-label="Pagination" className="mt-14 flex items-center justify-center gap-4">
          {results.page > 0 && (
            <Link href={pageHref(results.page - 1)} className="border-line rounded-lg border px-4 py-2">
              Previous
            </Link>
          )}
          <span className="figures text-mute text-sm">
            Page {results.page + 1} of {results.totalPages}
          </span>
          {results.page + 1 < results.totalPages && (
            <Link href={pageHref(results.page + 1)} className="border-line rounded-lg border px-4 py-2">
              Next
            </Link>
          )}
        </nav>
      )}
    </div>
  );
}
