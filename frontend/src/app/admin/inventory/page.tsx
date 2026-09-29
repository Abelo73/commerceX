"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Fragment, useEffect, useState } from "react";
import { AlertTriangle, CircleCheck, CircleX } from "lucide-react";
import { Pager } from "@/components/admin/Pager";
import { ApiError, api } from "@/lib/client-api";
import type { StockRow } from "@/lib/admin";
import type { Page } from "@/lib/types";

const SIZE = 25;
type AdjustmentType = "RESTOCK" | "ADJUSTMENT" | "RETURN";

export default function InventoryPage() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const low = params.get("low") === "1";
  const page = Math.max(0, Number(params.get("page")) || 0);
  const key = `lowStockOnly=${low}&page=${page}&size=${SIZE}`;

  const [result, setResult] = useState<{ key: string; page: Page<StockRow> } | null>(null);
  const [version, setVersion] = useState(0);
  const [editing, setEditing] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    api<Page<StockRow>>(`/admin/stock?${key}`)
      .then((p) => {
        if (!active) return;
        setResult({ key, page: p });
        setError(null);
      })
      .catch((e) => active && setError(e instanceof ApiError ? e.message : "Could not load inventory."));
    return () => {
      active = false;
    };
  }, [key, version]);

  const navigate = (next: { low?: boolean; page?: number }) => {
    const q = new URLSearchParams();
    if (next.low ?? low) q.set("low", "1");
    if (next.page) q.set("page", String(next.page));
    setEditing(null);
    router.replace(`${pathname}${q.size ? `?${q}` : ""}`, { scroll: false });
  };

  const stock = result?.page;
  const loading = result?.key !== key;

  return (
    <div className="mx-auto max-w-6xl">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="display text-4xl">Inventory</h1>
        <label className="inline-flex min-h-11 cursor-pointer items-center gap-2 text-sm font-medium">
          <input type="checkbox" checked={low} onChange={(e) => navigate({ low: e.target.checked, page: 0 })} className="accent-brand size-4" />
          Low stock only
        </label>
      </div>
      <p className="text-mute mt-1 text-sm">Lowest availability first. Available = on hand − reserved for unpaid orders.</p>

      {error && <p role="alert" className="text-split mt-6">{error}</p>}

      <div className={`border-line mt-6 overflow-x-auto rounded-card border bg-white transition-opacity ${loading ? "opacity-60" : ""}`} aria-busy={loading}>
        <table className="figures w-full min-w-[46rem] text-left text-sm">
          <thead className="text-mute border-line border-b">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Product</th>
              <th scope="col" className="px-4 py-3 font-medium">Stock</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Available</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">On hand</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Reserved</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">Sold</th>
              <th scope="col" className="px-4 py-3"><span className="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody className="divide-line divide-y">
            {stock?.content.map((row) => (
              <Fragment key={row.variantId}>
                <tr>
                  <td className="px-4 py-3">
                    <p className="font-medium">{row.productName ?? `Variant ${row.variantId}`}</p>
                    <p className="text-mute">{[row.label, row.sku].filter(Boolean).join(" · ")}</p>
                  </td>
                  <td className="px-4 py-3">
                    <StockState row={row} />
                  </td>
                  <td className="px-4 py-3 text-right font-semibold">{row.available}</td>
                  <td className="px-4 py-3 text-right">{row.onHand}</td>
                  <td className="px-4 py-3 text-right">{row.reserved}</td>
                  <td className="text-mute px-4 py-3 text-right">{row.sold}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      type="button"
                      aria-expanded={editing === row.variantId}
                      onClick={() => setEditing(editing === row.variantId ? null : row.variantId)}
                      className="text-brand min-h-9 font-semibold hover:underline"
                    >
                      Adjust
                    </button>
                  </td>
                </tr>
                {editing === row.variantId && (
                  <tr className="bg-surface">
                    <td colSpan={7} className="px-4 py-4">
                      <AdjustForm
                        row={row}
                        onDone={() => {
                          setEditing(null);
                          setVersion((v) => v + 1);
                        }}
                        onCancel={() => setEditing(null)}
                      />
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
        {stock && stock.content.length === 0 && (
          <p className="text-mute px-4 py-10 text-center">{low ? "Nothing is low on stock." : "No stock records yet."}</p>
        )}
        {!stock && !error && <div className="h-64" />}
      </div>

      {stock && <Pager page={stock.page} totalPages={stock.totalPages} total={stock.totalElements} noun="variant" onPage={(p) => navigate({ page: p })} />}
    </div>
  );
}

function StockState({ row }: { row: StockRow }) {
  const [Icon, text, color] =
    row.available <= 0
      ? [CircleX, "Sold out", "text-split"]
      : row.lowStock
        ? [AlertTriangle, `Low (≤ ${row.lowStockThreshold})`, "text-warn"]
        : [CircleCheck, "In stock", "text-ok"];
  return (
    <span className={`inline-flex items-center gap-1.5 font-medium whitespace-nowrap ${color}`}>
      <Icon className="size-4" aria-hidden /> {text}
    </span>
  );
}

function AdjustForm({ row, onDone, onCancel }: { row: StockRow; onDone: () => void; onCancel: () => void }) {
  const [type, setType] = useState<AdjustmentType>("RESTOCK");
  const [quantity, setQuantity] = useState("");
  const [reason, setReason] = useState("");
  const [threshold, setThreshold] = useState(String(row.lowStockThreshold));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (request: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await request();
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? (e.fieldError("quantity") ?? e.fieldError("lowStockThreshold") ?? e.message) : "Could not save.");
    } finally {
      setBusy(false);
    }
  };

  const submitAdjustment = (e: React.FormEvent) => {
    e.preventDefault();
    const n = Number(quantity);
    if (!Number.isInteger(n) || n === 0 || (type !== "ADJUSTMENT" && n < 0)) {
      setError(type === "ADJUSTMENT" ? "Enter a whole number other than 0 (negative removes stock)." : "Enter a positive whole number.");
      return;
    }
    run(() =>
      api(`/admin/inventory/${row.variantId}/adjustments`, {
        method: "POST",
        body: { type, quantity: n, reason: reason.trim() || undefined },
      }),
    );
  };

  const field = "border-line focus:border-brand mt-1 min-h-10 w-full rounded-lg border bg-white px-3 outline-none";

  return (
    <div className="grid gap-6 md:grid-cols-[1fr_auto]">
      <form onSubmit={submitAdjustment} className="grid gap-3 sm:grid-cols-[10rem_7rem_1fr_auto] sm:items-end">
        <div>
          <label htmlFor={`type-${row.variantId}`} className="text-mute block text-xs font-medium">Change</label>
          <select id={`type-${row.variantId}`} value={type} onChange={(e) => setType(e.target.value as AdjustmentType)} className={field}>
            <option value="RESTOCK">Restock (+)</option>
            <option value="RETURN">Customer return (+)</option>
            <option value="ADJUSTMENT">Correction (±)</option>
          </select>
        </div>
        <div>
          <label htmlFor={`qty-${row.variantId}`} className="text-mute block text-xs font-medium">Quantity</label>
          <input id={`qty-${row.variantId}`} inputMode="numeric" required value={quantity} onChange={(e) => setQuantity(e.target.value)} placeholder={type === "ADJUSTMENT" ? "-2" : "12"} className={field} />
        </div>
        <div>
          <label htmlFor={`reason-${row.variantId}`} className="text-mute block text-xs font-medium">Reason</label>
          <input id={`reason-${row.variantId}`} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. delivery from supplier" className={field} />
        </div>
        <button type="submit" disabled={busy} className="bg-brand hover:bg-brand-deep min-h-10 rounded-lg px-4 font-semibold text-white disabled:opacity-60">
          Save
        </button>
      </form>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          run(() => api(`/admin/inventory/${row.variantId}/threshold`, { method: "PUT", body: { lowStockThreshold: Number(threshold) } }));
        }}
        className="flex items-end gap-2"
      >
        <div>
          <label htmlFor={`threshold-${row.variantId}`} className="text-mute block text-xs font-medium">Low-stock alert at</label>
          <input id={`threshold-${row.variantId}`} type="number" min={0} max={1000000} required value={threshold} onChange={(e) => setThreshold(e.target.value)} className={`${field} w-24`} />
        </div>
        <button type="submit" disabled={busy} className="border-line min-h-10 rounded-lg border bg-white px-3 font-semibold hover:bg-tile disabled:opacity-60">
          Set
        </button>
      </form>
      {error && <p role="alert" className="text-split md:col-span-2">{error}</p>}
      <button type="button" onClick={onCancel} className="text-mute justify-self-start text-sm hover:text-ink md:col-span-2">
        Close
      </button>
    </div>
  );
}
