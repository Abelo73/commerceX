import Image from "next/image";
import Link from "next/link";
import { colorwayHex } from "@/lib/colors";
import type { ProductSummary } from "@/lib/types";
import { Price } from "./Price";
import { ProductArt, artKind } from "./ProductArt";

export function ProductCard({ product }: { product: ProductSummary }) {
  const kind = artKind(product.category?.slug, []);
  return (
    <article className="group relative">
      <div className="bg-midsole relative aspect-[4/3] overflow-hidden rounded-lg">
        {product.image ? (
          <Image
            src={product.image.url}
            alt={product.image.altText ?? product.name}
            fill
            sizes="(min-width: 1024px) 25vw, 50vw"
            className="object-cover transition-transform duration-500 group-hover:scale-[1.03]"
          />
        ) : (
          <ProductArt
            kind={kind === "shoe" ? "shoe" : guessApparel(product.name)}
            color={product.colors[0]}
            label={product.name}
            className="absolute inset-0 h-full w-full p-6 transition-transform duration-500 group-hover:-translate-y-1"
          />
        )}
        {!product.inStock && (
          <span className="bg-asphalt text-lane absolute top-3 left-3 rounded-full px-2.5 py-1 text-xs">
            Sold out
          </span>
        )}
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
        <ul className="mt-2 flex gap-1.5" aria-label={`${product.colors.length} colourways`}>
          {product.colors.slice(0, 5).map((color) => (
            <li
              key={color}
              title={color}
              className="ring-line h-3.5 w-3.5 rounded-full ring-1"
              style={{ backgroundColor: colorwayHex(color) }}
            />
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
