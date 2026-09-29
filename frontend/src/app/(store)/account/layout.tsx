"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/components/providers";
import { useRequireAuth } from "@/lib/hooks/useRequireAuth";

const SECTIONS = [
  { href: "/account", label: "Orders" },
  { href: "/account/addresses", label: "Addresses" },
  { href: "/account/wishlist", label: "Wishlist" },
];

export default function AccountLayout({ children }: { children: React.ReactNode }) {
  const user = useRequireAuth();
  const { signOut } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  if (!user) return <div className="mx-auto max-w-5xl px-4 py-16" aria-busy="true" />;

  return (
    <div className="mx-auto max-w-5xl px-4 py-12 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="display text-6xl">Hi, {user.firstName}</h1>
          <p className="text-mute mt-1">{user.email}</p>
        </div>
        <button type="button" onClick={() => signOut().then(() => router.push("/"))} className="text-mute text-sm underline underline-offset-4 hover:text-ink">
          Sign out
        </button>
      </div>
      <nav aria-label="Account" className="border-line mt-8 border-b">
        <ul className="flex gap-6">
          {SECTIONS.map((section) => {
            const active = section.href === "/account" ? pathname === "/account" || pathname.startsWith("/account/orders") : pathname.startsWith(section.href);
            return (
              <li key={section.href}>
                <Link
                  href={section.href}
                  aria-current={active ? "page" : undefined}
                  className={`-mb-px block border-b-2 pb-3 font-medium ${active ? "border-brand text-brand" : "text-mute border-transparent hover:text-ink"}`}
                >
                  {section.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      <div className="pt-8">{children}</div>
    </div>
  );
}
