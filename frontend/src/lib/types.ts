/** Wire types mirroring the backend API (see docs/api.md). Money is always in minor units. */

export type Money = { amount: number; currency: string };

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

export type Problem = {
  status: number;
  code: string;
  title?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
  details?: Record<string, unknown>;
  correlationId?: string;
};

export type CategoryNode = { id: number; name: string; slug: string; description?: string; children: CategoryNode[] };

export type ProductSummary = {
  id: number;
  slug: string;
  name: string;
  shortDescription?: string;
  price: Money;
  compareAtPrice?: Money;
  rating: number;
  reviewCount: number;
  inStock: boolean;
  brand?: { name: string; slug: string };
  category?: { id: number; name: string; slug: string };
  image?: { url: string; altText?: string };
  colors: string[];
};

export type Availability = "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK";

export type Variant = {
  id: number;
  sku: string;
  options: Record<string, string>;
  label: string;
  price: Money;
  compareAtPrice?: Money;
  availability: Availability;
};

export type ProductDetail = {
  id: number;
  slug: string;
  name: string;
  shortDescription?: string;
  description?: string;
  price: Money;
  compareAtPrice?: Money;
  rating: number;
  reviewCount: number;
  inStock: boolean;
  brand?: { name: string; slug: string };
  category?: { id: number; name: string; slug: string };
  options: { name: string; values: string[] }[];
  variants: Variant[];
  images: { url: string; altText?: string }[];
  tags: string[];
};

export type ReviewSummary = { average: number; count: number; distribution: Record<string, number> };

export type PublicReview = {
  id: number;
  authorName: string;
  rating: number;
  title: string;
  comment?: string;
  verifiedPurchase: boolean;
  createdAt: string;
};

export type CartIssue = "UNAVAILABLE" | "OUT_OF_STOCK" | "INSUFFICIENT_STOCK";

export type CartLine = {
  variantId: number;
  productId?: number;
  productSlug?: string;
  productName?: string;
  variantLabel?: string;
  sku?: string;
  imageUrl?: string;
  quantity: number;
  unitPrice?: Money;
  compareAtPrice?: Money;
  lineTotal?: Money;
  issue?: CartIssue;
  availableQuantity?: number;
};

export type Cart = {
  lines: CartLine[];
  itemCount: number;
  subtotal: Money;
  coupon?: { code: string; valid: boolean; discount: Money; reason: string; message: string };
  discount: Money;
  total: Money;
  checkoutReady: boolean;
};

export type User = {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
};

export type Address = {
  id: number;
  label?: string;
  fullName: string;
  line1: string;
  line2?: string;
  city: string;
  region?: string;
  postalCode: string;
  country: string;
  phone?: string;
  isDefault: boolean;
};

export type ShippingOption = { code: string; name: string; price: Money; minDays: number; maxDays: number };

export type Quote = {
  lines: { variantId: number; productName?: string; variantLabel?: string; quantity: number; unitPrice?: Money; lineTotal?: Money; issue?: string }[];
  subtotal: Money;
  discount: Money;
  shipping: Money;
  tax: Money;
  total: Money;
  couponCode?: string;
  shippingMethod: string;
  shippingOptions: ShippingOption[];
  ready: boolean;
  problems: string[];
};

export type OrderStatus =
  | "PENDING_PAYMENT"
  | "PAYMENT_FAILED"
  | "PAID"
  | "PROCESSING"
  | "PACKED"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCELLED"
  | "REFUNDED";

export type OrderDetails = {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  items: {
    variantId: number;
    productId: number;
    productName: string;
    variantLabel?: string;
    sku: string;
    unitPrice: Money;
    quantity: number;
    lineTotal: Money;
  }[];
  subtotal: Money;
  discount: Money;
  shipping: Money;
  tax: Money;
  total: Money;
  couponCode?: string;
  shippingMethod: string;
  shippingAddress: Omit<Address, "id" | "isDefault" | "label">;
  customerEmail: string;
  placedAt: string;
  paymentDueAt: string;
  history: { from?: string; to: string; reason?: string; at: string }[];
};

export type Payment = {
  id: number;
  status: "PENDING" | "REQUIRES_ACTION" | "SUCCEEDED" | "FAILED";
  amount: Money;
  failureCode?: string;
  failureMessage?: string;
  nextActionUrl?: string;
  createdAt: string;
};

export type WishlistItem = {
  productId: number;
  slug: string;
  name: string;
  price?: Money;
  imageUrl?: string;
  inStock: boolean;
  available: boolean;
  addedAt: string;
};
