"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { Minus, Plus, X } from "lucide-react";
import { ApiError, useCart } from "@/components/providers";
import { formatMoney } from "@/lib/format";
import type { CartLine } from "@/lib/types";
import { ProductArt } from "@/components/product/ProductArt";

function kindFromName(name?: string) {
  const lower = (name ?? "").toLowerCase();
  if (lower.includes("short")) return "shorts" as const;
  if (lower.includes("sock")) return "socks" as const;
  if (lower.includes("sleeve") || lower.includes("tee")) return "top" as const;
  return "shoe" as const;
}

const ISSUE_TEXT: Record<string, (line: CartLine) => string> = {
  UNAVAILABLE: () => "No longer available. Remove it to check out.",
  OUT_OF_STOCK: () => "Sold out. Remove it to check out.",
  INSUFFICIENT_STOCK: (line) => `Only ${line.availableQuantity} left. Lower the quantity to check out.`,
};

export function CartDrawer() {
  const { cart, open, setOpen, update, remove } = useCart();
  const reduce = useReducedMotion();
  const closeRef = useRef<HTMLButtonElement>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      previous?.focus();
    };
  }, [open, setOpen]);

  async function run(action: () => Promise<void>) {
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Your cart could not be updated. Try again.");
    }
  }

  const lines = cart?.lines ?? [];

  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-50">
          <motion.div
            className="bg-ink/40 absolute inset-0"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setOpen(false)}
          />
          <motion.aside
            role="dialog"
            aria-modal="true"
            aria-labelledby="cart-title"
            className="bg-surface absolute inset-y-0 right-0 flex w-full max-w-md flex-col shadow-2xl"
            initial={reduce ? { opacity: 0 } : { x: "100%" }}
            animate={reduce ? { opacity: 1 } : { x: 0 }}
            exit={reduce ? { opacity: 0 } : { x: "100%" }}
            transition={{ type: "spring", stiffness: 380, damping: 38 }}
          >
            <div className="border-line flex items-center justify-between border-b px-5 py-4">
              <h2 id="cart-title" className="display text-3xl">
                Your cart
              </h2>
              <button ref={closeRef} type="button" onClick={() => setOpen(false)} className="hover:bg-tile rounded-full p-2" aria-label="Close cart">
                <X aria-hidden className="h-5 w-5" />
              </button>
            </div>

            {lines.length === 0 ? (
              <div className="flex flex-1 flex-col items-start justify-center gap-4 px-5">
                <p className="text-lg">Your cart is empty.</p>
                <Link href="/shop" onClick={() => setOpen(false)} className="bg-brand hover:bg-brand-deep text-white rounded-lg px-5 py-2.5 font-medium">
                  Browse the shop
                </Link>
              </div>
            ) : (
              <>
                <ul className="flex-1 divide-y divide-line overflow-y-auto px-5">
                  {lines.map((line) => (
                    <li key={line.variantId} className="flex gap-4 py-4">
                      <div className="bg-tile h-20 w-24 shrink-0 rounded-md">
                        <ProductArt kind={kindFromName(line.productName)} color={line.variantLabel?.split(" / ")[0]} label="" className="h-full w-full p-2" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex justify-between gap-2">
                          <Link href={`/p/${line.productSlug}`} onClick={() => setOpen(false)} className="condensed font-semibold leading-tight hover:text-brand">
                            {line.productName ?? "Unavailable item"}
                          </Link>
                          {line.lineTotal && <span className="figures font-semibold">{formatMoney(line.lineTotal)}</span>}
                        </div>
                        {line.variantLabel && <p className="text-mute text-sm">{line.variantLabel}</p>}
                        {line.issue && (
                          <p className="text-split mt-1 text-sm" role="status">
                            {ISSUE_TEXT[line.issue](line)}
                          </p>
                        )}
                        <div className="mt-2 flex items-center gap-3">
                          <div className="border-line flex items-center rounded-full border">
                            <button
                              type="button"
                              aria-label={`Decrease quantity of ${line.productName}`}
                              disabled={line.quantity <= 1}
                              onClick={() => run(() => update(line.variantId, line.quantity - 1))}
                              className="p-1.5 disabled:opacity-30"
                            >
                              <Minus aria-hidden className="h-3.5 w-3.5" />
                            </button>
                            <span className="figures w-6 text-center text-sm" aria-live="polite">
                              {line.quantity}
                            </span>
                            <button
                              type="button"
                              aria-label={`Increase quantity of ${line.productName}`}
                              onClick={() => run(() => update(line.variantId, line.quantity + 1))}
                              className="p-1.5"
                            >
                              <Plus aria-hidden className="h-3.5 w-3.5" />
                            </button>
                          </div>
                          <button type="button" onClick={() => run(() => remove(line.variantId))} className="text-mute text-sm underline underline-offset-2 hover:text-ink">
                            Remove
                          </button>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
                <div className="border-line space-y-2 border-t px-5 py-4">
                  {error && (
                    <p className="text-split text-sm" role="alert">
                      {error}
                    </p>
                  )}
                  <dl className="figures space-y-1 text-sm">
                    <div className="flex justify-between">
                      <dt>Subtotal</dt>
                      <dd>{cart && formatMoney(cart.subtotal)}</dd>
                    </div>
                    {cart?.coupon?.valid && (
                      <div className="flex justify-between text-brand">
                        <dt>Code {cart.coupon.code}</dt>
                        <dd>−{formatMoney(cart.discount)}</dd>
                      </div>
                    )}
                    <div className="flex justify-between text-base font-semibold">
                      <dt>Total before shipping</dt>
                      <dd>{cart && formatMoney(cart.total)}</dd>
                    </div>
                  </dl>
                  <Link
                    href="/checkout"
                    onClick={() => setOpen(false)}
                    aria-disabled={!cart?.checkoutReady}
                    className={`block rounded-lg py-3 text-center font-semibold ${
                      cart?.checkoutReady ? "bg-brand text-white hover:bg-brand-deep" : "bg-tile text-mute pointer-events-none"
                    }`}
                  >
                    Check out
                  </Link>
                </div>
              </>
            )}
          </motion.aside>
        </div>
      )}
    </AnimatePresence>
  );
}
