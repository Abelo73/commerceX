# CommerceX

## Technical Requirements & Engineering Standards

---

# 1. Backend Stack

```text
Java 21
Spring Boot 3.x
Spring MVC
Spring Security
Spring Data JPA
Hibernate
PostgreSQL
Flyway
Redis
Kafka
OpenAPI
JUnit 5
Testcontainers
Docker
```

---

# 2. Frontend Stack

```text
Next.js
React
TypeScript
Tailwind CSS
Framer Motion
Lucide
```

Optional:

```text
GSAP
```

Use GSAP only where timeline-based animation adds genuine value.

---

# 3. Architecture

Use:

## Modular Monolith

```text
commercex
│
├── common
│
├── auth
├── catalog
├── category
├── inventory
├── cart
├── checkout
├── order
├── payment
├── promotion
├── review
├── wishlist
├── notification
└── admin
```

Each module owns its business logic.

---

# 4. Backend Layering

```text
Controller
     ↓
Application Service
     ↓
Domain Logic
     ↓
Repository
     ↓
PostgreSQL
```

Controllers must not contain business logic.

Repositories must not orchestrate business workflows.

---

# 5. DTO Rules

Never return JPA entities directly.

Use:

```text
Request DTO
Response DTO
Entity
Mapper
```

Prefer Java records for appropriate immutable DTOs.

Use constructor injection.

Avoid unnecessary Lombok magic.

---

# 6. PostgreSQL

PostgreSQL is the source of truth for transactional data.

Use Flyway.

Every schema change must be represented by a migration.

Example:

```text
V1__create_users.sql
V2__create_products.sql
V3__create_inventory.sql
V4__create_carts.sql
V5__create_orders.sql
```

---

# 7. Transaction Strategy

Transactions belong around business operations.

Example:

```text
Create Order
    ↓
Validate Cart
    ↓
Validate Inventory
    ↓
Calculate Price
    ↓
Create Order
    ↓
Reserve Inventory
    ↓
Create Payment Record
```

External payment API calls should generally not be held inside long database transactions.

Use:

```text
Idempotency
Outbox
Retry
State machines
Compensating actions
```

where required.

---

# 8. Inventory Concurrency

This is one of the most important technical challenges.

Example:

```text
Stock = 1

Customer A → buys 1
Customer B → buys 1
```

The system must not allow both transactions to succeed.

Possible solution:

```text
@Version
```

with optimistic locking.

Or:

```sql
SELECT ...
FOR UPDATE
```

with pessimistic locking.

Whichever approach is selected must have integration/concurrency tests.

---

# 9. Payment Architecture

Use an interface:

```java
public interface PaymentProvider {

    PaymentResult createPayment(PaymentRequest request);

    PaymentResult getPayment(String providerReference);

    RefundResult refund(RefundRequest request);
}
```

Implementations:

```text
MockPaymentProvider
ProviderAdapter
```

This makes payment providers replaceable.

---

# 10. Idempotency

Critical operations must support idempotency.

Especially:

```text
Payment
Payment webhook
Order creation
Refund
```

Example:

```text
Idempotency-Key
        ↓
Check existing operation
        ↓
Already processed?
    ↓             ↓
   YES            NO
    ↓             ↓
Return result   Process
```

---

# 11. Outbox Pattern

For reliable event publishing:

```text
Spring Transaction
       │
       ├── Business Data
       │
       └── Outbox Event
                ↓
           Publisher
                ↓
              Kafka
```

This avoids the classic problem:

```text
Database committed
BUT
Kafka publish failed
```

---

# 12. Kafka

Kafka should be introduced for real asynchronous workflows.

Example:

```text
OrderPlaced
      ↓
    Kafka
      ├── Notification
      ├── Analytics
      ├── Inventory
      └── Customer Activity
```

Do not use Kafka simply because it looks impressive.

---

# 13. Redis

Use Redis for appropriate workloads:

```text
Caching
Rate limiting
Temporary state
Idempotency support
Frequently accessed catalog data
```

Do not make Redis the source of truth for orders or payments.

---

# 14. Security

Use:

```text
Spring Security
JWT/OIDC
RBAC
Password hashing
Validation
Rate limiting
Secure headers
Audit logging
```

Authorization examples:

```text
CUSTOMER
→ customer resources

ADMIN
→ all administrative resources

CATALOG_MANAGER
→ catalog resources

OPERATIONS
→ orders/inventory
```

---

# 15. Observability

Spring Boot Actuator:

```text
/actuator/health
/actuator/info
/actuator/metrics
```

Track:

```text
HTTP latency
HTTP errors
Database latency
JVM
Redis
Kafka lag
Payment failures
Order failures
```

Use:

```text
Prometheus
Grafana
Structured logging
Correlation IDs
```

---

# 16. Testing Strategy

## Unit Tests

Test:

```text
Pricing
Promotion
Order state machine
Inventory rules
Validation
```

## Integration Tests

Use Testcontainers:

```text
PostgreSQL
Redis
Kafka where required
```

## API Tests

Test:

```text
Authentication
Authorization
Validation
Errors
Pagination
Business conflicts
```

## End-to-End

Test:

```text
Register
↓
Login
↓
Browse
↓
Product
↓
Cart
↓
Checkout
↓
Payment
↓
Order
↓
Admin processing
```

## Concurrency

Test simultaneous purchases against limited inventory.

---

# 17. CI/CD

```text
Git Push
    ↓
Compile
    ↓
Static Analysis
    ↓
Unit Tests
    ↓
Integration Tests
    ↓
Build Docker Image
    ↓
Security Scan
    ↓
Publish Image
    ↓
Deploy
```

---

# 18. Claude Code Engineering Skills

Claude should operate as:

```text
Senior Java Engineer
Senior Spring Boot Engineer
System Designer
Database Engineer
Security Engineer
Frontend Engineer
UX Engineer
DevOps Engineer
QA Engineer
Code Reviewer
```

Required technical skills:

```text
Java 21
Spring Boot
Spring Security
JPA/Hibernate
PostgreSQL
Redis
Kafka
REST
OpenAPI
Docker
CI/CD
JUnit
Testcontainers
Concurrency
Transactions
Idempotency
Outbox Pattern
Event-Driven Architecture
Observability
Performance Engineering
Next.js
React
TypeScript
Tailwind
Framer Motion
Accessibility
SEO
```

---

# 19. Claude Operating Rules

Before coding:

```text
1. Inspect repository
2. Understand architecture
3. Identify affected modules
4. Identify database impact
5. Identify security impact
6. Identify transaction impact
7. Create implementation plan
8. Implement
9. Test
10. Review
```

Never blindly modify files.

---

# 20. Claude Must Think About Failure

For every important feature ask:

```text
What happens if the request is retried?

What happens if the database fails?

What happens if payment succeeds but our request times out?

What happens if a webhook arrives twice?

What happens if two customers purchase the last item?

What happens if Kafka is unavailable?

What happens if Redis is unavailable?

What happens if the frontend sends malicious data?
```

This mindset separates a production system from a demo.

---

# 21. Claude Prompt Template

Use this for implementation tasks:

```text
You are working on CommerceX, a production-oriented e-commerce platform.

STACK:
Java 21
Spring Boot
PostgreSQL
Flyway
Spring Security
Redis
Kafka
Next.js
React
TypeScript
Docker

TASK:
[FEATURE]

REQUIREMENTS:
[REQUIREMENTS]

IMPORTANT:
- Inspect the existing implementation first.
- Do not rewrite unrelated code.
- Follow the existing architecture.
- Do not expose JPA entities.
- Validate all client input.
- Consider authorization.
- Consider transactions.
- Consider concurrency.
- Consider retries.
- Consider idempotency.
- Add tests.
- Update documentation where necessary.

PROCESS:

1. Inspect relevant files.
2. Explain the current architecture.
3. Identify affected components.
4. Propose implementation.
5. Implement.
6. Run tests.
7. Fix failures.
8. Review security.
9. Review performance.
10. Summarize changes.

DEFINITION OF DONE:

- Feature works
- Tests pass
- Validation exists
- Authorization exists
- Error handling exists
- Database migration exists if required
- API documentation is updated
- No sensitive information is logged
- No unrelated code is modified
```

---

# 22. Definition of Done

A feature is not done merely because:

```text
"It works on my machine."
```

It is done when:

```text
Implementation
+
Tests
+
Security
+
Validation
+
Error Handling
+
Database Integrity
+
Observability
+
Documentation
+
Performance Review
```

---

# 23. Golden Architecture Rule

Do not introduce complexity because it looks senior.

Use complexity only when it solves a real problem.

For example:

```text
PostgreSQL
→ transactional source of truth

Redis
→ fast temporary/cache workloads

Kafka
→ asynchronous event processing

Outbox
→ reliable event publication

Keycloak
→ centralized identity when needed

Kubernetes
→ deployment/orchestration when justified
```

Every technology must have a reason.
"""

---

## How I would build this with Claude

**Do not give Claude one giant prompt saying “build my e-commerce website.”** That's one of the easiest ways to end up with a beautiful-looking but poorly structured application.

Instead, build it in **vertical slices**:

```text
PHASE 0
Product + Architecture
        ↓
PHASE 1
Spring Boot Foundation
        ↓
PHASE 2
Authentication + Users
        ↓
PHASE 3
Product Catalog
        ↓
PHASE 4
Inventory
        ↓
PHASE 5
Cart + Wishlist
        ↓
PHASE 6
Checkout + Orders
        ↓
PHASE 7
Payments
        ↓
PHASE 8
Promotions + Reviews
        ↓
PHASE 9
Kafka + Outbox + Notifications
        ↓
PHASE 10
Premium Next.js Storefront
        ↓
PHASE 11
Admin Dashboard
        ↓
PHASE 12
Testing + Observability + CI/CD
        ↓
PHASE 13
Production Hardening
```

### The most important architectural decision

I strongly recommend **not starting with microservices**.

Start with:

```text
commercex-backend
│
├── auth
├── catalog
├── inventory
├── cart
├── checkout
├── order
├── payment
├── promotion
├── review
├── wishlist
├── notification
└── admin
```

inside **one Spring Boot application**.

Then introduce:

```text
Redis
   ↓
Outbox
   ↓
Kafka
   ↓
Async consumers
```

when the corresponding requirements appear.

Later, if the system genuinely requires independent scaling/deployment, modules such as payment, notification, catalog, or search can be extracted.

That gives you a project that demonstrates **real system-design judgment**, rather than simply having a diagram with 15 microservices.

### And visually, don't make the frontend a generic e-commerce template

I would make the customer experience closer to a **premium digital brand**:

```text
┌──────────────────────────────────────────────┐
│ Brand       Collections  New  About    🛒   │
├──────────────────────────────────────────────┤
│                                              │
│       LARGE VISUAL HERO                     │
│       Strong product story                   │
│       Editorial typography                   │
│                         [Explore Collection] │
│                                              │
├──────────────────────────────────────────────┤
│              Featured Collection             │
│                                              │
│    Product       Product       Product       │
│                                              │
├──────────────────────────────────────────────┤
│              Brand Experience                │
│          Large visual storytelling           │
├──────────────────────────────────────────────┤
│             New Arrivals                     │
│                                              │
│    Product       Product       Product       │
└──────────────────────────────────────────────┘
```

Then use motion for **product storytelling**, not random animations.

For example:

* Hero image parallax
* Product image reveal
* Smooth category transitions
* Cart drawer animation
* Shared-layout product → detail transition
* Scroll-triggered editorial sections
* Magnetic/interactive CTA where appropriate
* Skeleton loaders
* Smooth page transitions
* Micro-interactions for wishlist/cart
* Subtle hover transformations

The result should feel like a **real commercial product**, while the backend demonstrates the engineering depth behind it.

**Next, the best step is to turn these specifications into the actual project blueprint:** repository/folder structure, complete PostgreSQL ERD, domain model, API specification, page map, Docker architecture, and then a **master Claude Code prompt that builds the project phase-by-phase without destroying the architecture.**
