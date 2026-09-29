"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Field } from "@/components/forms/Field";
import { ApiError, useAuth } from "@/components/providers";

export default function RegisterPage() {
  const { register } = useAuth();
  const router = useRouter();
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError(null);
    try {
      await register({
        email: String(form.get("email")),
        password: String(form.get("password")),
        firstName: String(form.get("firstName")),
        lastName: String(form.get("lastName")),
      });
      router.push("/account");
    } catch (e) {
      setError(e instanceof ApiError ? e : null);
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-md px-4 py-16">
      <h1 className="display text-6xl">Create an account</h1>
      <form onSubmit={onSubmit} className="mt-8 space-y-5" noValidate>
        <div className="grid grid-cols-2 gap-4">
          <Field label="First name" name="firstName" autoComplete="given-name" required error={error?.fieldError("firstName")} />
          <Field label="Last name" name="lastName" autoComplete="family-name" required error={error?.fieldError("lastName")} />
        </div>
        <Field label="Email" name="email" type="email" autoComplete="email" required error={error?.fieldError("email")} />
        <Field
          label="Password"
          name="password"
          type="password"
          autoComplete="new-password"
          required
          hint="At least 10 characters."
          error={error?.fieldError("password")}
        />
        {error && !error.problem.errors && (
          <p role="alert" className="text-split text-sm">
            {error.code === "EMAIL_ALREADY_REGISTERED" ? "An account with this email already exists. Sign in instead." : error.message}
          </p>
        )}
        <button disabled={busy} className="bg-track hover:bg-track-deep text-lane w-full rounded-full py-3 font-semibold disabled:opacity-60">
          {busy ? "Creating account…" : "Create account"}
        </button>
        <p className="text-mute text-sm">
          Already have an account?{" "}
          <Link href="/login" className="text-track font-medium underline underline-offset-4">
            Sign in
          </Link>
        </p>
      </form>
    </div>
  );
}
