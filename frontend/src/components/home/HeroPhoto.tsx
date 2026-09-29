"use client";

import Image from "next/image";
import Link from "next/link";
import { motion, useReducedMotion } from "motion/react";
import { LANES } from "@/lib/nav";

const EASE = [0.22, 1, 0.36, 1] as const;

/**
 * Full-bleed photo hero. A navy gradient keeps the white type readable on any part of the photo.
 * Motion is limited to one slow settle of the photo and a fade-up of the text on first load.
 */
export function HeroPhoto() {
  const reduce = useReducedMotion();
  const rise = (delay: number) =>
    reduce ? {} : { initial: { opacity: 0, y: 18 }, animate: { opacity: 1, y: 0 }, transition: { duration: 0.7, delay, ease: EASE } };

  return (
    <section aria-labelledby="hero-title" className="bg-navy relative isolate flex min-h-[88svh] items-end overflow-hidden text-white">
      <motion.div
        className="absolute inset-0 -z-10"
        initial={reduce ? false : { scale: 1.08 }}
        animate={{ scale: 1 }}
        transition={{ duration: 2.2, ease: EASE }}
      >
        <Image
          src="/images/hero-night-run.jpg"
          alt="Three runners silhouetted on a road at dusk"
          fill
          priority
          sizes="100vw"
          className="object-cover object-[60%_40%]"
        />
      </motion.div>
      <div aria-hidden className="from-navy via-navy/70 absolute inset-0 -z-10 bg-gradient-to-r to-transparent" />
      <div aria-hidden className="from-navy absolute inset-x-0 bottom-0 -z-10 h-1/2 bg-gradient-to-t to-transparent" />

      <div className="mx-auto w-full max-w-7xl px-4 pt-36 pb-14 sm:px-6 md:pb-20">
        <motion.p {...rise(0.1)} className="text-sky flex items-center gap-2 text-sm font-semibold">
          <span aria-hidden className="bg-sky h-1.5 w-1.5 rounded-full" />
          New season running
        </motion.p>
        <motion.h1 {...rise(0.18)} id="hero-title" className="display mt-5 max-w-3xl text-[clamp(2.8rem,7vw,5.5rem)]">
          Every run, fitted right.
        </motion.h1>
        <motion.p {...rise(0.26)} className="mt-5 max-w-lg text-lg text-white/80">
          Road, trail and track shoes with live stock for every size, so the pair you pick is the pair that ships.
        </motion.p>
        <motion.div {...rise(0.34)} className="mt-9 flex flex-wrap gap-3">
          <Link href="/shop" className="text-navy inline-flex min-h-12 items-center rounded-lg bg-white px-6 font-semibold transition-colors hover:bg-white/90">
            Shop running shoes
          </Link>
          <Link href="#how-it-works" className="inline-flex min-h-12 items-center rounded-lg border border-white/35 px-6 font-semibold backdrop-blur-sm transition-colors hover:border-white/70 hover:bg-white/10">
            How it works
          </Link>
        </motion.div>

        <motion.nav {...rise(0.45)} aria-label="Shop by category" className="mt-14 border-t border-white/15 pt-6">
          <ul className="flex flex-wrap gap-2">
            {LANES.map((lane) => (
              <li key={lane.slug}>
                <Link
                  href={`/shop?category=${lane.slug}`}
                  className="inline-flex min-h-10 items-center rounded-full border border-white/25 bg-white/5 px-4 text-sm font-medium backdrop-blur-sm transition-colors hover:bg-white hover:text-navy"
                >
                  {lane.name}
                </Link>
              </li>
            ))}
          </ul>
        </motion.nav>
      </div>
    </section>
  );
}
