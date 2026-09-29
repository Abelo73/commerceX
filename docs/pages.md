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

## Design system: calm navy (branch `redesign/navy`)

Inspired by a user-supplied reference (deep navy hero, white product "screen" in soft rings, clean comparison
table, numbered steps). Replaces the earlier "lane" and "night track" directions.

| Token | Value | Use |
|---|---|---|
| navy / navy-2 | `#0B1A4A` / `#14286A` | hero, offer card, footer, filled check icons |
| ink / mute | `#0F172A` / `#5B6478` | text (mute ≥ 4.5:1 on white) |
| surface / tile | `#F6F8FC` / `#EEF2F9` | alternate sections, image tiles |
| line | `#E3E7EF` | borders, dividers |
| brand / brand-deep | `#2455E6` / `#1B43BD` | primary actions and links on light backgrounds |
| sky | `#8FB3FF` | small accents on navy |
| ok | `#16A34A` | status dots |
| split | `#C9302F` | sale prices, low stock only |

Type: Plus Jakarta Sans. `display` = 800 weight, −0.03em tracking; headings balance their line breaks.
Buttons are soft rectangles (`rounded-lg`); pills only for badges and swatches.

Home: **full-bleed photo hero** (night-run photo, navy gradient for legibility, headline, two buttons, category
chips) → "The pair you pick is the pair you get"
comparison (live stock, price guard, verified reviews, tracking — all real features) → category cards →
new arrivals → three steps → navy offer card → footer.

Sign-in / register: **split screen** in the `(auth)` route group (no store header/footer): photo panel with
three account benefits on the left, form on the right; the photo becomes a banner on mobile.

Photography: Unsplash License, stored in `public/images` (see `CREDITS.md`), chosen without visible third-party
brand logos. Product imagery stays illustrated (`ProductArt` / `HeroShoe`) until real product photos exist.

Route groups: `app/(store)` = header + footer + cart drawer; `app/(auth)` = bare full-screen pages.

Motion is deliberately light: one slow photo settle + fade-up on hero load, and hover/drawer transitions.
Everything is static under `prefers-reduced-motion`.
