"use client";

import { api, newIdempotencyKey } from "./client-api";
import type { Payment } from "./types";

/**
 * Pays an order. The key is remembered per order until the attempt settles, so a retried click or a
 * reload during the request reuses it and can never charge twice.
 */
export async function payOrder(orderNumber: string, paymentToken: string): Promise<Payment> {
  const storageKey = `cx_pay_key_${orderNumber}`;
  let key = sessionStorage.getItem(storageKey);
  if (!key) {
    key = newIdempotencyKey("pay");
    sessionStorage.setItem(storageKey, key);
  }
  const payment = await api<Payment>(`/orders/${encodeURIComponent(orderNumber)}/payments`, {
    method: "POST",
    headers: { "Idempotency-Key": key },
    body: { paymentToken },
  });
  if (payment.status === "SUCCEEDED" || payment.status === "FAILED") {
    // A declined card needs a fresh attempt (and key) next time.
    sessionStorage.removeItem(storageKey);
  }
  return payment;
}
