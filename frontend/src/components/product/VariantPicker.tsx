"use client";

import { useMemo, useState } from "react";
import { ApiError, useCart } from "@/components/providers";
import { colorwayHex } from "@/lib/colors";
import type { ProductDetail, Variant } from "@/lib/types";
import { Price } from "./Price";
import { ProductArt, artKind } from "./ProductArt";

const AVAILABILITY_TEXT = {
  IN_STOCK: "In stock, ships in 1–2 days",
  LOW_STOCK: "Only a few left",
  OUT_OF_STOCK: "Sold out in this size",
} as const;

export function VariantPicker({ product }: { product: ProductDetail }) {
  const { add } = useCart();
  const colorOption = product.options.find((o) => o.name === "Color");
  const otherOptions = product.options.filter((o) => o.name !== "Color");

  const firstAvailable = product.variants.find((v) => v.availability !== "OUT_OF_STOCK") ?? product.variants[0];
  const [selection, setSelection] = useState<Record<string, string>>(() => {
    const initial: Record<string, string> = {};
    if (colorOption && firstAvailable) initial.Color = firstAvailable.options.Color;
    return initial;
  });
  const [status, setStatus] = useState<{ kind: "error" | "added"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const selected: Variant | undefined = useMemo(
    () =>
      product.variants.find((v) => product.options.every((o) => selection[o.name] === v.options[o.name])),
    [product, selection],
  );
  const missing = product.options.filter((o) => !selection[o.name]).map((o) => o.name.toLowerCase());

  function variantFor(optionName: string, value: string) {
    const wanted = { ...selection, [optionName]: value };
    return product.variants.find((v) =>
      Object.entries(wanted).every(([name, val]) => v.options[name] === val),
    );
  }

  async function addToCart() {
    if (!selected) return;
    setBusy(true);
    setStatus(null);
    try {
      await add(selected.id, 1);
      setStatus({ kind: "added", text: `Added ${product.name}, ${selected.label}, to your cart.` });
    } catch (e) {
      setStatus({ kind: "error", text: e instanceof ApiError ? e.message : "Could not add to cart. Try again." });
    } finally {
      setBusy(false);
    }
  }

  const kind = artKind(product.category?.slug, product.tags);

  return (
    <div className="grid gap-10 md:grid-cols-[1.2fr_1fr]">
      <div className="bg-midsole relative aspect-[4/3] rounded-2xl md:sticky md:top-24 md:self-start">
        <ProductArt kind={kind} color={selection.Color ?? colorOption?.values[0]} label={`${product.name} in ${selection.Color ?? ""}`} className="absolute inset-0 h-full w-full p-10" />
      </div>

      <div>
        {product.category && <p className="text-mute">{product.category.name}</p>}
        <h1 className="display mt-1 text-6xl">{product.name}</h1>
        <div className="mt-3 text-xl">
          <Price price={selected?.price ?? product.price} compareAt={selected?.compareAtPrice ?? product.compareAtPrice} from={!selected} />
        </div>
        {product.shortDescription && <p className="mt-4 max-w-prose text-lg">{product.shortDescription}</p>}

        {colorOption && (
          <fieldset className="mt-8">
            <legend className="font-semibold">
              Colour <span className="text-mute font-normal">{selection.Color}</span>
            </legend>
            <div className="mt-3 flex flex-wrap gap-3">
              {colorOption.values.map((color) => (
                <label key={color} className="cursor-pointer" title={color}>
                  <input
                    type="radio"
                    name="Color"
                    value={color}
                    checked={selection.Color === color}
                    onChange={() => setSelection((s) => ({ ...s, Color: color }))}
                    className="peer sr-only"
                  />
                  <span
                    className="ring-line peer-checked:ring-track peer-focus-visible:outline-track block h-10 w-10 rounded-full ring-1 ring-offset-2 ring-offset-chalk peer-checked:ring-2 peer-focus-visible:outline-2 peer-focus-visible:outline-offset-4"
                    style={{ backgroundColor: colorwayHex(color) }}
                  />
                  <span className="sr-only">{color}</span>
                </label>
              ))}
            </div>
          </fieldset>
        )}

        {otherOptions.map((option) => (
          <fieldset key={option.name} className="mt-8">
            <legend className="font-semibold">{option.name === "Size" ? "Size (EU)" : option.name}</legend>
            <div className="mt-3 grid grid-cols-4 gap-2 sm:grid-cols-6">
              {option.values.map((value) => {
                const variant = variantFor(option.name, value);
                const soldOut = !variant || variant.availability === "OUT_OF_STOCK";
                return (
                  <label key={value} className={soldOut ? "cursor-not-allowed" : "cursor-pointer"}>
                    <input
                      type="radio"
                      name={option.name}
                      value={value}
                      disabled={soldOut}
                      checked={selection[option.name] === value}
                      onChange={() => setSelection((s) => ({ ...s, [option.name]: value }))}
                      className="peer sr-only"
                    />
                    <span className="border-line peer-checked:bg-asphalt peer-checked:text-lane peer-checked:border-asphalt peer-focus-visible:outline-track figures block rounded-md border bg-white py-2.5 text-center text-sm peer-disabled:bg-transparent peer-disabled:text-mute/60 peer-disabled:line-through peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2">
                      {value}
                    </span>
                  </label>
                );
              })}
            </div>
          </fieldset>
        ))}

        <div className="mt-8">
          <p className={`text-sm ${selected?.availability === "LOW_STOCK" ? "text-split" : "text-mute"}`} aria-live="polite">
            {selected ? AVAILABILITY_TEXT[selected.availability] : `Choose a ${missing.join(" and ")}`}
          </p>
          <button
            type="button"
            onClick={addToCart}
            disabled={!selected || selected.availability === "OUT_OF_STOCK" || busy}
            className="bg-track hover:bg-track-deep text-lane disabled:bg-midsole disabled:text-mute mt-3 w-full rounded-full py-4 text-lg font-semibold transition-colors"
          >
            {busy ? "Adding…" : "Add to cart"}
          </button>
          {status && (
            <p role={status.kind === "error" ? "alert" : "status"} className={`mt-3 text-sm ${status.kind === "error" ? "text-split" : "text-mute"}`}>
              {status.text}
            </p>
          )}
        </div>

        {product.description && (
          <div className="border-line mt-10 border-t pt-6">
            <h2 className="condensed text-xl font-semibold">About this {kind === "shoe" ? "shoe" : "piece"}</h2>
            <p className="mt-2 max-w-prose leading-relaxed">{product.description}</p>
          </div>
        )}
      </div>
    </div>
  );
}
