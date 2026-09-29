import Link from "next/link";
import { LANES } from "@/lib/nav";

export function SiteFooter() {
  return (
    <footer className="bg-asphalt text-lane mt-24">
      <div className="mx-auto grid max-w-7xl gap-10 px-4 py-14 sm:px-6 md:grid-cols-[2fr_1fr_1fr]">
        <div>
          <p className="display text-5xl">CommerceX</p>
          <p className="mt-3 max-w-sm text-white/75">Shoes and gear for runners who count kilometres, not steps.</p>
        </div>
        <nav aria-label="Shop">
          <p className="font-semibold">Shop</p>
          <ul className="mt-3 space-y-2 text-white/75">
            {LANES.map((lane) => (
              <li key={lane.slug}>
                <Link href={`/shop?category=${lane.slug}`} className="hover:text-white">
                  {lane.name}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <nav aria-label="Account">
          <p className="font-semibold">Account</p>
          <ul className="mt-3 space-y-2 text-white/75">
            <li>
              <Link href="/account" className="hover:text-white">
                Orders
              </Link>
            </li>
            <li>
              <Link href="/login" className="hover:text-white">
                Sign in
              </Link>
            </li>
          </ul>
        </nav>
      </div>
    </footer>
  );
}
