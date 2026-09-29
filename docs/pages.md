# Page Map

One Next.js app (App Router). Storefront and admin use separate route groups and layouts.

## Storefront — `app/(store)`

| Route | Page | Key UX |
|---|---|---|
| `/` | Home | Editorial hero (parallax), featured collection, new arrivals, trending, categories, promo, brand story, testimonials, newsletter |
| `/shop` | All products | Filters (category, brand, price, rating, stock), sort, pagination, skeletons |
| `/shop?category=&q=&minPrice=&maxPrice=&inStock=&sort=&page=` | Category / search / filtered listing | One URL-driven listing (shareable, server-rendered); replaces separate `/c`, `/b`, `/search` routes |
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

## Design system (Phase 10)

| Token | Value | Use |
|---|---|---|
| chalk | `#F4F6F8` | page background |
| asphalt | `#1D2430` | text, footer |
| mute | `#545D6D` | secondary text (≥ 4.5:1 on chalk) |
| midsole | `#E3E8F2` | image tiles, promo surfaces |
| track | `#2451C9` | brand, primary actions, hero band |
| split | `#C9302F` | sale prices and low stock only |

Type: Archivo variable. Display = `wdth 62`, weight 800 (`display` utility); body = normal width; prices use tabular
figures. Signature element: the hero's four numbered lanes = the four categories; the lane lines draw in once on load
(disabled under `prefers-reduced-motion`). Product imagery falls back to tinted SVG silhouettes (`ProductArt`) until
photography exists; real image URLs from the catalog are used when present.
