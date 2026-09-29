"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";
import { useRequireAuth } from "@/lib/hooks/useRequireAuth";
import type { OrderDetails, Payment } from "@/lib/types";

export default function ConfirmationPage() {
  const user = useRequireAuth();
  const { orderNumber } = useParams<{ orderNumber: string }>();
  const [order, setOrder] = useState<OrderDetails | null>(null);
  const [payment, setPayment] = useState<Payment | null>(null);

  useEffect(() => {
    if (!user) return;
    const path = `/orders/${encodeURIComponent(orderNumber)}`;
    Promise.all([api<OrderDetails>(path), api<Payment[]>(`${path}/payments`)]).then(([o, p]) => {
      setOrder(o);
      setPayment(p.at(-1) ?? null);
    });
  }, [user, orderNumber]);

  if (!order) return <div className="mx-auto max-w-3xl px-4 py-16" aria-busy="true" />;

  const paid = order.status === "PAID";
  const headline = paid ? "You’re on the start line." : "Your order is placed.";
  const detail = paid
    ? `We’ve received your payment of ${formatMoney(order.total)}. A confirmation is on its way to ${order.customerEmail}.`
    : payment?.status === "FAILED"
      ? `Your payment was declined${payment.failureMessage ? ` (${payment.failureMessage})` : ""}. We’re holding your items for a little while so you can try again.`
      : payment?.status === "REQUIRES_ACTION"
        ? "Your bank needs extra verification. Your order updates automatically once it’s confirmed."
        : "We’re confirming your payment. This usually takes a moment.";

  return (
    <div className="mx-auto max-w-3xl px-4 py-16">
      <p className="text-mute figures">Order {order.orderNumber}</p>
      <h1 className="display mt-2 text-6xl">{headline}</h1>
      <p className="mt-4 max-w-prose text-lg">{detail}</p>
      <div className="mt-8 flex flex-wrap gap-3">
        <Link href={`/account/orders/${order.orderNumber}`} className="bg-brand hover:bg-brand-deep text-white rounded-lg px-6 py-3 font-semibold">
          {paid ? "Track your order" : "Pay for your order"}
        </Link>
        <Link href="/shop" className="border-ink rounded-lg border px-6 py-3 font-semibold">
          Keep shopping
        </Link>
      </div>
      <ul className="divide-line border-line mt-12 divide-y border-y">
        {order.items.map((item) => (
          <li key={item.variantId} className="flex justify-between gap-4 py-3">
            <span>
              {item.productName} <span className="text-mute">{item.variantLabel}</span> × {item.quantity}
            </span>
            <span className="figures">{formatMoney(item.lineTotal)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
