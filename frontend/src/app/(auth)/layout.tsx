/** Sign-in and registration: full-screen split layout without the store header and footer. */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <main id="main" className="flex-1">
      {children}
    </main>
  );
}
