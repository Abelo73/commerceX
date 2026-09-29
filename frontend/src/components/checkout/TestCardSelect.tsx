"use client";

import { TEST_CARDS } from "@/lib/orders";

/** Payment method picker for the mock provider. Replaced by the real provider's card element later. */
export function TestCardSelect({ value, onChange }: { value: string; onChange: (token: string) => void }) {
  return (
    <label className="block text-sm">
      <span className="font-medium">Payment method</span>
      <span className="text-mute block">Test mode: no real payment is taken.</span>
      <select value={value} onChange={(e) => onChange(e.target.value)} className="border-line mt-1.5 rounded-lg border bg-white px-3 py-2.5">
        {TEST_CARDS.map((card) => (
          <option key={card.token} value={card.token}>
            {card.label}
          </option>
        ))}
      </select>
    </label>
  );
}

