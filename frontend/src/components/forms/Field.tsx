import { useId } from "react";

export function Field({
  label,
  error,
  hint,
  ...input
}: React.InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }) {
  const id = useId();
  const describedBy = [error && `${id}-error`, hint && `${id}-hint`].filter(Boolean).join(" ") || undefined;
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`mt-1.5 w-full rounded-lg border bg-white px-3.5 py-2.5 outline-none focus:border-brand ${
          error ? "border-split" : "border-line"
        }`}
        {...input}
      />
      {hint && !error && (
        <p id={`${id}-hint`} className="text-mute mt-1 text-sm">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-split mt-1 text-sm">
          {error}
        </p>
      )}
    </div>
  );
}
