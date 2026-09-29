"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { useMotionValueEvent, useScroll } from "motion/react";
import { Search, ShoppingBag, User } from "lucide-react";
import { useAuth, useCart } from "@/components/providers";
import { LANES } from "@/lib/nav";

export function SiteHeader() {
  const { user, ready } = useAuth();
  const { cart, setOpen } = useCart();
  const count = cart?.itemCount ?? 0;
  const home = usePathname() === "/";
  const { scrollY } = useScroll();
  const [scrolled, setScrolled] = useState(false);
  useMotionValueEvent(scrollY, "change", (y) => setScrolled(y > 40));
  // Over the home hero the header floats, transparent with light text, until the page scrolls.
  const overHero = home && !scrolled;

  return (
    <header
      className={`top-0 z-30 border-b transition-colors duration-300 ${home ? "fixed inset-x-0" : "sticky"} ${
        overHero ? "text-white border-transparent bg-transparent" : "border-line text-ink bg-white/90 backdrop-blur-md"
      }`}
    >
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-6 px-4 sm:px-6">
        <Link href="/" className={`text-2xl font-extrabold tracking-tight ${overHero ? "text-white" : "text-navy"}`} aria-label="CommerceX home">
          CommerceX
        </Link>
        <nav aria-label="Categories" className="hidden md:block">
          <ul className="flex gap-5 text-sm font-medium">
            {LANES.map((lane) => (
              <li key={lane.slug}>
                <Link href={`/shop?category=${lane.slug}`} className={overHero ? "text-white/85 hover:text-white" : "text-mute hover:text-ink"}>
                  {lane.name}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <form action="/shop" role="search" className="ml-auto hidden flex-1 justify-end sm:flex">
          <label className={`focus-within:border-brand flex w-full max-w-xs items-center gap-2 rounded-full border px-3 py-1.5 ${overHero ? "border-white/30 bg-white/10" : "border-line bg-white"}`}>
            <Search aria-hidden className={`h-4 w-4 ${overHero ? "text-white/70" : "text-mute"}`} />
            <span className="sr-only">Search products</span>
            <input name="q" type="search" placeholder="Search shoes and gear" className={`w-full bg-transparent text-sm outline-none ${overHero ? "placeholder:text-white/60" : ""}`} />
          </label>
        </form>
        <div className="ml-auto flex items-center gap-1 sm:ml-0">
          <Link
            href={user ? "/account" : "/login"}
            className={`flex min-h-11 items-center gap-2 rounded-lg px-3 text-sm ${overHero ? "hover:bg-white/10" : "hover:bg-tile"}`}
          >
            <User aria-hidden className="h-5 w-5" />
            <span className="hidden sm:inline">{ready && user ? user.firstName : "Sign in"}</span>
          </Link>
          <button
            type="button"
            onClick={() => setOpen(true)}
            className={`relative flex min-h-11 min-w-11 items-center justify-center rounded-lg ${overHero ? "hover:bg-white/10" : "hover:bg-tile"}`}
            aria-label={`Open cart, ${count} item${count === 1 ? "" : "s"}`}
          >
            <ShoppingBag aria-hidden className="h-5 w-5" />
            {count > 0 && (
              <span className="bg-brand text-white figures absolute top-0 right-0 grid h-5 min-w-5 place-items-center rounded-full px-1 text-xs font-semibold">
                {count}
              </span>
            )}
          </button>
        </div>
      </div>
      <nav aria-label="Categories" className={`border-t md:hidden ${overHero ? "border-white/15" : "border-line"}`}>
        <ul className="flex gap-5 overflow-x-auto px-4 py-2 text-sm font-medium whitespace-nowrap">
          {LANES.map((lane) => (
            <li key={lane.slug}>
              <Link href={`/shop?category=${lane.slug}`}>{lane.name}</Link>
            </li>
          ))}
          <li>
            <Link href="/shop">Shop all</Link>
          </li>
        </ul>
      </nav>
    </header>
  );
}
