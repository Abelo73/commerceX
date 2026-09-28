# Domain Model

## Aggregates

| Aggregate | Root | Invariants |
|---|---|---|
| User | `User` | unique email; roles non-empty |
| Product | `Product` (+ variants, options, images) | slug unique; published product has ≥1 active variant with price > 0 |
| InventoryItem | `InventoryItem` (per variant) | `0 ≤ reserved ≤ on_hand` |
| Cart | `Cart` | quantity 1..99 per line; one line per variant |
| Order | `Order` (+ items) | totals = Σ lines − discount + shipping + tax; transitions only via state machine |
| Payment | `Payment` | one SUCCEEDED payment per order; refunds ≤ captured amount |
| Coupon | `Coupon` | used_count ≤ usage_limit; within date window |

## Order state machine

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT: order placed (stock reserved)
    PENDING_PAYMENT --> PAID: payment succeeded
    PENDING_PAYMENT --> PAYMENT_FAILED: payment failed
    PENDING_PAYMENT --> CANCELLED: customer cancel / payment timeout
    PAYMENT_FAILED --> PAID: retry succeeded
    PAYMENT_FAILED --> CANCELLED: give up / timeout
    PAID --> PROCESSING
    PAID --> REFUNDED: cancelled before fulfilment
    PROCESSING --> PACKED
    PROCESSING --> REFUNDED
    PACKED --> SHIPPED
    SHIPPED --> DELIVERED
    DELIVERED --> REFUNDED: return accepted
```

- Transitions live in one place (`OrderStatus.canTransitionTo`), unit-tested exhaustively.
- Every transition writes `order_status_history` in the same transaction and records an outbox event.
- Side effects on inventory:
  - enter `PENDING_PAYMENT` → **reserve**
  - enter `PAID` → **commit** (on_hand −= q, reserved −= q, sold += q)
  - enter `CANCELLED` from unpaid states → **release** (reserved −= q)
  - enter `REFUNDED` → optional **return** to stock (admin choice)
- A scheduled job cancels `PENDING_PAYMENT` / `PAYMENT_FAILED` orders whose reservation expired (default 30 min).

## Inventory

**Strategy: atomic conditional update** (decision D-02).

```sql
UPDATE inventory_items
SET    reserved = reserved + :qty, updated_at = now()
WHERE  variant_id = :variantId
AND    on_hand - reserved >= :qty;
-- 1 row  → reserved
-- 0 rows → insufficient stock → whole checkout transaction rolls back
```

Why this over the alternatives:
- vs. `@Version` optimistic locking: no retry storms on hot items; the DB decides in one statement.
- vs. `SELECT … FOR UPDATE`: no lock held while Java code runs; same row-level serialization, shorter.
- `CHECK (reserved <= on_hand)` is a second safety net — even a buggy code path cannot oversell.
- Multi-line orders reserve variants **in ascending `variant_id` order** to avoid deadlocks.

Required test: 50 concurrent threads buy the last unit → exactly 1 succeeds, `reserved = 1`, 49 get `409 INSUFFICIENT_STOCK`.
✅ Implemented in `InventoryConcurrencyIT` (plus 10-of-50, opposing-order multi-line deadlock test, and a CHECK-constraint bypass test). Mutation-tested: removing the `WHERE` guard makes the suite fail.

`InventoryApi` (public): `stockLevels`, `reserve`, `commit`, `release`. The three mutations use
`Propagation.MANDATORY` (they must run inside the order's transaction) and are idempotent per
`(order_id, variant_id)`. Stock crossing zero publishes `StockStatusChanged` (synchronous, same
transaction → catalog `in_stock` flags); dropping to the threshold publishes `LowStockReached`.
Reservation expiry is driven by the order module (it owns order state) — inventory never releases on
its own, so a late payment can never be committed against stock that was silently given away.

## Checkout / place order

```text
POST /api/v1/orders  { addressId, shippingMethod, expectedTotal }  + Idempotency-Key
  0. Claim idempotency key            (own tx: replay if COMPLETED, 409 if IN_PROGRESS, 422 if body differs)
  ── one business transaction ──────────────────────────────────────────────────────────────────
  1. Customer active; address belongs to customer; shipping method known
  2. Load cart; every line purchasable (else 409 CHECKOUT_NOT_READY with problems)
  3. Re-price from the catalog (never trust client), shipping rules, tax placeholder (0, tax-inclusive)
  4. total == expectedTotal? (else 409 PRICE_CHANGED — the customer never pays an unseen amount)
  5. [Phase 8] validate + redeem coupon (conditional UPDATE on used_count)
  6. Insert order + item snapshots + history (PENDING_PAYMENT, payment_due_at = now + 30 min)
  7. Reserve inventory (sorted conditional UPDATEs; 409 INSUFFICIENT_STOCK rolls everything back)
  8. Clear cart; mark idempotency key COMPLETED with the response
  COMMIT  (on any failure: key released so the client may retry with it)
  ──────────────────────────────────────────────────────────────────────────────────────────────
  [Phase 7] create payment (outside the transaction) → PaymentProvider
```

Order expiry: a job (every 60 s) locks one overdue unpaid order at a time with
`FOR UPDATE SKIP LOCKED`, cancels it and releases its stock. Payment callbacks lock the same row,
so exactly one of "late payment" and "expiry" wins (tested); a payment that loses gets a conflict and
must be refunded (Phase 7).

## Payment flow

```text
createPayment (outside tx) ──► provider
        │ success/timeout/error
        ▼
payment status updated in its own short tx
        ▲
webhook ─┘  (dedup by provider_event_id; source of truth for final state)
        ▲
reconciliation job polls getPayment for PENDING payments older than N minutes
```

- "Payment succeeded but our request timed out" → payment stays PENDING → webhook or reconciliation job resolves it.
- Duplicate webhook → unique constraint on `(provider, provider_event_id)` → no-op.
- Payment transitions are idempotent: applying SUCCEEDED twice does nothing the second time.

## Domain events (outbox → Kafka in Phase 9)

`OrderPlaced`, `OrderPaid`, `OrderCancelled`, `OrderShipped`, `OrderDelivered`, `OrderRefunded`, `PaymentFailed`, `InventoryLow`, `ReviewSubmitted`, `UserRegistered`, `PasswordResetRequested`.

Consumers must be idempotent (dedup by event ID).
