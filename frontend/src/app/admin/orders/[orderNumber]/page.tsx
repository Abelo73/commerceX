"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { useAuth } from "@/components/providers";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { ApiError, api, newIdempotencyKey } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";
import { type AdminPayment, NEXT_STEP, STAFF_STATUS_TEXT, canCancel, canRefund, hasRole } from "@/lib/admin";
import type { Money, OrderDetails, OrderStatus } from "@/lib/types";

const when = (iso: string) => new Date(iso).toLocaleString("en-US", { dateStyle: "medium", timeStyle: "short" });

export default function AdminOrderPage() {
  const { orderNumber } = useParams<{ orderNumber: string }>();
  const { user } = useAuth();
  const [order, setOrder] = useState<OrderDetails | null>(null);
  const [payments, setPayments] = useState<AdminPayment[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    () =>
      Promise.all([
        api<OrderDetails>(`/admin/orders/${encodeURIComponent(orderNumber)}`),
        api<AdminPayment[]>(`/admin/orders/${encodeURIComponent(orderNumber)}/payments`),
      ]),
    [orderNumber],
  );

  const reload = useCallback(
    () =>
      load().then(([o, p]) => {
        setOrder(o);
        setPayments(p);
      }),
    [load],
  );

  useEffect(() => {
    let active = true;
    load()
      .then(([o, p]) => {
        if (!active) return;
        setOrder(o);
        setPayments(p);
      })
      .catch((e) => active && setError(e instanceof ApiError && e.status === 404 ? "Order not found." : "Could not load this order."));
    return () => {
      active = false;
    };
  }, [load]);

  if (error) {
    return (
      <div className="mx-auto max-w-5xl">
        <BackLink />
        <p role="alert" className="text-split mt-6">{error}</p>
      </div>
    );
  }
  if (!order) return <div className="mx-auto max-w-5xl" aria-busy="true"><BackLink /></div>;

  const address = order.shippingAddress;
  const isAdmin = hasRole(user, "ADMIN");

  return (
    <div className="mx-auto max-w-5xl">
      <BackLink />
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <h1 className="display text-4xl">{order.orderNumber}</h1>
        <StatusBadge status={order.status} />
      </div>
      <p className="text-mute mt-1 text-sm">
        Placed {when(order.placedAt)} by {order.customerEmail}
      </p>

      <div className="mt-8 grid gap-4 lg:grid-cols-[1fr_20rem]">
        <div className="space-y-4">
          <Card title="Items">
            <ul className="divide-line divide-y">
              {order.items.map((item) => (
                <li key={item.variantId} className="figures flex justify-between gap-4 py-3 text-sm">
                  <div>
                    <p className="font-medium">{item.productName}</p>
                    <p className="text-mute">
                      {item.variantLabel} · {item.sku} · {item.quantity} × {formatMoney(item.unitPrice)}
                    </p>
                  </div>
                  <p className="font-semibold">{formatMoney(item.lineTotal)}</p>
                </li>
              ))}
            </ul>
            <dl className="border-line figures mt-2 space-y-1 border-t pt-3 text-sm">
              <Row label="Subtotal" value={order.subtotal} />
              {order.discount.amount > 0 && <Row label={`Discount${order.couponCode ? ` (${order.couponCode})` : ""}`} value={order.discount} negative />}
              <Row label={`Shipping (${order.shippingMethod.toLowerCase()})`} value={order.shipping} />
              {order.tax.amount > 0 && <Row label="Tax" value={order.tax} />}
              <div className="flex justify-between pt-1 text-base font-bold">
                <dt>Total</dt>
                <dd>{formatMoney(order.total)}</dd>
              </div>
            </dl>
          </Card>

          <Card title="Payments">
            {payments.length === 0 ? (
              <p className="text-mute text-sm">No payment attempts yet.</p>
            ) : (
              <ul className="divide-line divide-y">
                {payments.map((p) => (
                  <PaymentRow key={p.payment.id} entry={p} orderStatus={order.status} isAdmin={isAdmin} onChange={reload} />
                ))}
              </ul>
            )}
          </Card>
        </div>

        <div className="space-y-4">
          <Fulfilment order={order} onChange={reload} />
          <Card title="Ship to">
            <address className="text-sm not-italic">
              {address.fullName}
              <br />
              {address.line1}
              {address.line2 && <><br />{address.line2}</>}
              <br />
              {address.postalCode} {address.city}
              {address.region && `, ${address.region}`}
              <br />
              {address.country}
              {address.phone && <><br /><span className="text-mute">{address.phone}</span></>}
            </address>
          </Card>
          <Card title="History">
            <ol className="space-y-3 text-sm">
              {[...order.history].reverse().map((h, i) => (
                <li key={i}>
                  <p className="font-medium">{STAFF_STATUS_TEXT[h.to as OrderStatus] ?? h.to}</p>
                  <p className="text-mute">
                    {when(h.at)}
                    {h.reason && ` · ${h.reason}`}
                  </p>
                </li>
              ))}
            </ol>
          </Card>
        </div>
      </div>
    </div>
  );
}

function BackLink() {
  return (
    <Link href="/admin/orders" className="text-mute inline-flex items-center gap-1.5 text-sm hover:text-ink">
      <ArrowLeft className="size-4" aria-hidden /> All orders
    </Link>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="border-line rounded-card border bg-white p-5">
      <h2 className="condensed mb-2 font-bold">{title}</h2>
      {children}
    </section>
  );
}

function Row({ label, value, negative }: { label: string; value: Money; negative?: boolean }) {
  return (
    <div className="flex justify-between">
      <dt className="text-mute">{label}</dt>
      <dd>{negative ? "−" : ""}{formatMoney(value)}</dd>
    </div>
  );
}

function Fulfilment({ order, onChange }: { order: OrderDetails; onChange: () => Promise<void> }) {
  const next = NEXT_STEP[order.status];
  const cancellable = canCancel(order.status);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!next && !cancellable) return null;

  const move = async (to: OrderStatus) => {
    if (to === "CANCELLED" && !window.confirm(`Cancel ${order.orderNumber}? Its reserved stock is released.`)) return;
    setBusy(true);
    setError(null);
    try {
      await api(`/admin/orders/${encodeURIComponent(order.orderNumber)}/transitions`, {
        method: "POST",
        body: { toStatus: to, reason: reason.trim() || undefined },
      });
      setReason("");
      await onChange();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not update the order.");
      await onChange().catch(() => undefined);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Fulfilment">
      <label htmlFor="transition-reason" className="text-mute block text-sm">
        Note (optional)
      </label>
      <input
        id="transition-reason"
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        maxLength={500}
        placeholder={next ? "e.g. tracking number" : "e.g. customer asked"}
        className="border-line focus:border-brand mt-1 min-h-10 w-full rounded-lg border px-3 text-sm outline-none"
      />
      <div className="mt-3 flex flex-wrap gap-2">
        {next && (
          <button
            type="button"
            disabled={busy}
            onClick={() => move(next.to)}
            className="bg-brand hover:bg-brand-deep min-h-10 flex-1 rounded-lg px-4 text-sm font-semibold text-white disabled:opacity-60"
          >
            {next.label}
          </button>
        )}
        {cancellable && (
          <button
            type="button"
            disabled={busy}
            onClick={() => move("CANCELLED")}
            className="border-split text-split hover:bg-split/5 min-h-10 flex-1 rounded-lg border px-4 text-sm font-semibold disabled:opacity-60"
          >
            Cancel order
          </button>
        )}
      </div>
      {error && <p role="alert" className="text-split mt-2 text-sm">{error}</p>}
    </Card>
  );
}

function fractionDigits(currency: string) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).resolvedOptions().maximumFractionDigits ?? 2;
}

function PaymentRow({
  entry,
  orderStatus,
  isAdmin,
  onChange,
}: {
  entry: AdminPayment;
  orderStatus: OrderStatus;
  isAdmin: boolean;
  onChange: () => Promise<void>;
}) {
  const { payment, refunds } = entry;
  const currency = payment.amount.currency;
  const remaining = payment.amount.amount - payment.refunded.amount;
  const pendingRefund = refunds.some((r) => r.status === "PENDING");
  const refundable = isAdmin && payment.status === "SUCCEEDED" && remaining > 0 && !pendingRefund && canRefund(orderStatus);

  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [key, setKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const digits = fractionDigits(currency);
    const minor = amount.trim() ? Math.round(Number(amount) * 10 ** digits) : undefined;
    if (minor !== undefined && (!Number.isFinite(minor) || minor <= 0 || minor > remaining)) {
      setError(`Enter an amount up to ${formatMoney({ amount: remaining, currency })}.`);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api(`/admin/payments/${payment.id}/refunds`, {
        method: "POST",
        headers: { "Idempotency-Key": key },
        body: { amount: minor, reason: reason.trim() },
      });
      setOpen(false);
      await onChange();
    } catch (err) {
      // Keep the key: resubmitting after a timeout must not refund twice.
      setError(err instanceof ApiError ? (err.fieldError("amount") ?? err.message) : "Refund failed. Try again.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <li className="py-3 text-sm">
      <div className="figures flex flex-wrap items-baseline justify-between gap-2">
        <p>
          <span className="font-semibold">{formatMoney(payment.amount)}</span>{" "}
          <span className={payment.status === "SUCCEEDED" ? "text-ok" : payment.status === "FAILED" ? "text-split" : "text-warn"}>
            {payment.status.toLowerCase().replace("_", " ")}
          </span>
          {payment.refunded.amount > 0 && <span className="text-mute"> · {formatMoney(payment.refunded)} refunded</span>}
        </p>
        <p className="text-mute">{when(payment.createdAt)}</p>
      </div>
      <p className="text-mute mt-0.5">
        {entry.provider}
        {entry.providerReference && ` · ${entry.providerReference}`}
        {payment.failureMessage && ` · ${payment.failureMessage}`}
      </p>
      {refunds.length > 0 && (
        <ul className="border-line mt-2 space-y-1 border-l-2 pl-3">
          {refunds.map((r) => (
            <li key={r.id} className="figures">
              Refund {formatMoney(r.amount)} · {r.status.toLowerCase()}
              {r.reason && <span className="text-mute"> · {r.reason}</span>}
              {r.failureMessage && <span className="text-split"> · {r.failureMessage}</span>}
            </li>
          ))}
        </ul>
      )}
      {refundable && !open && (
        <button
          type="button"
          onClick={() => {
            setKey(newIdempotencyKey("refund"));
            setAmount("");
            setReason("");
            setError(null);
            setOpen(true);
          }}
          className="text-brand mt-2 font-semibold hover:underline"
        >
          Refund…
        </button>
      )}
      {!refundable && isAdmin && payment.status === "SUCCEEDED" && remaining > 0 && !canRefund(orderStatus) && (
        <p className="text-mute mt-2">Refunds open again once the order is delivered.</p>
      )}
      {open && (
        <form onSubmit={submit} className="bg-surface mt-3 space-y-3 rounded-lg p-3">
          <div className="grid gap-3 sm:grid-cols-[8rem_1fr]">
            <div>
              <label htmlFor={`refund-amount-${payment.id}`} className="text-mute block text-xs font-medium">
                Amount ({currency})
              </label>
              <input
                id={`refund-amount-${payment.id}`}
                inputMode="decimal"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder={(remaining / 10 ** fractionDigits(currency)).toFixed(fractionDigits(currency))}
                className="border-line focus:border-brand mt-1 min-h-10 w-full rounded-lg border bg-white px-3 outline-none"
              />
            </div>
            <div>
              <label htmlFor={`refund-reason-${payment.id}`} className="text-mute block text-xs font-medium">
                Reason
              </label>
              <input
                id={`refund-reason-${payment.id}`}
                required
                maxLength={500}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                className="border-line focus:border-brand mt-1 min-h-10 w-full rounded-lg border bg-white px-3 outline-none"
              />
            </div>
          </div>
          <p className="text-mute text-xs">Leave the amount empty to refund the full {formatMoney({ amount: remaining, currency })}.</p>
          {error && <p role="alert" className="text-split">{error}</p>}
          <div className="flex gap-2">
            <button type="submit" disabled={busy} className="bg-brand hover:bg-brand-deep min-h-10 rounded-lg px-4 font-semibold text-white disabled:opacity-60">
              {busy ? "Refunding…" : "Refund"}
            </button>
            <button type="button" onClick={() => setOpen(false)} className="text-mute min-h-10 px-3 hover:text-ink">
              Cancel
            </button>
          </div>
        </form>
      )}
    </li>
  );
}
