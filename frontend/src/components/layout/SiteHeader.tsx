"use client";

import Link from "next/link";
import { Search, ShoppingBag, User } from "lucide-react";
import { useAuth, useCart } from "@/components/providers";
import { LANES } from "@/lib/nav";

export function SiteHeader() {
  const { user, ready } = useAuth();
  const { cart, setOpen } = useCart();
  const count = cart?.itemCount ?? 0;

  return (
    <header className="bg-chalk/95 border-line sticky top-0 z-30 border-b backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-6 px-4 sm:px-6">
        <Link href="/" className="display text-track text-3xl" aria-label="CommerceX home">
          CommerceX
        </Link>
        <nav aria-label="Categories" className="hidden md:block">
          <ul className="flex gap-5 text-sm font-medium">
            {LANES.map((lane) => (
              <li key={lane.slug}>
                <Link href={`/shop?category=${lane.slug}`} className="hover:text-track">
                  {lane.name}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <form action="/shop" role="search" className="ml-auto hidden flex-1 justify-end sm:flex">
          <label className="border-line focus-within:border-track flex w-full max-w-xs items-center gap-2 rounded-full border bg-white px-3 py-1.5">
            <Search aria-hidden className="text-mute h-4 w-4" />
            <span className="sr-only">Search products</span>
            <input name="q" type="search" placeholder="Search shoes and gear" className="w-full bg-transparent text-sm outline-none" />
          </label>
        </form>
        <div className="ml-auto flex items-center gap-1 sm:ml-0">
          <Link
            href={user ? "/account" : "/login"}
            className="hover:bg-midsole flex items-center gap-2 rounded-full px-3 py-2 text-sm"
          >
            <User aria-hidden className="h-5 w-5" />
            <span className="hidden sm:inline">{ready && user ? user.firstName : "Sign in"}</span>
          </Link>
          <button
            type="button"
            onClick={() => setOpen(true)}
            className="hover:bg-midsole relative flex items-center rounded-full p-2"
            aria-label={`Open cart, ${count} item${count === 1 ? "" : "s"}`}
          >
            <ShoppingBag aria-hidden className="h-5 w-5" />
            {count > 0 && (
              <span className="bg-track text-lane figures absolute -top-0.5 -right-0.5 grid h-5 min-w-5 place-items-center rounded-full px-1 text-xs font-semibold">
                {count}
              </span>
            )}
          </button>
        </div>
      </div>
      <nav aria-label="Categories" className="border-line border-t md:hidden">
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
