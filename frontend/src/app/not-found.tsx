import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-24 sm:px-6">
      <h1 className="display text-6xl">Off course.</h1>
      <p className="text-mute mt-4 max-w-prose text-lg">This page doesn’t exist, or the product is no longer sold.</p>
      <Link href="/shop" className="bg-track hover:bg-track-deep text-lane mt-8 inline-block rounded-full px-6 py-3 font-semibold">
        Back to the shop
      </Link>
    </div>
  );
}
