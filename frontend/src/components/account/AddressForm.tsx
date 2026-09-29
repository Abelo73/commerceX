"use client";

import { useState } from "react";
import { Field } from "@/components/forms/Field";
import { ApiError, api } from "@/lib/client-api";
import type { Address } from "@/lib/types";

/** Creates a saved address; used in the address book and inline at checkout. */
export function AddressForm({ onSaved, onCancel, makeDefault = false }: {
  onSaved: (address: Address) => void;
  onCancel?: () => void;
  makeDefault?: boolean;
}) {
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const value = (name: string) => String(form.get(name) ?? "").trim() || undefined;
    setBusy(true);
    setError(null);
    try {
      const saved = await api<Address>("/me/addresses", {
        method: "POST",
        body: {
          label: value("label"),
          fullName: value("fullName"),
          line1: value("line1"),
          line2: value("line2"),
          city: value("city"),
          region: value("region"),
          postalCode: value("postalCode"),
          country: value("country")?.toUpperCase(),
          phone: value("phone"),
          isDefault: makeDefault || form.get("isDefault") === "on",
        },
      });
      onSaved(saved);
    } catch (e) {
      setError(e instanceof ApiError ? e : null);
    } finally {
      setBusy(false);
    }
  }

  const err = (field: string) => error?.fieldError(field);
  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      <Field label="Full name" name="fullName" autoComplete="name" required error={err("fullName")} />
      <Field label="Address" name="line1" autoComplete="address-line1" required error={err("line1")} />
      <Field label="Apartment, suite (optional)" name="line2" autoComplete="address-line2" error={err("line2")} />
      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="City" name="city" autoComplete="address-level2" required error={err("city")} />
        <Field label="State or region" name="region" autoComplete="address-level1" error={err("region")} />
        <Field label="Postal code" name="postalCode" autoComplete="postal-code" required error={err("postalCode")} />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Country code" name="country" autoComplete="country" required maxLength={2} defaultValue="US" hint="Two letters, e.g. US" error={err("country")} />
        <Field label="Phone (optional)" name="phone" type="tel" autoComplete="tel" error={err("phone")} />
      </div>
      <Field label="Label (optional)" name="label" placeholder="Home, work…" error={err("label")} />
      {!makeDefault && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" name="isDefault" className="accent-brand" /> Use as my default address
        </label>
      )}
      {error && !error.problem.errors && <p role="alert" className="text-split text-sm">{error.message}</p>}
      <div className="flex gap-3">
        <button disabled={busy} className="bg-brand hover:bg-brand-deep text-white rounded-lg px-5 py-2.5 font-semibold disabled:opacity-60">
          {busy ? "Saving…" : "Save address"}
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel} className="border-line rounded-lg border px-5 py-2.5">
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}
