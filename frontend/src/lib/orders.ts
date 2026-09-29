import type { OrderStatus } from "./types";

/** Customer-facing wording for order statuses. */
export const ORDER_STATUS_TEXT: Record<OrderStatus, string> = {
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

export const isUnpaid = (status: OrderStatus) => status === "PENDING_PAYMENT" || status === "PAYMENT_FAILED";

/** Fulfilment path shown as a timeline; each stage is reached in this order. */
export const FULFILMENT_STAGES: OrderStatus[] = ["PAID", "PROCESSING", "PACKED", "SHIPPED", "DELIVERED"];

/** Mock provider test cards (no real payment provider is connected yet). */
export const TEST_CARDS = [
  { token: "tok_success", label: "Test card that is approved" },
  { token: "tok_decline", label: "Test card that is declined" },
  { token: "tok_3ds", label: "Test card that needs extra verification" },
] as const;
