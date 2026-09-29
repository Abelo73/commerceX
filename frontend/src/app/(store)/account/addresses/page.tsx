"use client";

import { useCallback, useEffect, useState } from "react";
import { AddressForm } from "@/components/account/AddressForm";
import { ApiError, api } from "@/lib/client-api";
import type { Address } from "@/lib/types";

export default function AddressesPage() {
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => api<Address[]>("/me/addresses").then(setAddresses), []);
  useEffect(() => {
    load().catch(() => setAddresses([]));
  }, [load]);

  async function act(action: () => Promise<unknown>) {
    setError(null);
    try {
      await action();
      await load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "That didn’t work. Try again.");
    }
  }

  if (addresses === null) return <p className="text-mute">Loading addresses…</p>;

  return (
    <div className="space-y-8">
      {addresses.length === 0 && !adding && <p>You haven’t saved an address yet.</p>}
      <ul className="grid gap-4 sm:grid-cols-2">
        {addresses.map((a) => (
          <li key={a.id} className={`rounded-xl border bg-white p-5 ${a.isDefault ? "border-brand" : "border-line"}`}>
            <p className="font-semibold">
              {a.label ?? a.fullName}
              {a.isDefault && <span className="text-brand ml-2 text-sm font-medium">Default</span>}
            </p>
            <address className="text-mute mt-1 text-sm not-italic">
              {a.fullName}, {a.line1}
              {a.line2 ? `, ${a.line2}` : ""}, {a.city} {a.postalCode}, {a.country}
            </address>
            <div className="mt-3 flex gap-4 text-sm">
              {!a.isDefault && (
                <button
                  type="button"
                  className="text-brand underline underline-offset-4"
                  onClick={() => act(() => api(`/me/addresses/${a.id}`, { method: "PUT", body: { ...a, isDefault: true } }))}
                >
                  Make default
                </button>
              )}
              <button type="button" className="text-mute underline underline-offset-4 hover:text-ink" onClick={() => act(() => api(`/me/addresses/${a.id}`, { method: "DELETE" }))}>
                Delete
              </button>
            </div>
          </li>
        ))}
      </ul>
      {error && <p role="alert" className="text-split text-sm">{error}</p>}
      {adding ? (
        <div className="max-w-xl">
          <h2 className="condensed mb-4 text-2xl font-semibold">New address</h2>
          <AddressForm onSaved={() => { setAdding(false); load(); }} onCancel={() => setAdding(false)} />
        </div>
      ) : (
        <button type="button" onClick={() => setAdding(true)} className="border-ink rounded-lg border px-5 py-2.5 font-medium hover:bg-ink hover:text-white">
          Add an address
        </button>
      )}
    </div>
  );
}
