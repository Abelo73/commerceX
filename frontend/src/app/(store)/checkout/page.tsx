"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { AddressForm } from "@/components/account/AddressForm";
import { TestCardSelect } from "@/components/checkout/TestCardSelect";
import { useCart } from "@/components/providers";
import { ApiError, api, newIdempotencyKey } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";
import { useRequireAuth } from "@/lib/hooks/useRequireAuth";
import { TEST_CARDS } from "@/lib/orders";
import { payOrder } from "@/lib/payments";
import type { Address, Cart, OrderDetails, Quote } from "@/lib/types";

const ORDER_KEY = "cx_checkout_key";

/**
 * One key per checkout attempt, kept across reloads: resubmitting (double click, flaky network) returns the
 * same order instead of creating a second one. It is cleared once the order exists.
 */
function checkoutKey() {
  let key = sessionStorage.getItem(ORDER_KEY);
  if (!key) {
    key = newIdempotencyKey("checkout");
    sessionStorage.setItem(ORDER_KEY, key);
  }
  return key;
}

export default function CheckoutPage() {
  const user = useRequireAuth();
  const router = useRouter();
  const { refresh: refreshCart } = useCart();
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [addressId, setAddressId] = useState<number | null>(null);
  const [addingAddress, setAddingAddress] = useState(false);
  const [shipping, setShipping] = useState("STANDARD");
  const [quote, setQuote] = useState<Quote | null>(null);
  const [code, setCode] = useState("");
  const [codeMessage, setCodeMessage] = useState<string | null>(null);
  const [token, setToken] = useState<string>(TEST_CARDS[0].token);
  const [error, setError] = useState<string | null>(null);
  const [placing, setPlacing] = useState(false);

  const loadQuote = useCallback(
    async (method: string) => setQuote(await api<Quote>("/checkout/quote", { method: "POST", body: { shippingMethod: method } })),
    [],
  );

  useEffect(() => {
    if (!user) return;
    let active = true;
    api<Address[]>("/me/addresses").then((list) => {
      if (!active) return;
      setAddresses(list);
      setAddressId((current) => current ?? list.find((a) => a.isDefault)?.id ?? list[0]?.id ?? null);
      setAddingAddress(list.length === 0);
    });
    api<Quote>("/checkout/quote", { method: "POST", body: { shippingMethod: "STANDARD" } })
      .then((q) => active && setQuote(q))
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [user]);

  async function applyCode(event: React.FormEvent) {
    event.preventDefault();
    setCodeMessage(null);
    try {
      await api<Cart>("/cart/coupon", { method: "PUT", body: { code } });
      setCode("");
      await Promise.all([loadQuote(shipping), refreshCart()]);
    } catch (e) {
      setCodeMessage(e instanceof ApiError ? e.message : "That code couldn’t be applied.");
    }
  }

  async function removeCode() {
    await api("/cart/coupon", { method: "DELETE" });
    await Promise.all([loadQuote(shipping), refreshCart()]);
  }

  async function placeOrder() {
    if (!quote || !addressId) return;
    setPlacing(true);
    setError(null);
    let order: OrderDetails;
    try {
      order = await api<OrderDetails>("/orders", {
        method: "POST",
        headers: { "Idempotency-Key": checkoutKey() },
        body: { addressId, shippingMethod: shipping, expectedTotal: quote.total.amount },
      });
    } catch (e) {
      setPlacing(false);
      if (e instanceof ApiError) {
        // The request was answered, so this attempt is over: a new submit gets a new key.
        sessionStorage.removeItem(ORDER_KEY);
        if (e.code === "PRICE_CHANGED") {
          await loadQuote(shipping);
          setError("Prices changed since you opened checkout. Check the new total, then place your order.");
        } else if (e.code === "INSUFFICIENT_STOCK" || e.code === "CHECKOUT_NOT_READY") {
          await loadQuote(shipping);
          setError(`${e.message} Update your cart and try again.`);
        } else {
          setError(e.message);
        }
      } else {
        setError("We couldn’t reach the store. Your order was not placed twice: try again with the same details.");
      }
      return;
    }
    sessionStorage.removeItem(ORDER_KEY);
    await refreshCart();
    try {
      await payOrder(order.orderNumber, token);
    } catch {
      // The order exists and its stock is held; payment can be retried from the confirmation page.
    }
    router.push(`/checkout/confirmation/${order.orderNumber}`);
  }

  if (!user || !quote) return <div className="mx-auto max-w-6xl px-4 py-16" aria-busy="true" />;

  if (quote.lines.length === 0) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16">
        <h1 className="display text-6xl">Checkout</h1>
        <p className="mt-6 text-lg">Your cart is empty.</p>
        <Link href="/shop" className="bg-brand text-white mt-6 inline-block rounded-lg px-6 py-3 font-semibold">
          Browse the shop
        </Link>
      </div>
    );
  }

  const canPlace = quote.ready && addressId !== null && !placing;

  return (
    <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6">
      <h1 className="display text-6xl">Checkout</h1>
      <div className="mt-10 grid gap-12 lg:grid-cols-[1.4fr_1fr]">
        <div className="space-y-12">
          <Step n={1} title="Shipping address">
            {addresses && addresses.length > 0 && !addingAddress && (
              <fieldset className="space-y-3">
                <legend className="sr-only">Choose an address</legend>
                {addresses.map((a) => (
                  <label key={a.id} className={`flex cursor-pointer gap-3 rounded-xl border bg-white p-4 ${addressId === a.id ? "border-brand ring-brand ring-1" : "border-line"}`}>
                    <input type="radio" name="address" className="accent-brand mt-1" checked={addressId === a.id} onChange={() => setAddressId(a.id)} />
                    <span>
                      <span className="font-semibold">{a.fullName}</span>
                      <span className="text-mute block text-sm">
                        {a.line1}
                        {a.line2 ? `, ${a.line2}` : ""}, {a.city} {a.postalCode}, {a.country}
                      </span>
                    </span>
                  </label>
                ))}
                <button type="button" onClick={() => setAddingAddress(true)} className="text-brand text-sm font-medium underline underline-offset-4">
                  Ship somewhere else
                </button>
              </fieldset>
            )}
            {addingAddress && (
              <AddressForm
                makeDefault={addresses?.length === 0}
                onCancel={addresses && addresses.length > 0 ? () => setAddingAddress(false) : undefined}
                onSaved={(saved) => {
                  setAddresses((list) => [...(list ?? []), saved]);
                  setAddressId(saved.id);
                  setAddingAddress(false);
                }}
              />
            )}
          </Step>

          <Step n={2} title="Delivery">
            <fieldset className="grid gap-3 sm:grid-cols-2">
              <legend className="sr-only">Choose a delivery speed</legend>
              {quote.shippingOptions.map((option) => (
                <label key={option.code} className={`flex cursor-pointer justify-between gap-3 rounded-xl border bg-white p-4 ${shipping === option.code ? "border-brand ring-brand ring-1" : "border-line"}`}>
                  <span className="flex gap-3">
                    <input
                      type="radio"
                      name="shipping"
                      className="accent-brand mt-1"
                      checked={shipping === option.code}
                      onChange={() => {
                        setShipping(option.code);
                        loadQuote(option.code);
                      }}
                    />
                    <span>
                      <span className="font-semibold">{option.name}</span>
                      <span className="text-mute block text-sm">
                        {option.minDays}–{option.maxDays} business days
                      </span>
                    </span>
                  </span>
                  <span className="figures font-semibold">{option.price.amount === 0 ? "Free" : formatMoney(option.price)}</span>
                </label>
              ))}
            </fieldset>
          </Step>

          <Step n={3} title="Payment">
            <TestCardSelect value={token} onChange={setToken} />
          </Step>
        </div>

        <aside aria-labelledby="summary-title" className="bg-tile h-fit rounded-2xl p-6 lg:sticky lg:top-24">
          <h2 id="summary-title" className="condensed text-2xl font-semibold">
            Order summary
          </h2>
          <ul className="divide-line mt-4 divide-y text-sm">
            {quote.lines.map((line) => (
              <li key={line.variantId} className="flex justify-between gap-3 py-2">
                <span>
                  {line.productName} <span className="text-mute">{line.variantLabel}</span> × {line.quantity}
                  {line.issue && <span className="text-split block">No longer available</span>}
                </span>
                {line.lineTotal && <span className="figures">{formatMoney(line.lineTotal)}</span>}
              </li>
            ))}
          </ul>

          {quote.couponCode ? (
            <p className="mt-4 flex justify-between text-sm">
              <span>
                Code <strong>{quote.couponCode}</strong>
              </span>
              <button type="button" onClick={removeCode} className="text-mute underline underline-offset-4">
                Remove
              </button>
            </p>
          ) : (
            <form onSubmit={applyCode} className="mt-4 flex gap-2">
              <label className="sr-only" htmlFor="coupon">Discount code</label>
              <input id="coupon" value={code} onChange={(e) => setCode(e.target.value)} placeholder="Discount code" className="border-line w-full rounded-lg border bg-white px-4 py-2 text-sm uppercase" />
              <button disabled={!code.trim()} className="border-ink rounded-full border px-4 text-sm font-medium disabled:opacity-40">
                Apply
              </button>
            </form>
          )}
          {codeMessage && <p role="alert" className="text-split mt-2 text-sm">{codeMessage}</p>}

          <dl className="figures border-line mt-4 space-y-1 border-t pt-4 text-sm">
            <div className="flex justify-between"><dt>Subtotal</dt><dd>{formatMoney(quote.subtotal)}</dd></div>
            {quote.discount.amount > 0 && (
              <div className="text-brand flex justify-between"><dt>Discount</dt><dd>−{formatMoney(quote.discount)}</dd></div>
            )}
            <div className="flex justify-between"><dt>Shipping</dt><dd>{quote.shipping.amount === 0 ? "Free" : formatMoney(quote.shipping)}</dd></div>
            <div className="flex justify-between pt-2 text-lg font-semibold"><dt>Total</dt><dd>{formatMoney(quote.total)}</dd></div>
          </dl>

          {quote.problems.length > 0 && (
            <ul role="alert" className="text-split mt-3 space-y-1 text-sm">
              {quote.problems.map((p) => <li key={p}>{p}</li>)}
            </ul>
          )}
          {error && <p role="alert" className="text-split mt-3 text-sm">{error}</p>}

          <button
            type="button"
            onClick={placeOrder}
            disabled={!canPlace}
            className="bg-brand hover:bg-brand-deep text-white disabled:bg-line disabled:text-mute mt-5 w-full rounded-lg py-4 text-lg font-semibold"
          >
            {placing ? "Placing order…" : `Place order and pay ${formatMoney(quote.total)}`}
          </button>
          {!addressId && <p className="text-mute mt-2 text-sm">Add a shipping address to continue.</p>}
        </aside>
      </div>
    </div>
  );
}

function Step({ n, title, children }: { n: number; title: string; children: React.ReactNode }) {
  return (
    <section aria-labelledby={`step-${n}`}>
      <h2 id={`step-${n}`} className="mb-4 flex items-baseline gap-3">
        <span className="figures text-mute text-lg">{n}</span>
        <span className="condensed text-2xl font-semibold">{title}</span>
      </h2>
      {children}
    </section>
  );
}
