"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";
import type { Money, Page } from "@/lib/types";
import { useAuth } from "@/components/providers";

type OrderSummary = { id: number; orderNumber: string; status: string; total: Money; itemCount: number; placedAt: string };

const STATUS_TEXT: Record<string, string> = {
  PENDING_PAYMENT: "Awaiting payment",
  PAYMENT_FAILED: "Payment failed",
  PAID: "Paid",
  PROCESSING: "Being prepared",
  PACKED: "Packed",
  SHIPPED: "On its way",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
  REFUNDED: "Refunded",
};

export default function AccountPage() {
  const { user, ready, signOut } = useAuth();
  const router = useRouter();
  const [orders, setOrders] = useState<OrderSummary[] | null>(null);

  useEffect(() => {
    if (ready && !user) router.replace("/login?next=/account");
    if (user) api<Page<OrderSummary>>("/orders?size=20").then((p) => setOrders(p.content)).catch(() => setOrders([]));
  }, [ready, user, router]);

  if (!user) return <div className="mx-auto max-w-3xl px-4 py-16" aria-busy="true" />;

  return (
    <div className="mx-auto max-w-3xl px-4 py-16">
      <div className="flex items-end justify-between gap-4">
        <h1 className="display text-6xl">Hi, {user.firstName}</h1>
        <button type="button" onClick={() => signOut().then(() => router.push("/"))} className="text-mute text-sm underline underline-offset-4">
          Sign out
        </button>
      </div>
      <p className="text-mute mt-2">{user.email}</p>

      <h2 className="condensed mt-12 text-2xl font-semibold">Your orders</h2>
      {orders === null ? (
        <p className="text-mute mt-4">Loading your orders…</p>
      ) : orders.length === 0 ? (
        <p className="mt-4">
          No orders yet.{" "}
          <Link href="/shop" className="text-track font-medium underline underline-offset-4">
            Find your next pair
          </Link>
        </p>
      ) : (
        <ul className="divide-line border-line mt-4 divide-y border-y">
          {orders.map((order) => (
            <li key={order.id} className="figures flex flex-wrap items-center justify-between gap-2 py-4">
              <span className="font-semibold">{order.orderNumber}</span>
              <span className="text-mute text-sm">{new Date(order.placedAt).toLocaleDateString("en-US", { dateStyle: "medium" })}</span>
              <span>{STATUS_TEXT[order.status] ?? order.status}</span>
              <span className="font-semibold">{formatMoney(order.total)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
