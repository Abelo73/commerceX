"use client";

import { useEffect, useRef, useState } from "react";
import { formatMoney } from "@/lib/format";

type Day = { date: string; revenueMinor: number; orders: number };

const HEIGHT = 220;
const PAD = { top: 24, right: 8, bottom: 28, left: 56 };
const MAX_BAR = 24;

/** Clean axis: a 1/2/5 × 10^n step that fits the peak in about four ticks (minor units, so ≥ 1 major unit). */
function niceScale(peak: number) {
  const raw = Math.max(peak, 100) / 4;
  const power = 10 ** Math.floor(Math.log10(raw));
  const step = ([1, 2, 5, 10].find((s) => s * power >= raw) ?? 10) * power;
  const max = Math.max(step, Math.ceil(peak / step) * step);
  return { max, ticks: Array.from({ length: Math.round(max / step) + 1 }, (_, i) => i * step) };
}

const dayLabel = (iso: string, style: "short" | "long" = "short") =>
  new Date(`${iso}T00:00:00Z`).toLocaleDateString("en-US", {
    timeZone: "UTC",
    month: "short",
    day: "numeric",
    ...(style === "long" ? { weekday: "short" } : {}),
  });

/** Column path: 4px rounded data-end, square at the baseline. */
function column(x: number, y: number, w: number, h: number) {
  const r = Math.min(4, w / 2, h);
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`;
}

/**
 * Daily revenue as columns (one series, so no legend: the card title names it). Hover or arrow keys
 * reveal each day; the busiest day carries a direct label; a table view holds every value.
 */
export function RevenueChart({ days, currency }: { days: Day[]; currency: string }) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [active, setActive] = useState<number | null>(null);

  useEffect(() => {
    const node = wrapRef.current;
    if (!node) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)));
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  const money = (minor: number) => formatMoney({ amount: minor, currency });
  const { max, ticks } = niceScale(Math.max(...days.map((d) => d.revenueMinor), 0));
  const plotW = Math.max(0, width - PAD.left - PAD.right);
  const plotH = HEIGHT - PAD.top - PAD.bottom;
  const band = days.length ? plotW / days.length : 0;
  const barW = Math.max(2, Math.min(MAX_BAR, band - 2));
  const y = (v: number) => PAD.top + plotH - (v / max) * plotH;
  const peak = days.reduce((best, d, i) => (d.revenueMinor > (days[best]?.revenueMinor ?? -1) ? i : best), 0);
  const labelEvery = Math.max(1, Math.ceil(days.length / Math.max(1, Math.floor(plotW / 64))));

  const onKey = (e: React.KeyboardEvent) => {
    if (!days.length) return;
    const current = active ?? days.length - 1;
    const next = e.key === "ArrowLeft" ? current - 1 : e.key === "ArrowRight" ? current + 1 : e.key === "Home" ? 0 : e.key === "End" ? days.length - 1 : null;
    if (next === null) return;
    e.preventDefault();
    setActive(Math.min(days.length - 1, Math.max(0, next)));
  };

  const tip = active !== null ? days[active] : null;
  const tipX = active !== null ? PAD.left + band * active + band / 2 : 0;

  return (
    <div>
      <div ref={wrapRef} className="relative">
        {width > 0 && (
          <svg
            width={width}
            height={HEIGHT}
            role="img"
            aria-label={`Daily revenue, ${dayLabel(days[0]?.date ?? "")} to ${dayLabel(days.at(-1)?.date ?? "")}. Use arrow keys to read each day.`}
            tabIndex={0}
            onKeyDown={onKey}
            onFocus={() => setActive((a) => a ?? days.length - 1)}
            onBlur={() => setActive(null)}
            onPointerLeave={() => setActive(null)}
            className="block touch-pan-y"
          >
            {ticks.map((t) => (
              <g key={t}>
                <line x1={PAD.left} x2={width - PAD.right} y1={y(t)} y2={y(t)} stroke="var(--color-line)" strokeWidth={1} />
                <text x={PAD.left - 8} y={y(t)} dy="0.32em" textAnchor="end" className="fill-mute figures text-[11px]">
                  {money(t)}
                </text>
              </g>
            ))}
            {days.map((d, i) => {
              const x = PAD.left + band * i + (band - barW) / 2;
              const h = (d.revenueMinor / max) * plotH;
              return (
                <g key={d.date}>
                  {h > 0 && (
                    <path
                      d={column(x, y(d.revenueMinor), barW, h)}
                      fill={active === i ? "var(--color-brand-deep)" : "var(--color-brand)"}
                      opacity={active === null || active === i ? 1 : 0.55}
                    />
                  )}
                  {/* Hit area: the whole band, full height — bigger than the mark. */}
                  <rect
                    x={PAD.left + band * i}
                    y={PAD.top}
                    width={band}
                    height={plotH}
                    fill="transparent"
                    onPointerEnter={() => setActive(i)}
                    onPointerDown={() => setActive(i)}
                  />
                  {i % labelEvery === 0 && (
                    <text x={PAD.left + band * i + band / 2} y={HEIGHT - 8} textAnchor="middle" className="fill-mute text-[11px]">
                      {dayLabel(d.date)}
                    </text>
                  )}
                </g>
              );
            })}
            {days[peak]?.revenueMinor > 0 && active === null && (
              <text
                x={PAD.left + band * peak + band / 2}
                y={y(days[peak].revenueMinor) - 8}
                textAnchor={peak > days.length * 0.85 ? "end" : peak < days.length * 0.15 ? "start" : "middle"}
                className="fill-ink figures text-xs font-semibold"
              >
                {money(days[peak].revenueMinor)}
              </text>
            )}
          </svg>
        )}
        {tip && (
          <div
            role="status"
            className="border-line pointer-events-none absolute top-0 z-10 w-max rounded-lg border bg-white px-3 py-2 text-sm shadow-lg"
            style={{
              left: tipX,
              transform: `translateX(${tipX > width * 0.7 ? "-100%" : tipX < width * 0.3 ? "0" : "-50%"})`,
            }}
          >
            <p className="figures font-semibold">{money(tip.revenueMinor)}</p>
            <p className="text-mute">
              {dayLabel(tip.date, "long")} · {tip.orders} order{tip.orders === 1 ? "" : "s"}
            </p>
          </div>
        )}
      </div>
      <details className="mt-3 text-sm">
        <summary className="text-mute cursor-pointer hover:text-ink">Show as table</summary>
        <div className="mt-2 max-h-64 overflow-y-auto">
          <table className="figures w-full text-left">
            <thead className="text-mute sticky top-0 bg-white">
              <tr>
                <th className="py-1.5 font-medium">Day</th>
                <th className="py-1.5 text-right font-medium">Orders</th>
                <th className="py-1.5 text-right font-medium">Revenue</th>
              </tr>
            </thead>
            <tbody className="divide-line divide-y">
              {days.map((d) => (
                <tr key={d.date}>
                  <td className="py-1.5">{dayLabel(d.date, "long")}</td>
                  <td className="py-1.5 text-right">{d.orders}</td>
                  <td className="py-1.5 text-right">{money(d.revenueMinor)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}
