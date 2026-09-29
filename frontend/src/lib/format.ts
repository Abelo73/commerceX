import type { Money } from "./types";

const formatters = new Map<string, Intl.NumberFormat>();

/** Formats minor units in the store locale, e.g. {amount: 14000, currency: "USD"} → "$140". */
export function formatMoney(money: Money): string {
  let formatter = formatters.get(money.currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: money.currency,
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    });
    formatters.set(money.currency, formatter);
  }
  const digits = new Intl.NumberFormat("en-US", { style: "currency", currency: money.currency })
    .resolvedOptions().maximumFractionDigits ?? 2;
  return formatter.format(money.amount / 10 ** digits);
}
