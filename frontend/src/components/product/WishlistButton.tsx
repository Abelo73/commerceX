"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Heart } from "lucide-react";
import { useAuth } from "@/components/providers";
import { api } from "@/lib/client-api";
import type { WishlistItem } from "@/lib/types";

export function WishlistButton({ productId, slug }: { productId: number; slug: string }) {
  const { user, ready } = useAuth();
  const router = useRouter();
  const [saved, setSaved] = useState(false);
  // Whether the saved state is known. Clicks wait for it, so a late initial load can't undo a click.
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    if (!user) return;
    let active = true;
    api<WishlistItem[]>("/wishlist")
      .then((items) => active && setSaved(items.some((i) => i.productId === productId)))
      .catch(() => undefined)
      .finally(() => active && setChecked(true));
    return () => {
      active = false;
    };
  }, [user, productId]);

  async function toggle() {
    if (!user) {
      router.push(`/login?next=/p/${slug}`);
      return;
    }
    const next = !saved;
    setSaved(next);
    try {
      await api(`/wishlist/${productId}`, { method: next ? "PUT" : "DELETE" });
    } catch {
      setSaved(!next);
    }
  }

  return (
    <button
      type="button"
      onClick={toggle}
      disabled={!ready || (!!user && !checked)}
      aria-pressed={saved}
      className="border-line hover:border-ink flex items-center gap-2 rounded-lg border bg-white px-4 py-2 text-sm font-medium disabled:opacity-50"
    >
      <Heart aria-hidden className={`h-4 w-4 ${saved ? "fill-split text-split" : ""}`} />
      {saved ? "Saved to wishlist" : "Save to wishlist"}
    </button>
  );
}
