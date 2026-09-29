"use client";

import Link from "next/link";
import { motion, useReducedMotion } from "motion/react";
import { ProductArt } from "@/components/product/ProductArt";
import { LANES } from "@/lib/nav";

/**
 * The store's signature: a stretch of blue track. Each lane is a category, numbered as on a real track.
 * On load the lane lines draw left to right like a runner passing, once.
 */
export function LaneHero({ featuredSlug, featuredName }: { featuredSlug?: string; featuredName?: string }) {
  const reduce = useReducedMotion();
  const line = (i: number) =>
    reduce
      ? {}
      : {
          initial: { scaleX: 0 },
          animate: { scaleX: 1 },
          transition: { duration: 0.9, delay: 0.15 + i * 0.09, ease: [0.22, 1, 0.36, 1] as const },
        };

  return (
    <section aria-labelledby="hero-title" className="bg-track text-lane relative overflow-hidden">
      <div className="mx-auto grid max-w-7xl gap-6 px-4 pt-14 sm:px-6 md:grid-cols-[1.1fr_1fr] md:pt-20">
        <motion.h1
          id="hero-title"
          className="display text-[clamp(3.5rem,10vw,7.5rem)]"
          initial={reduce ? false : { opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
        >
          Run the distance you haven’t yet.
        </motion.h1>
        {featuredSlug && (
          <Link href={`/p/${featuredSlug}`} className="group relative -mb-10 flex flex-col items-end self-end md:-mb-16">
            <span className="condensed text-lg font-semibold text-white/85 group-hover:text-white">
              {featuredName}
            </span>
            <ProductArt
              kind="shoe"
              color="Signal Red"
              label={featuredName ?? "Featured shoe"}
              className="w-full max-w-xl drop-shadow-[0_24px_30px_rgba(10,20,60,0.35)] transition-transform duration-500 group-hover:-translate-y-2"
            />
          </Link>
        )}
      </div>

      <nav aria-label="Shop by lane" className="relative mt-10">
        <ol>
          {LANES.map((lane, i) => (
            <li key={lane.slug} className="relative">
              <motion.span aria-hidden className="bg-lane absolute inset-x-0 top-0 block h-[3px] origin-left" {...line(i)} />
              <Link
                href={`/shop?category=${lane.slug}`}
                className="hover:bg-track-deep mx-auto flex max-w-7xl items-baseline gap-4 px-4 py-5 transition-colors sm:gap-8 sm:px-6"
              >
                <span className="figures w-6 text-lg text-white/70">{lane.lane}</span>
                <span className="display w-40 text-4xl sm:w-56 sm:text-5xl">{lane.name}</span>
                <span className="hidden text-white/80 sm:inline">{lane.blurb}</span>
              </Link>
            </li>
          ))}
        </ol>
        <motion.span aria-hidden className="bg-lane block h-[3px] origin-left" {...line(LANES.length)} />
      </nav>
    </section>
  );
}
