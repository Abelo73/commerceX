"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Search } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Pager } from "@/components/admin/Pager";
import { ApiError, api } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";
import { type AdminOrderSummary, STAFF_STATUS_TEXT } from "@/lib/admin";
import type { OrderStatus, Page } from "@/lib/types";

const STATUSES = Object.keys(STAFF_STATUS_TEXT) as OrderStatus[];
const SIZE = 20;

export default function AdminOrdersPage() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const status = params.get("status") ?? "";
  const q = params.get("q") ?? "";
  const page = Math.max(0, Number(params.get("page")) || 0);
  const query = new URLSearchParams({ page: String(page), size: String(SIZE) });
  if (status) query.set("status", status);
  if (q) query.set("q", q);
  const key = query.toString();

  const [result, setResult] = useState<{ key: string; page: Page<AdminOrderSummary> } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState(q);

  useEffect(() => {
    let active = true;
    api<Page<AdminOrderSummary>>(`/admin/orders?${key}`)
      .then((p) => {
        if (!active) return;
        setResult({ key, page: p });
        setError(null);
      })
      .catch((e) => active && setError(e instanceof ApiError ? e.message : "Could not load orders."));
    return () => {
      active = false;
    };
  }, [key]);

  const navigate = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params.toString());
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (!("page" in changes)) next.delete("page");
    router.replace(`${pathname}${next.size ? `?${next}` : ""}`, { scroll: false });
  };

  const orders = result?.page;
  const loading = result?.key !== key;

  return (
    <div className="mx-auto max-w-6xl">
      <h1 className="display text-4xl">Orders</h1>

      <div className="mt-6 flex flex-wrap gap-3">
        <form
          role="search"
          onSubmit={(e) => {
            e.preventDefault();
            navigate({ q: search.trim() });
          }}
          className="relative min-w-0 flex-1 sm:max-w-sm"
        >
          <label htmlFor="order-search" className="sr-only">
            Order number or customer email
          </label>
          <Search className="text-mute pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2" aria-hidden />
          <input
            id="order-search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Order number or exact email"
            maxLength={254}
            className="border-line focus:border-brand min-h-11 w-full rounded-lg border bg-white py-2 pr-3 pl-9 outline-none"
          />
        </form>
        <label className="sr-only" htmlFor="order-status">
          Status
        </label>
        <select
          id="order-status"
          value={status}
          onChange={(e) => navigate({ status: e.target.value })}
          className="border-line focus:border-brand min-h-11 rounded-lg border bg-white px-3 outline-none"
        >
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {STAFF_STATUS_TEXT[s]}
            </option>
          ))}
        </select>
      </div>

      {error && (
        <p role="alert" className="text-split mt-6">
          {error}
        </p>
      )}

      <div className={`border-line mt-6 overflow-x-auto rounded-card border bg-white transition-opacity ${loading ? "opacity-60" : ""}`} aria-busy={loading}>
        <table className="figures w-full min-w-[40rem] text-left text-sm">
          <thead className="text-mute border-line border-b">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Order</th>
              <th scope="col" className="px-4 py-3 font-medium">Placed</th>
              <th scope="col" className="px-4 py-3 font-medium">Customer</th>
              <th scope="col" className="px-4 py-3 font-medium">Status</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Items</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Total</th>
            </tr>
          </thead>
          <tbody className="divide-line divide-y">
            {orders?.content.map((o) => (
              <tr key={o.id} className="hover:bg-surface relative">
                <td className="px-4 py-3 font-semibold whitespace-nowrap">
                  <Link href={`/admin/orders/${o.orderNumber}`} className="text-brand after:absolute after:inset-0 hover:underline">
                    {o.orderNumber}
                  </Link>
                </td>
                <td className="text-mute px-4 py-3 whitespace-nowrap">
                  {new Date(o.placedAt).toLocaleString("en-US", { dateStyle: "medium", timeStyle: "short" })}
                </td>
                <td className="max-w-56 truncate px-4 py-3">{o.customerEmail}</td>
                <td className="px-4 py-3">
                  <StatusBadge status={o.status} />
                </td>
                <td className="px-4 py-3 text-right">{o.itemCount}</td>
                <td className="px-4 py-3 text-right font-semibold">{formatMoney(o.total)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {orders && orders.content.length === 0 && (
          <p className="text-mute px-4 py-10 text-center">No orders match these filters.</p>
        )}
        {!orders && !error && <div className="h-64" />}
      </div>

      {orders && <Pager page={orders.page} totalPages={orders.totalPages} total={orders.totalElements} noun="order" onPage={(p) => navigate({ page: p ? String(p) : "" })} />}
    </div>
  );
}
