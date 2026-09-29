"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { colorwayHex } from "@/lib/colors";
import type { ProductSummary } from "@/lib/types";
import { Price } from "./Price";
import { ProductArt, artKind } from "./ProductArt";

export function ProductCard({ product }: { product: ProductSummary }) {
  const [color, setColor] = useState(product.colors[0]);
  const kind = artKind(product.category?.slug, []);
  const onSale = product.compareAtPrice && product.compareAtPrice.amount > product.price.amount;

  return (
    <article className="group relative">
      <div className="bg-tile relative aspect-[4/3] overflow-hidden rounded-2xl transition-colors duration-300 group-hover:bg-[#e6ebf5]">
        {product.image ? (
          <Image
            src={product.image.url}
            alt={product.image.altText ?? product.name}
            fill
            sizes="(min-width: 1024px) 25vw, 50vw"
            className="object-cover transition-transform duration-500 group-hover:scale-[1.04]"
          />
        ) : (
          <ProductArt
            kind={kind === "shoe" ? "shoe" : guessApparel(product.name)}
            color={color}
            label={product.name}
            className="absolute inset-0 h-full w-full p-6 transition-transform duration-500 ease-out group-hover:-translate-y-1"
          />
        )}
        <div className="absolute top-3 left-3 flex gap-1.5">
          {!product.inStock && <span className="bg-navy text-white rounded-full px-2.5 py-1 text-xs font-medium">Sold out</span>}
          {onSale && product.inStock && <span className="bg-split rounded-full px-2.5 py-1 text-xs font-semibold text-white">Sale</span>}
        </div>
      </div>
      <div className="mt-3 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="condensed text-lg leading-tight font-semibold">
            <Link href={`/p/${product.slug}`} className="after:absolute after:inset-0">
              {product.name}
            </Link>
          </h3>
          {product.category && <p className="text-mute text-sm">{product.category.name}</p>}
        </div>
        <Price price={product.price} compareAt={product.compareAtPrice} />
      </div>
      {product.colors.length > 1 && (
        <ul className="relative z-10 mt-2 flex gap-1" aria-label={`${product.colors.length} colourways`}>
          {product.colors.slice(0, 5).map((c) => (
            <li key={c}>
              <button
                type="button"
                title={c}
                aria-label={`Preview ${c}`}
                aria-pressed={c === color}
                onMouseEnter={() => setColor(c)}
                onFocus={() => setColor(c)}
                onClick={() => setColor(c)}
                className="grid h-7 w-7 place-items-center rounded-full"
              >
                <span
                  className={`block h-4 w-4 rounded-full ring-offset-1 ${c === color ? "ring-ink ring-2" : "ring-line ring-1"}`}
                  style={{ backgroundColor: colorwayHex(c) }}
                />
              </button>
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}

function guessApparel(name: string) {
  const lower = name.toLowerCase();
  if (lower.includes("short")) return "shorts" as const;
  if (lower.includes("sock")) return "socks" as const;
  return "top" as const;
}
