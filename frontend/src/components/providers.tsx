"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import {
  ApiError,
  api,
  cartToken,
  mightHaveSession,
  refreshAccessToken,
  setAccessToken,
  setCartToken,
} from "@/lib/client-api";
import type { Cart, User } from "@/lib/types";

type AuthState = {
  user: User | null;
  ready: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  register: (input: { email: string; password: string; firstName: string; lastName: string }) => Promise<void>;
  signOut: () => Promise<void>;
};

type CartState = {
  cart: Cart | null;
  open: boolean;
  setOpen: (open: boolean) => void;
  refresh: () => Promise<void>;
  add: (variantId: number, quantity: number) => Promise<void>;
  update: (variantId: number, quantity: number) => Promise<void>;
  remove: (variantId: number) => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);
const CartContext = createContext<CartState | null>(null);

type AuthResponse = { accessToken: string; user: User };

export function Providers({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const [cart, setCart] = useState<Cart | null>(null);
  const [open, setOpen] = useState(false);

  const refresh = useCallback(async () => {
    try {
      setCart(await api<Cart>("/cart"));
    } catch {
      setCart(null);
    }
  }, []);

  // Restore the session from the refresh cookie, then load the cart that belongs to it.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const token = mightHaveSession() ? await refreshAccessToken() : null;
      if (token && !cancelled) {
        try {
          setUser(await api<User>("/me"));
        } catch {
          setUser(null);
        }
      }
      if (!cancelled) {
        setReady(true);
        await refresh();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [refresh]);

  const afterSignIn = useCallback(
    async (response: AuthResponse) => {
      setAccessToken(response.accessToken);
      setUser(response.user);
      if (cartToken()) {
        // Keep what the shopper added as a guest.
        await api<Cart>("/cart/merge", { method: "POST" }).catch(() => undefined);
        setCartToken(null);
      }
      await refresh();
    },
    [refresh],
  );

  const auth = useMemo<AuthState>(
    () => ({
      user,
      ready,
      signIn: async (email, password) =>
        afterSignIn(await api<AuthResponse>("/auth/login", { method: "POST", body: { email, password } })),
      register: async (input) =>
        afterSignIn(await api<AuthResponse>("/auth/register", { method: "POST", body: input })),
      signOut: async () => {
        await api("/auth/logout", { method: "POST" }).catch(() => undefined);
        setAccessToken(null);
        setUser(null);
        setCart(null);
      },
    }),
    [user, ready, afterSignIn],
  );

  const cartState = useMemo<CartState>(
    () => ({
      cart,
      open,
      setOpen,
      refresh,
      add: async (variantId, quantity) => {
        const result = await api<{ cart: Cart }>("/cart/items", {
          method: "POST",
          body: { variantId, quantity },
        });
        setCart(result.cart);
        setOpen(true);
      },
      update: async (variantId, quantity) => {
        setCart(await api<Cart>(`/cart/items/${variantId}`, { method: "PATCH", body: { quantity } }));
      },
      remove: async (variantId) => {
        setCart(await api<Cart>(`/cart/items/${variantId}`, { method: "DELETE" }));
      },
    }),
    [cart, open, refresh],
  );

  return (
    <AuthContext.Provider value={auth}>
      <CartContext.Provider value={cartState}>{children}</CartContext.Provider>
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside <Providers>");
  return context;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error("useCart must be used inside <Providers>");
  return context;
}

export { ApiError };
