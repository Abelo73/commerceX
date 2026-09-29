"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useTransition } from "react";
import type { CategoryNode } from "@/lib/types";

const SORTS = [
  ["newest", "Newest"],
  ["price_asc", "Price: low to high"],
  ["price_desc", "Price: high to low"],
  ["rating", "Top rated"],
] as const;

const PRICE_BANDS = [
  ["", "Any price"],
  ["0-10000", "Under $100"],
  ["10000-15000", "$100 to $150"],
  ["15000-", "$150 and up"],
] as const;

/** Filters live in the URL, so every filtered view can be shared, bookmarked and server-rendered. */
export function ShopFilters({ categories }: { categories: CategoryNode[] }) {
  const router = useRouter();
  const params = useSearchParams();
  const [pending, startTransition] = useTransition();

  function set(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params.toString());
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    next.delete("page");
    startTransition(() => router.push(`/shop?${next.toString()}`, { scroll: false }));
  }

  const band = [params.get("minPrice") ?? "", params.get("maxPrice") ?? ""].join("-");
  const selectClass =
    "border-line focus:border-brand rounded-full border bg-white py-2 pr-8 pl-3 text-sm outline-none";

  return (
    <div className="flex flex-wrap items-center gap-3" aria-busy={pending}>
      <label className="sr-only" htmlFor="filter-category">Category</label>
      <select
        id="filter-category"
        className={selectClass}
        value={params.get("category") ?? ""}
        onChange={(e) => set({ category: e.target.value || null })}
      >
        <option value="">All categories</option>
        {categories.map((c) => (
          <option key={c.slug} value={c.slug}>
            {c.name}
          </option>
        ))}
      </select>

      <label className="sr-only" htmlFor="filter-price">Price</label>
      <select
        id="filter-price"
        className={selectClass}
        value={PRICE_BANDS.some(([v]) => v === band) ? band : ""}
        onChange={(e) => {
          const [min, max] = e.target.value.split("-");
          set({ minPrice: min || null, maxPrice: max || null });
        }}
      >
        {PRICE_BANDS.map(([value, label]) => (
          <option key={label} value={value}>
            {label}
          </option>
        ))}
      </select>

      <label className="border-line flex cursor-pointer items-center gap-2 rounded-lg border bg-white px-3 py-2 text-sm">
        <input
          type="checkbox"
          className="accent-brand"
          checked={params.get("inStock") === "true"}
          onChange={(e) => set({ inStock: e.target.checked ? "true" : null })}
        />
        In stock only
      </label>

      <label className="text-mute ml-auto flex items-center gap-2 text-sm" htmlFor="filter-sort">
        Sort by
        <select
          id="filter-sort"
          className={selectClass + " text-ink"}
          value={params.get("sort") ?? (params.get("q") ? "relevance" : "newest")}
          onChange={(e) => set({ sort: e.target.value })}
        >
          {params.get("q") && <option value="relevance">Best match</option>}
          {SORTS.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
