import type { Money, OrderStatus, User } from "./types";

export type StaffRole = "ADMIN" | "OPERATIONS" | "CATALOG_MANAGER";

export type Dashboard = {
  from: string;
  currency: string;
  revenueMinor: number;
  ordersPlaced: number;
  paidOrders: number;
  averageOrderValueMinor: number;
  newCustomers: number;
  totalCustomers: number;
  failedPayments: number;
  publishedProducts: number;
  lowStockVariants: number;
  awaitingFulfilment: number;
  unpaidOrders: number;
  revenueByDay: { date: string; revenueMinor: number; orders: number }[];
  recentActivity: { id: number; action: string; resourceType?: string; resourceId?: string; actorEmail?: string; at: string }[];
};

export type StockRow = {
  variantId: number;
  productId?: number;
  productName?: string;
  sku?: string;
  label?: string;
  imageUrl?: string;
  onHand: number;
  reserved: number;
  available: number;
  sold: number;
  lowStockThreshold: number;
  lowStock: boolean;
  updatedAt: string;
};

export type AdminOrderSummary = {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  total: Money;
  itemCount: number;
  customerEmail: string;
  placedAt: string;
};

export type RefundView = {
  id: number;
  paymentId: number;
  status: "PENDING" | "SUCCEEDED" | "FAILED";
  amount: Money;
  reason?: string;
  failureMessage?: string;
  createdAt: string;
};

export type AdminPayment = {
  payment: {
    id: number;
    status: "PENDING" | "REQUIRES_ACTION" | "SUCCEEDED" | "FAILED";
    amount: Money;
    refunded: Money;
    failureCode?: string;
    failureMessage?: string;
    createdAt: string;
  };
  provider: string;
  providerReference?: string;
  refunds: RefundView[];
};

export const hasRole = (user: User | null, ...roles: StaffRole[]) => !!user && roles.some((r) => user.roles.includes(r));

/** Back-office sections, in sidebar order. Slice 2 adds products, customers, coupons and reviews. */
export const ADMIN_SECTIONS: { href: string; label: string; roles: StaffRole[] }[] = [
  { href: "/admin", label: "Dashboard", roles: ["ADMIN", "OPERATIONS"] },
  { href: "/admin/orders", label: "Orders", roles: ["ADMIN", "OPERATIONS"] },
  { href: "/admin/inventory", label: "Inventory", roles: ["ADMIN", "OPERATIONS"] },
];

/** Staff wording for order statuses (the storefront uses friendlier customer wording). */
export const STAFF_STATUS_TEXT: Record<OrderStatus, string> = {
  PENDING_PAYMENT: "Awaiting payment",
  PAYMENT_FAILED: "Payment failed",
  PAID: "Paid, to fulfil",
  PROCESSING: "Processing",
  PACKED: "Packed",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
  REFUNDED: "Refunded",
};

/** Next fulfilment step staff can take (mirrors the backend state machine). */
export const NEXT_STEP: Partial<Record<OrderStatus, { to: OrderStatus; label: string }>> = {
  PAID: { to: "PROCESSING", label: "Start processing" },
  PROCESSING: { to: "PACKED", label: "Mark packed" },
  PACKED: { to: "SHIPPED", label: "Mark shipped" },
  SHIPPED: { to: "DELIVERED", label: "Mark delivered" },
};

/** Only unpaid orders can be cancelled by staff; paid ones are refunded instead. */
export const canCancel = (status: OrderStatus) => status === "PENDING_PAYMENT" || status === "PAYMENT_FAILED";

/** Refunds are blocked while an order is in transit (PACKED, SHIPPED); the backend enforces the rest. */
export const canRefund = (status: OrderStatus) => status !== "PACKED" && status !== "SHIPPED";

export const ACTIVITY_TEXT: Record<string, string> = {
  INVENTORY_ADJUSTED: "Adjusted stock",
  INVENTORY_THRESHOLD_SET: "Changed low-stock threshold",
  ORDER_STATUS_CHANGED: "Changed order status",
  REFUND_REQUESTED: "Issued a refund",
  PAYMENT_AMOUNT_MISMATCH: "Payment amount mismatch (check)",
  PRODUCT_UNPUBLISHED: "Unpublished a product",
  PRODUCT_ARCHIVED: "Archived a product",
  USER_UPDATED: "Updated a user",
  PRODUCT_CREATED: "Created a product",
  PRODUCT_UPDATED: "Updated a product",
  PRODUCT_PUBLISHED: "Published a product",
  COUPON_CREATED: "Created a coupon",
  COUPON_UPDATED: "Updated a coupon",
  REVIEW_APPROVED: "Approved a review",
  REVIEW_REJECTED: "Rejected a review",
};

export const activityText = (action: string) =>
  ACTIVITY_TEXT[action] ?? action.charAt(0) + action.slice(1).toLowerCase().replaceAll("_", " ");

export function compactNumber(n: number) {
  return new Intl.NumberFormat("en-US", { notation: n >= 10_000 ? "compact" : "standard", maximumFractionDigits: 1 }).format(n);
}

export function relativeTime(iso: string, now = Date.now()) {
  const minutes = Math.round((now - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });
}
