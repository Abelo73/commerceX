"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ProductArt } from "@/components/product/ProductArt";
import { api } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";
import type { WishlistItem } from "@/lib/types";

export default function WishlistPage() {
  const [items, setItems] = useState<WishlistItem[] | null>(null);

  useEffect(() => {
    api<WishlistItem[]>("/wishlist").then(setItems).catch(() => setItems([]));
  }, []);

  if (items === null) return <p className="text-mute">Loading your wishlist…</p>;
  if (items.length === 0) {
    return (
      <p>
        Nothing saved yet. Tap the heart on any product to keep it here.{" "}
        <Link href="/shop" className="text-brand font-medium underline underline-offset-4">
          Browse the shop
        </Link>
      </p>
    );
  }
  return (
    <ul className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3">
      {items.map((item) => (
        <li key={item.productId} className="relative">
          <div className="bg-tile aspect-[4/3] rounded-lg">
            <ProductArt kind="shoe" color={undefined} label={item.name} className={`h-full w-full p-6 ${item.available ? "" : "opacity-40"}`} />
          </div>
          <div className="mt-2 flex justify-between gap-2">
            {item.available ? (
              <Link href={`/p/${item.slug}`} className="condensed font-semibold hover:text-brand">
                {item.name}
              </Link>
            ) : (
              <span className="condensed text-mute font-semibold">{item.name}</span>
            )}
            {item.price && <span className="figures font-semibold">{formatMoney(item.price)}</span>}
          </div>
          <p className="text-mute text-sm">{!item.available ? "No longer sold" : item.inStock ? "In stock" : "Sold out"}</p>
          <button
            type="button"
            onClick={() => api<WishlistItem[]>(`/wishlist/${item.productId}`, { method: "DELETE" }).then(setItems)}
            className="text-mute mt-1 text-sm underline underline-offset-4 hover:text-ink"
          >
            Remove
          </button>
        </li>
      ))}
    </ul>
  );
}
