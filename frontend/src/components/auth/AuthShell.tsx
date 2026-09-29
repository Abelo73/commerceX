import Image from "next/image";
import Link from "next/link";
import { Check } from "lucide-react";

const BENEFITS = ["Track every order from paid to delivered", "Check out faster with saved addresses", "Keep a wishlist of the pairs you’re eyeing"];

/**
 * Split-screen frame for sign-in and registration: photo panel with what an account gives you,
 * and the form on a clean white side. On small screens the photo becomes a short banner.
 */
export function AuthShell({
  image,
  imageAlt,
  title,
  subtitle,
  children,
}: {
  image: string;
  imageAlt: string;
  title: string;
  subtitle: string;
  children: React.ReactNode;
}) {
  return (
    <div className="grid min-h-svh lg:grid-cols-2">
      <aside className="bg-navy relative isolate flex min-h-56 flex-col justify-between overflow-hidden p-6 text-white sm:p-10 lg:min-h-svh">
        <Image src={image} alt={imageAlt} fill priority sizes="(min-width: 1024px) 50vw, 100vw" className="-z-10 object-cover" />
        <div aria-hidden className="from-navy via-navy/60 absolute inset-0 -z-10 bg-gradient-to-t to-navy/20" />
        <Link href="/" className="text-2xl font-extrabold tracking-tight">
          CommerceX
        </Link>
        <div className="hidden lg:block">
          <p className="display max-w-md text-4xl">An account keeps your running kit in one place.</p>
          <ul className="mt-8 space-y-3">
            {BENEFITS.map((benefit) => (
              <li key={benefit} className="flex items-center gap-3 text-white/90">
                <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-white/15 backdrop-blur">
                  <Check aria-hidden className="h-3.5 w-3.5" strokeWidth={3} />
                </span>
                {benefit}
              </li>
            ))}
          </ul>
        </div>
      </aside>

      <div className="flex items-center justify-center px-4 py-12 sm:px-10">
        <div className="w-full max-w-md">
          <h1 className="display text-4xl">{title}</h1>
          <p className="text-mute mt-2">{subtitle}</p>
          <div className="mt-8">{children}</div>
          <p className="text-mute mt-10 text-sm">
            <Link href="/" className="hover:text-ink underline underline-offset-4">
              Back to the shop
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
