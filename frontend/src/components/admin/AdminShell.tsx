"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Suspense } from "react";
import { Boxes, LayoutDashboard, LogOut, Receipt, Store } from "lucide-react";
import { useAuth } from "@/components/providers";
import { ADMIN_SECTIONS, hasRole } from "@/lib/admin";
import { useRequireAuth } from "@/lib/hooks/useRequireAuth";

const ICONS: Record<string, React.ComponentType<{ className?: string; "aria-hidden"?: boolean }>> = {
  "/admin": LayoutDashboard,
  "/admin/orders": Receipt,
  "/admin/inventory": Boxes,
};

/** Back-office frame: navy sidebar on desktop, a scrollable tab row on small screens. */
export function AdminShell({ children }: { children: React.ReactNode }) {
  const user = useRequireAuth();
  const { signOut } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  if (!user) return <main id="main" className="min-h-dvh" aria-busy="true" />;

  const sections = ADMIN_SECTIONS.filter((s) => hasRole(user, ...s.roles));
  if (sections.length === 0) {
    return (
      <main id="main" className="mx-auto flex min-h-dvh max-w-md flex-col justify-center px-4">
        <h1 className="display text-4xl">No access</h1>
        <p className="text-mute mt-3">
          {hasRole(user, "CATALOG_MANAGER")
            ? "Catalog tools are coming to the admin area soon. Until then, use the API."
            : "This area is for CommerceX staff. You're signed in as " + user.email + "."}
        </p>
        <Link href="/" className="text-brand mt-6 font-medium underline underline-offset-4">
          Back to the store
        </Link>
      </main>
    );
  }

  const isActive = (href: string) => (href === "/admin" ? pathname === "/admin" : pathname.startsWith(href));

  return (
    <div className="bg-surface min-h-dvh lg:grid lg:grid-cols-[15rem_1fr]">
      <aside className="bg-navy text-white lg:sticky lg:top-0 lg:flex lg:h-dvh lg:flex-col">
        <div className="flex items-center justify-between gap-4 px-5 py-4 lg:block lg:py-6">
          <Link href="/admin" className="text-xl font-extrabold tracking-tight">
            CommerceX <span className="text-sky text-sm font-semibold">admin</span>
          </Link>
          <Link href="/" className="text-sky inline-flex items-center gap-1.5 text-sm hover:text-white lg:hidden">
            <Store className="size-4" aria-hidden /> Store
          </Link>
        </div>
        <nav aria-label="Admin" className="overflow-x-auto px-3 pb-3 lg:flex-1 lg:overflow-visible lg:pb-0">
          <ul className="flex gap-1 lg:flex-col">
            {sections.map((s) => {
              const Icon = ICONS[s.href];
              const active = isActive(s.href);
              return (
                <li key={s.href}>
                  <Link
                    href={s.href}
                    aria-current={active ? "page" : undefined}
                    className={`flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-medium whitespace-nowrap transition-colors ${
                      active ? "bg-white/12 text-white" : "text-white/70 hover:bg-white/6 hover:text-white"
                    }`}
                  >
                    {Icon && <Icon className="size-4.5" aria-hidden />}
                    {s.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
        <div className="hidden border-t border-white/10 px-5 py-4 text-sm lg:block">
          <p className="truncate font-medium">{user.firstName} {user.lastName}</p>
          <p className="truncate text-white/60">{user.email}</p>
          <div className="mt-3 flex gap-4">
            <Link href="/" className="text-sky inline-flex items-center gap-1.5 hover:text-white">
              <Store className="size-4" aria-hidden /> Store
            </Link>
            <button
              type="button"
              onClick={() => signOut().then(() => router.push("/login"))}
              className="text-sky inline-flex items-center gap-1.5 hover:text-white"
            >
              <LogOut className="size-4" aria-hidden /> Sign out
            </button>
          </div>
        </div>
      </aside>
      <main id="main" className="min-w-0 px-4 py-8 sm:px-8">
        <Suspense>{children}</Suspense>
      </main>
    </div>
  );
}
