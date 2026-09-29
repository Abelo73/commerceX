"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";
import { ORDER_STATUS_TEXT, isUnpaid } from "@/lib/orders";
import type { Money, OrderStatus, Page } from "@/lib/types";

type OrderSummary = { id: number; orderNumber: string; status: OrderStatus; total: Money; itemCount: number; placedAt: string };

export default function OrdersPage() {
  const [orders, setOrders] = useState<OrderSummary[] | null>(null);

  useEffect(() => {
    api<Page<OrderSummary>>("/orders?size=50").then((p) => setOrders(p.content)).catch(() => setOrders([]));
  }, []);

  if (orders === null) return <p className="text-mute">Loading your orders…</p>;
  if (orders.length === 0) {
    return (
      <p>
        No orders yet.{" "}
        <Link href="/shop" className="text-brand font-medium underline underline-offset-4">
          Find your next pair
        </Link>
      </p>
    );
  }
  return (
    <ul className="divide-line border-line divide-y border-y">
      {orders.map((order) => (
        <li key={order.id}>
          <Link href={`/account/orders/${order.orderNumber}`} className="figures hover:bg-tile/60 grid grid-cols-2 gap-1 px-2 py-4 sm:grid-cols-4 sm:items-center">
            <span className="font-semibold">{order.orderNumber}</span>
            <span className="text-mute text-sm sm:order-none">
              {new Date(order.placedAt).toLocaleDateString("en-US", { dateStyle: "medium" })}, {order.itemCount} item{order.itemCount === 1 ? "" : "s"}
            </span>
            <span className={isUnpaid(order.status) ? "text-split" : ""}>{ORDER_STATUS_TEXT[order.status]}</span>
            <span className="font-semibold sm:text-right">{formatMoney(order.total)}</span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
