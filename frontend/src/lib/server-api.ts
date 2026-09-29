import "server-only";
import { notFound } from "next/navigation";

const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

/**
 * Public catalog reads from Server Components, straight to the backend (no proxy hop).
 * Cached for {@code revalidate} seconds; a 404 renders the not-found page.
 */
export async function serverGet<T>(path: string, revalidate = 60): Promise<T> {
  const response = await fetch(`${backend}/api/v1${path}`, { next: { revalidate } });
  if (response.status === 404) {
    notFound();
  }
  if (!response.ok) {
    throw new Error(`GET ${path} failed with ${response.status}`);
  }
  return response.json() as Promise<T>;
}
