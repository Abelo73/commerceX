"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthShell } from "@/components/auth/AuthShell";
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
    <form onSubmit={onSubmit} className="space-y-5" noValidate>
      <Field label="Email" name="email" type="email" autoComplete="email" required error={error?.fieldError("email")} />
      <Field label="Password" name="password" type="password" autoComplete="current-password" required error={error?.fieldError("password")} />
      {error && !error.problem.errors && (
        <p role="alert" className="text-split text-sm">
          {error.code === "INVALID_CREDENTIALS" ? "That email and password don’t match. Check them and try again." : error.message}
        </p>
      )}
      <button disabled={busy} className="bg-brand hover:bg-brand-deep text-white w-full rounded-lg py-3 font-semibold disabled:opacity-60">
        {busy ? "Signing in…" : "Sign in"}
      </button>
      <p className="text-mute text-sm">
        New to CommerceX?{" "}
        <Link href="/register" className="text-brand font-medium underline underline-offset-4">
          Create an account
        </Link>
      </p>
    </form>
  );
}

export default function LoginPage() {
  return (
    <AuthShell
      image="/images/auth-trail.jpg"
      imageAlt="A trail runner cresting a grassy hill above the mountains"
      title="Welcome back"
      subtitle="Sign in to track orders and check out faster."
    >
      <Suspense>
        <LoginForm />
      </Suspense>
    </AuthShell>
  );
}
