"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useAuth } from "@/components/providers";

/** Sends signed-out visitors to sign in, returning them here afterwards. */
export function useRequireAuth() {
  const { user, ready } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  useEffect(() => {
    if (ready && !user) router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [ready, user, router, pathname]);
  return user;
}
