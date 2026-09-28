# Page Map

One Next.js app (App Router). Storefront and admin use separate route groups and layouts.

## Storefront — `app/(store)`

| Route | Page | Key UX |
|---|---|---|
| `/` | Home | Editorial hero (parallax), featured collection, new arrivals, trending, categories, promo, brand story, testimonials, newsletter |
| `/shop` | All products | Filters (category, brand, price, rating, stock), sort, pagination, skeletons |
| `/c/[slug]` | Category | Same listing, category hero |
| `/b/[slug]` | Brand | Same listing, brand header |
| `/search?q=` | Search results | Empty state with suggestions |
| `/p/[slug]` | Product detail | Gallery, variant selector, availability, reviews; shared-layout transition from card |
| `/cart` | Cart page | Also available as animated drawer from header |
| `/checkout` | Checkout | Steps: customer → address → shipping → payment → review |
| `/checkout/confirmation/[orderNumber]` | Confirmation | |
| `/login`, `/register`, `/forgot-password`, `/reset-password` | Auth | |
| `/account` | Overview | |
| `/account/orders`, `/account/orders/[orderNumber]` | Orders + tracking timeline | |
| `/account/addresses` | Addresses | |
| `/account/wishlist` | Wishlist | Move to cart micro-interaction |
| `/account/reviews` | My reviews | |
| `/about` | Brand story | Scroll-triggered editorial sections |

## Admin — `app/admin`

| Route | Roles |
|---|---|
| `/admin` | Dashboard KPIs — ADMIN |
| `/admin/products`, `/admin/products/[id]` | ADMIN, CATALOG_MANAGER |
| `/admin/categories`, `/admin/brands` | ADMIN, CATALOG_MANAGER |
| `/admin/inventory` | ADMIN, OPERATIONS |
| `/admin/orders`, `/admin/orders/[orderNumber]` | ADMIN, OPERATIONS |
| `/admin/customers` | ADMIN |
| `/admin/coupons` | ADMIN |
| `/admin/reviews` | ADMIN |

## Cross-cutting UI

Toasts, skeletons, empty and error states, keyboard focus styles, `prefers-reduced-motion` respected, mobile-first breakpoints, SEO metadata + product JSON-LD.
