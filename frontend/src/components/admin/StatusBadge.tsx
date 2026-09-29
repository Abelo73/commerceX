import { CircleCheck, CircleDashed, CircleDot, CircleX, RotateCcw, Truck } from "lucide-react";
import { STAFF_STATUS_TEXT } from "@/lib/admin";
import type { OrderStatus } from "@/lib/types";

/** Order status with an icon and label, so state never depends on colour alone. */
const STYLE: Record<OrderStatus, { icon: typeof CircleDot; className: string }> = {
  PENDING_PAYMENT: { icon: CircleDashed, className: "text-warn bg-warn/10" },
  PAYMENT_FAILED: { icon: CircleX, className: "text-split bg-split/10" },
  PAID: { icon: CircleDot, className: "text-brand bg-brand/10" },
  PROCESSING: { icon: CircleDot, className: "text-brand bg-brand/10" },
  PACKED: { icon: CircleDot, className: "text-brand bg-brand/10" },
  SHIPPED: { icon: Truck, className: "text-brand bg-brand/10" },
  DELIVERED: { icon: CircleCheck, className: "text-ok bg-ok/10" },
  CANCELLED: { icon: CircleX, className: "text-mute bg-tile" },
  REFUNDED: { icon: RotateCcw, className: "text-mute bg-tile" },
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  const { icon: Icon, className } = STYLE[status];
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold whitespace-nowrap ${className}`}>
      <Icon className="size-3.5" aria-hidden />
      {STAFF_STATUS_TEXT[status]}
    </span>
  );
}
