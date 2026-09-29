import type { Metadata } from "next";
import { Star } from "lucide-react";
import { VariantPicker } from "@/components/product/VariantPicker";
import { serverGet } from "@/lib/server-api";
import type { Page, ProductDetail, PublicReview, ReviewSummary } from "@/lib/types";

type Params = Promise<{ slug: string }>;

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { slug } = await params;
  const product = await serverGet<ProductDetail>(`/products/${encodeURIComponent(slug)}`);
  return { title: product.name, description: product.shortDescription };
}

export default async function ProductPage({ params }: { params: Params }) {
  const { slug } = await params;
  const [product, reviews] = await Promise.all([
    serverGet<ProductDetail>(`/products/${encodeURIComponent(slug)}`),
    serverGet<{ summary: ReviewSummary; reviews: Page<PublicReview> }>(
      `/products/${encodeURIComponent(slug)}/reviews?size=6`,
    ),
  ]);

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "Product",
    name: product.name,
    description: product.shortDescription,
    sku: product.variants[0]?.sku,
    brand: product.brand ? { "@type": "Brand", name: product.brand.name } : undefined,
    offers: {
      "@type": "AggregateOffer",
      priceCurrency: product.price.currency,
      lowPrice: product.price.amount / 100,
      availability: product.inStock ? "https://schema.org/InStock" : "https://schema.org/OutOfStock",
    },
    aggregateRating:
      reviews.summary.count > 0
        ? { "@type": "AggregateRating", ratingValue: reviews.summary.average, reviewCount: reviews.summary.count }
        : undefined,
  };

  return (
    <div className="mx-auto max-w-7xl px-4 pt-10 sm:px-6">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, "\\u003c") }} />
      <VariantPicker product={product} />

      <section aria-labelledby="reviews-title" className="border-line mt-20 border-t pt-10">
        <h2 id="reviews-title" className="display text-5xl">
          Reviews
        </h2>
        {reviews.summary.count === 0 ? (
          <p className="text-mute mt-4">No reviews yet. Customers who receive this product can review it from their account.</p>
        ) : (
          <div className="mt-6 grid gap-10 md:grid-cols-[16rem_1fr]">
            <div>
              <p className="figures display text-6xl">{reviews.summary.average.toFixed(1)}</p>
              <p className="text-mute">from {reviews.summary.count} reviews</p>
              <dl className="figures mt-4 space-y-1 text-sm">
                {Object.entries(reviews.summary.distribution).map(([stars, count]) => (
                  <div key={stars} className="flex items-center gap-2">
                    <dt className="w-10">{stars} star</dt>
                    <dd className="bg-midsole h-2 flex-1 rounded-full">
                      <span className="bg-track block h-2 rounded-full" style={{ width: `${(count / reviews.summary.count) * 100}%` }} />
                    </dd>
                    <dd className="w-6 text-right">{count}</dd>
                  </div>
                ))}
              </dl>
            </div>
            <ul className="divide-line divide-y">
              {reviews.reviews.content.map((review) => (
                <li key={review.id} className="py-5 first:pt-0">
                  <p className="flex items-center gap-1" aria-label={`${review.rating} out of 5 stars`}>
                    {Array.from({ length: 5 }, (_, i) => (
                      <Star key={i} aria-hidden className={`h-4 w-4 ${i < review.rating ? "fill-track text-track" : "text-line"}`} />
                    ))}
                  </p>
                  <h3 className="mt-2 font-semibold">{review.title}</h3>
                  {review.comment && <p className="mt-1 max-w-prose">{review.comment}</p>}
                  <p className="text-mute mt-2 text-sm">
                    {review.authorName}
                    {review.verifiedPurchase ? ", verified purchase" : ""}
                  </p>
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>
    </div>
  );
}
