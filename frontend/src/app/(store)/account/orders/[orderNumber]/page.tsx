"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Check, Star } from "lucide-react";
import { ApiError, api } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";
import { FULFILMENT_STAGES, ORDER_STATUS_TEXT, TEST_CARDS, isUnpaid } from "@/lib/orders";
import { payOrder } from "@/lib/payments";
import type { OrderDetails, Payment } from "@/lib/types";
import { TestCardSelect } from "@/components/checkout/TestCardSelect";

type Loaded = { order: OrderDetails; payments: Payment[] } | "missing" | null;

async function fetchOrder(orderNumber: string): Promise<Loaded> {
  const path = `/orders/${encodeURIComponent(orderNumber)}`;
  try {
    const [order, payments] = await Promise.all([api<OrderDetails>(path), api<Payment[]>(`${path}/payments`)]);
    return { order, payments };
  } catch (e) {
    return e instanceof ApiError && e.status === 404 ? "missing" : null;
  }
}

export default function OrderPage() {
  const { orderNumber } = useParams<{ orderNumber: string }>();
  const [order, setOrder] = useState<OrderDetails | null>(null);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [missing, setMissing] = useState(false);

  const apply = useCallback((result: Loaded) => {
    if (result === "missing") setMissing(true);
    else if (result) {
      setOrder(result.order);
      setPayments(result.payments);
    }
  }, []);

  /** Reload after an action (pay, cancel). */
  const load = useCallback(async () => apply(await fetchOrder(orderNumber)), [apply, orderNumber]);

  useEffect(() => {
    let active = true;
    fetchOrder(orderNumber).then((result) => active && apply(result));
    return () => {
      active = false;
    };
  }, [orderNumber, apply]);

  if (missing) {
    return (
      <p>
        We couldn’t find order {orderNumber} in your account.{" "}
        <Link href="/account" className="text-brand underline underline-offset-4">
          See all orders
        </Link>
      </p>
    );
  }
  if (!order) return <p className="text-mute">Loading order…</p>;

  const reached = new Map(order.history.map((h) => [h.to, h.at]));
  const lastPayment = payments.at(-1);

  return (
    <div className="space-y-10">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h2 className="display text-5xl">{order.orderNumber}</h2>
          <p className="text-mute">
            Placed {new Date(order.placedAt).toLocaleString("en-US", { dateStyle: "medium", timeStyle: "short" })}
          </p>
        </div>
        <p className={`text-xl font-semibold ${isUnpaid(order.status) ? "text-split" : ""}`}>
          {ORDER_STATUS_TEXT[order.status]}
        </p>
      </div>

      {isUnpaid(order.status) && (
        <PayNow order={order} lastPayment={lastPayment} onChange={load} />
      )}

      {!isUnpaid(order.status) && order.status !== "CANCELLED" && (
        <section aria-labelledby="progress-title">
          <h3 id="progress-title" className="sr-only">
            Progress
          </h3>
          <ol className="grid gap-3 sm:grid-cols-5">
            {FULFILMENT_STAGES.map((stage) => {
              const at = reached.get(stage);
              return (
                <li key={stage} className={`border-t-4 pt-2 ${at ? "border-brand" : "border-line"}`}>
                  <p className={`flex items-center gap-1.5 font-medium ${at ? "" : "text-mute"}`}>
                    {at && <Check aria-hidden className="text-brand h-4 w-4" />}
                    {ORDER_STATUS_TEXT[stage]}
                  </p>
                  {at && (
                    <p className="text-mute text-sm">
                      {new Date(at).toLocaleDateString("en-US", { dateStyle: "medium" })}
                    </p>
                  )}
                </li>
              );
            })}
          </ol>
          {order.status === "REFUNDED" && <p className="mt-3">This order was refunded.</p>}
        </section>
      )}

      <section aria-labelledby="items-title">
        <h3 id="items-title" className="condensed text-2xl font-semibold">
          Items
        </h3>
        <ul className="divide-line border-line mt-3 divide-y border-y">
          {order.items.map((item) => (
            <li key={item.variantId} className="py-4">
              <div className="flex justify-between gap-4">
                <div>
                  <p className="font-semibold">{item.productName}</p>
                  <p className="text-mute text-sm">
                    {item.variantLabel}, quantity {item.quantity}
                  </p>
                </div>
                <p className="figures font-semibold">{formatMoney(item.lineTotal)}</p>
              </div>
              {order.status === "DELIVERED" && <ReviewForm productId={item.productId} productName={item.productName} />}
            </li>
          ))}
        </ul>
        <dl className="figures mt-4 ml-auto max-w-xs space-y-1 text-sm">
          <Row label="Subtotal" value={formatMoney(order.subtotal)} />
          {order.discount.amount > 0 && <Row label={`Code ${order.couponCode ?? ""}`} value={`−${formatMoney(order.discount)}`} />}
          <Row label="Shipping" value={order.shipping.amount === 0 ? "Free" : formatMoney(order.shipping)} />
          <div className="flex justify-between border-t border-line pt-2 text-base font-semibold">
            <dt>Total</dt>
            <dd>{formatMoney(order.total)}</dd>
          </div>
        </dl>
      </section>

      <section aria-labelledby="ship-title">
        <h3 id="ship-title" className="condensed text-2xl font-semibold">
          Shipping to
        </h3>
        <address className="mt-2 not-italic">
          {order.shippingAddress.fullName}
          <br />
          {order.shippingAddress.line1}
          {order.shippingAddress.line2 && (
            <>
              <br />
              {order.shippingAddress.line2}
            </>
          )}
          <br />
          {order.shippingAddress.city}
          {order.shippingAddress.region ? `, ${order.shippingAddress.region}` : ""} {order.shippingAddress.postalCode}
          <br />
          {order.shippingAddress.country}
        </address>
      </section>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function PayNow({ order, lastPayment, onChange }: { order: OrderDetails; lastPayment?: Payment; onChange: () => void }) {
  const [token, setToken] = useState<string>(TEST_CARDS[0].token);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const inFlight = lastPayment && (lastPayment.status === "PENDING" || lastPayment.status === "REQUIRES_ACTION");

  async function act(action: () => Promise<void>) {
    setBusy(true);
    setMessage(null);
    try {
      await action();
    } catch (e) {
      setMessage(e instanceof ApiError ? e.message : "Something went wrong. Try again.");
    } finally {
      setBusy(false);
      onChange();
    }
  }

  return (
    <section aria-labelledby="pay-title" className="bg-tile rounded-2xl p-6">
      <h3 id="pay-title" className="condensed text-2xl font-semibold">
        Complete your payment
      </h3>
      <p className="mt-1">
        We’re holding your items until{" "}
        {new Date(order.paymentDueAt).toLocaleTimeString("en-US", { timeStyle: "short" })}.
        {order.status === "PAYMENT_FAILED" && lastPayment?.failureMessage ? ` Your last payment failed: ${lastPayment.failureMessage}.` : ""}
      </p>
      {inFlight ? (
        <p className="mt-3">
          {lastPayment.status === "REQUIRES_ACTION"
            ? "Your bank asked for extra verification. This order updates automatically once it’s confirmed."
            : "Your payment is being processed. This page updates once it’s confirmed."}
        </p>
      ) : (
        <div className="mt-4 flex flex-wrap items-end gap-3">
          <TestCardSelect value={token} onChange={setToken} />
          <button
            type="button"
            disabled={busy}
            onClick={() => act(async () => {
              const payment = await payOrder(order.orderNumber, token);
              if (payment.status === "FAILED") setMessage(`Payment declined: ${payment.failureMessage ?? "try another card"}.`);
            })}
            className="bg-brand hover:bg-brand-deep text-white rounded-lg px-6 py-3 font-semibold disabled:opacity-60"
          >
            {busy ? "Paying…" : `Pay ${formatMoney(order.total)}`}
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => act(async () => {
              await api(`/orders/${encodeURIComponent(order.orderNumber)}/cancel`, { method: "POST" });
            })}
            className="text-mute underline underline-offset-4 hover:text-ink"
          >
            Cancel order
          </button>
        </div>
      )}
      {message && (
        <p role="alert" className="text-split mt-3 text-sm">
          {message}
        </p>
      )}
    </section>
  );
}

function ReviewForm({ productId, productName }: { productId: number; productName: string }) {
  const [open, setOpen] = useState(false);
  const [rating, setRating] = useState(0);
  const [state, setState] = useState<{ kind: "done" | "error"; text: string } | null>(null);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await api("/reviews", {
        method: "POST",
        body: { productId, rating, title: String(form.get("title") ?? ""), comment: String(form.get("comment") ?? "") },
      });
      setState({ kind: "done", text: "Thanks. Your review will appear once it’s approved." });
    } catch (e) {
      setState({ kind: "error", text: e instanceof ApiError ? e.message : "Your review couldn’t be sent. Try again." });
    }
  }

  if (state?.kind === "done") return <p className="text-mute mt-2 text-sm">{state.text}</p>;
  if (!open) {
    return (
      <button type="button" onClick={() => setOpen(true)} className="text-brand mt-2 text-sm font-medium underline underline-offset-4">
        Review {productName}
      </button>
    );
  }
  return (
    <form onSubmit={onSubmit} className="mt-3 space-y-3">
      <fieldset>
        <legend className="text-sm font-medium">Your rating</legend>
        <div className="mt-1 flex gap-1">
          {[1, 2, 3, 4, 5].map((n) => (
            <label key={n} className="cursor-pointer">
              <input type="radio" name="rating" value={n} className="peer sr-only" onChange={() => setRating(n)} required />
              <Star aria-hidden className={`h-7 w-7 peer-focus-visible:outline-2 ${n <= rating ? "fill-brand text-brand" : "text-line"}`} />
              <span className="sr-only">{n} star{n > 1 ? "s" : ""}</span>
            </label>
          ))}
        </div>
      </fieldset>
      <input name="title" required maxLength={150} placeholder="Headline" aria-label="Review headline" className="border-line w-full rounded-lg border bg-white px-3 py-2" />
      <textarea name="comment" maxLength={5000} rows={3} placeholder="How did they run?" aria-label="Review" className="border-line w-full rounded-lg border bg-white px-3 py-2" />
      {state?.kind === "error" && <p role="alert" className="text-split text-sm">{state.text}</p>}
      <button disabled={rating === 0} className="bg-brand hover:bg-brand-deep text-white rounded-lg px-5 py-2 font-semibold disabled:opacity-50">
        Submit review
      </button>
    </form>
  );
}

