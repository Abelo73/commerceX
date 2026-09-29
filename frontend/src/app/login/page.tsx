"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { Field } from "@/components/forms/Field";
import { ApiError, useAuth } from "@/components/providers";

function LoginForm() {
  const { signIn } = useAuth();
  const router = useRouter();
  const next = useSearchParams().get("next");
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError(null);
    try {
      await signIn(String(form.get("email")), String(form.get("password")));
      router.push(next && next.startsWith("/") && !next.startsWith("//") ? next : "/account");
    } catch (e) {
      setError(e instanceof ApiError ? e : null);
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="mt-8 space-y-5" noValidate>
      <Field label="Email" name="email" type="email" autoComplete="email" required error={error?.fieldError("email")} />
      <Field label="Password" name="password" type="password" autoComplete="current-password" required error={error?.fieldError("password")} />
      {error && !error.problem.errors && (
        <p role="alert" className="text-split text-sm">
          {error.code === "INVALID_CREDENTIALS" ? "That email and password don’t match. Check them and try again." : error.message}
        </p>
      )}
      <button disabled={busy} className="bg-track hover:bg-track-deep text-lane w-full rounded-full py-3 font-semibold disabled:opacity-60">
        {busy ? "Signing in…" : "Sign in"}
      </button>
      <p className="text-mute text-sm">
        New to CommerceX?{" "}
        <Link href="/register" className="text-track font-medium underline underline-offset-4">
          Create an account
        </Link>
      </p>
    </form>
  );
}

export default function LoginPage() {
  return (
    <div className="mx-auto max-w-md px-4 py-16">
      <h1 className="display text-6xl">Sign in</h1>
      <Suspense>
        <LoginForm />
      </Suspense>
    </div>
  );
}
