import type { Metadata } from "next";
import { Plus_Jakarta_Sans } from "next/font/google";
import { Providers } from "@/components/providers";
import "./globals.css";

const jakarta = Plus_Jakarta_Sans({
  subsets: ["latin"],
  variable: "--font-jakarta",
  display: "swap",
});

export const metadata: Metadata = {
  title: { default: "Running shoes and gear | CommerceX", template: "%s | CommerceX" },
  description: "Road, trail and track running shoes, spikes and apparel.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={jakarta.variable}>
      <body className="flex min-h-screen flex-col antialiased">
        <a href="#main" className="bg-brand text-white sr-only rounded px-3 py-2 focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50">
          Skip to content
        </a>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
