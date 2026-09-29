"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AlertTriangle, ChevronRight, CircleCheck, CircleX, Clock, PackageOpen } from "lucide-react";
import { RevenueChart } from "@/components/admin/RevenueChart";
import { ApiError, api } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";
import { type Dashboard, activityText, compactNumber, relativeTime } from "@/lib/admin";

const RANGES = [
  { days: 1, label: "Today" },
  { days: 7, label: "7 days" },
  { days: 30, label: "30 days" },
  { days: 90, label: "90 days" },
];

export default function DashboardPage() {
  const [days, setDays] = useState(30);
  const [data, setData] = useState<Dashboard | null>(null);
  const [loadedDays, setLoadedDays] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    api<Dashboard>(`/admin/dashboard?days=${days}`)
      .then((d) => {
        if (!active) return;
        setData(d);
        setLoadedDays(days);
        setError(null);
      })
      .catch((e) => active && setError(e instanceof ApiError ? e.message : "Could not load the dashboard."));
    return () => {
      active = false;
    };
  }, [days]);

  const loading = loadedDays !== days;
  const money = (minor: number) => formatMoney({ amount: minor, currency: data?.currency ?? "USD" });
  const period = RANGES.find((r) => r.days === days)?.label.toLowerCase() ?? `${days} days`;

  return (
    <div className="mx-auto max-w-6xl">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="display text-4xl">Dashboard</h1>
        <div role="group" aria-label="Date range" className="border-line inline-flex rounded-lg border bg-white p-1">
          {RANGES.map((r) => (
            <button
              key={r.days}
              type="button"
              aria-pressed={days === r.days}
              onClick={() => setDays(r.days)}
              className={`min-h-9 rounded-md px-3 text-sm font-medium transition-colors ${
                days === r.days ? "bg-navy text-white" : "text-mute hover:bg-tile hover:text-ink"
              }`}
            >
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <p role="alert" className="text-split mt-6">
          {error}
        </p>
      )}

      {!data ? (
        !error && <div className="mt-8 h-96 animate-pulse rounded-card bg-white" aria-busy="true" />
      ) : (
        <div className={`mt-8 transition-opacity ${loading ? "opacity-60" : ""}`} aria-busy={loading}>
          <section aria-labelledby="sales" className="grid gap-4 lg:grid-cols-[1fr_20rem]">
            <div className="border-line rounded-card border bg-white p-6">
              <h2 id="sales" className="text-mute text-sm font-medium">
                Revenue, {period}
              </h2>
              <p className="mt-1 text-5xl font-bold tracking-tight">{money(data.revenueMinor)}</p>
              <p className="text-mute mt-1 text-sm">
                {data.paidOrders} paid order{data.paidOrders === 1 ? "" : "s"}
                {data.paidOrders > 0 && <> · average {money(data.averageOrderValueMinor)}</>}
              </p>
              {days > 1 && (
                <div className="mt-6">
                  <RevenueChart days={data.revenueByDay} currency={data.currency} />
                </div>
              )}
            </div>

            <div className="grid content-start gap-4 sm:grid-cols-2 lg:grid-cols-1">
              <Stat label="Orders placed" value={compactNumber(data.ordersPlaced)} note={`${data.paidOrders} paid`} />
              <Stat
                label="New customers"
                value={compactNumber(data.newCustomers)}
                note={`${compactNumber(data.totalCustomers)} in total`}
              />
              <Stat label="Published products" value={compactNumber(data.publishedProducts)} note="live in the store" />
            </div>
          </section>

          <div className="mt-4 grid gap-4 lg:grid-cols-2">
            <section aria-labelledby="attention" className="border-line rounded-card border bg-white p-6">
              <h2 id="attention" className="condensed text-lg font-bold">
                Needs attention
              </h2>
              <ul className="divide-line mt-3 divide-y">
                <Attention
                  href="/admin/orders?status=PAID"
                  count={data.awaitingFulfilment}
                  label={`order${data.awaitingFulfilment === 1 ? "" : "s"} to fulfil`}
                  hint="Paid, processing or packed"
                  tone="warn"
                  icon={Clock}
                />
                <Attention
                  href="/admin/inventory?low=1"
                  count={data.lowStockVariants}
                  label={`variant${data.lowStockVariants === 1 ? "" : "s"} low on stock`}
                  hint="At or below their threshold"
                  tone="warn"
                  icon={PackageOpen}
                />
                <Attention
                  href="/admin/orders?status=PAYMENT_FAILED"
                  count={data.failedPayments}
                  label={`failed payment${data.failedPayments === 1 ? "" : "s"}, ${period}`}
                  hint="Declined or errored attempts"
                  tone="critical"
                  icon={CircleX}
                />
                <Attention
                  href="/admin/orders?status=PENDING_PAYMENT"
                  count={data.unpaidOrders}
                  label={`unpaid order${data.unpaidOrders === 1 ? "" : "s"}`}
                  hint="Stock is held until payment is due"
                  tone="warn"
                  icon={AlertTriangle}
                />
              </ul>
            </section>

            <section aria-labelledby="activity" className="border-line rounded-card border bg-white p-6">
              <h2 id="activity" className="condensed text-lg font-bold">
                Recent staff activity
              </h2>
              {data.recentActivity.length === 0 ? (
                <p className="text-mute mt-3 text-sm">Nothing yet.</p>
              ) : (
                <ol className="mt-3 space-y-3">
                  {data.recentActivity.map((a) => (
                    <li key={a.id} className="flex gap-3 text-sm">
                      <span className="bg-sky mt-2 size-1.5 shrink-0 rounded-full" aria-hidden />
                      <div className="min-w-0 flex-1">
                        <p>
                          <span className="font-medium">{activityText(a.action)}</span>
                          {a.resourceType && a.resourceId && (
                            <span className="text-mute">
                              {" "}
                              · {a.resourceType} {a.resourceId}
                            </span>
                          )}
                        </p>
                        <p className="text-mute truncate">{a.actorEmail ?? "System"}</p>
                      </div>
                      <time dateTime={a.at} className="text-mute shrink-0" title={new Date(a.at).toLocaleString()}>
                        {relativeTime(a.at)}
                      </time>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>
        </div>
      )}
    </div>
  );
}

function Stat({ label, value, note }: { label: string; value: string; note: string }) {
  return (
    <div className="border-line rounded-card border bg-white p-5">
      <p className="text-mute text-sm font-medium">{label}</p>
      <p className="mt-1 text-3xl font-bold tracking-tight">{value}</p>
      <p className="text-mute text-sm">{note}</p>
    </div>
  );
}

function Attention({
  href,
  count,
  label,
  hint,
  tone,
  icon: Icon,
}: {
  href: string;
  count: number;
  label: string;
  hint: string;
  tone: "warn" | "critical";
  icon: React.ComponentType<{ className?: string; "aria-hidden"?: boolean }>;
}) {
  const clear = count === 0;
  const StatusIcon = clear ? CircleCheck : Icon;
  const color = clear ? "text-ok" : tone === "critical" ? "text-split" : "text-warn";
  return (
    <li>
      <Link href={href} className="group -mx-2 flex min-h-14 items-center gap-3 rounded-lg px-2 hover:bg-surface">
        <StatusIcon className={`size-5 shrink-0 ${color}`} aria-hidden />
        <div className="min-w-0 flex-1">
          <p className="text-sm">
            <span className="figures font-bold">{count}</span> {label}
          </p>
          <p className="text-mute text-xs">{clear ? "All clear" : hint}</p>
        </div>
        <ChevronRight className="text-mute size-4 transition-transform group-hover:translate-x-0.5" aria-hidden />
      </Link>
    </li>
  );
}
