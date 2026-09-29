"use client";

import type { Problem } from "./types";

/**
 * Browser API client. All calls go to /api on this origin (proxied to the backend).
 * - The access token lives only in memory; the refresh token is an httpOnly cookie the browser sends
 *   to /api/v1/auth. On a 401 the client refreshes once (single-flight) and retries.
 * - Guest carts are identified by X-Cart-Token, kept in localStorage.
 */

const CART_TOKEN_KEY = "cx_cart_token";
/** Not a secret: only remembers that this browser signed in, so first-time visitors skip the refresh call. */
const SESSION_HINT_KEY = "cx_has_session";

let accessToken: string | null = null;
let refreshInFlight: Promise<string | null> | null = null;
const tokenListeners = new Set<(token: string | null) => void>();

export class ApiError extends Error {
  constructor(public readonly problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (${problem.status})`);
  }

  get status() {
    return this.problem.status;
  }

  get code() {
    return this.problem.code;
  }

  /** Message for a form field, if the server reported one. */
  fieldError(field: string) {
    return this.problem.errors?.find((e) => e.field === field)?.message;
  }
}

export function setAccessToken(token: string | null) {
  accessToken = token;
  try {
    if (token) window.localStorage.setItem(SESSION_HINT_KEY, "1");
    else window.localStorage.removeItem(SESSION_HINT_KEY);
  } catch {
    // Storage unavailable: the session is simply not restored after a reload.
  }
  tokenListeners.forEach((listener) => listener(token));
}

export function onAccessTokenChange(listener: (token: string | null) => void) {
  tokenListeners.add(listener);
  return () => tokenListeners.delete(listener);
}

export function cartToken(): string | null {
  try {
    return window.localStorage.getItem(CART_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setCartToken(token: string | null) {
  try {
    if (token) {
      window.localStorage.setItem(CART_TOKEN_KEY, token);
    } else {
      window.localStorage.removeItem(CART_TOKEN_KEY);
    }
  } catch {
    // Storage unavailable (private mode): the guest cart lasts for this page view only.
  }
}

/** Exchanges the refresh cookie for a new access token. Concurrent callers share one request. */
export function mightHaveSession(): boolean {
  try {
    return window.localStorage.getItem(SESSION_HINT_KEY) === "1";
  } catch {
    return false;
  }
}

export function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = fetch("/api/v1/auth/refresh", { method: "POST", credentials: "same-origin" })
      .then(async (response) => {
        const token = response.ok ? ((await response.json()).accessToken as string) : null;
        setAccessToken(token);
        return token;
      })
      .catch(() => {
        setAccessToken(null);
        return null;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

type Options = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
};

export async function api<T>(path: string, options: Options = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json", ...options.headers };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  const guestToken = cartToken();
  if (guestToken) {
    headers["X-Cart-Token"] = guestToken;
  }

  const response = await fetch(`/api/v1${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    credentials: "same-origin",
  });

  const issuedCartToken = response.headers.get("X-Cart-Token");
  if (issuedCartToken) {
    setCartToken(issuedCartToken);
  }

  if (response.status === 401 && accessToken && !retried) {
    const token = await refreshAccessToken();
    if (token) {
      return api<T>(path, options, true);
    }
  }
  if (!response.ok) {
    let problem: Problem;
    try {
      problem = await response.json();
    } catch {
      problem = { status: response.status, code: "HTTP_" + response.status };
    }
    throw new ApiError({ ...problem, status: response.status });
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

/** A fresh key per user action; reuse it only when retrying that same action. */
export function newIdempotencyKey(prefix: string) {
  return `${prefix}-${crypto.randomUUID()}`;
}
