export default function Loading() {
  return (
    <div className="mx-auto max-w-7xl px-4 pt-10 sm:px-6" aria-busy="true" aria-label="Loading products">
      <div className="bg-tile h-14 w-72 animate-pulse rounded" />
      <ul className="mt-20 grid grid-cols-2 gap-x-4 gap-y-10 lg:grid-cols-4">
        {Array.from({ length: 8 }, (_, i) => (
          <li key={i}>
            <div className="bg-tile aspect-[4/3] animate-pulse rounded-lg" />
            <div className="bg-tile mt-3 h-5 w-2/3 animate-pulse rounded" />
          </li>
        ))}
      </ul>
    </div>
  );
}
