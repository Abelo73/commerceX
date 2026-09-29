import { ChevronLeft, ChevronRight } from "lucide-react";

export function Pager({
  page,
  totalPages,
  total,
  noun,
  onPage,
}: {
  page: number;
  totalPages: number;
  total: number;
  noun: string;
  onPage: (page: number) => void;
}) {
  const button = "border-line inline-flex min-h-10 items-center gap-1 rounded-lg border bg-white px-3 text-sm font-medium hover:bg-tile disabled:pointer-events-none disabled:opacity-40";
  return (
    <nav aria-label="Pagination" className="mt-4 flex items-center justify-between gap-4 text-sm">
      <p className="text-mute figures">
        {total} {noun}
        {total === 1 ? "" : "s"}
        {totalPages > 1 && ` · page ${page + 1} of ${totalPages}`}
      </p>
      {totalPages > 1 && (
        <div className="flex gap-2">
          <button type="button" className={button} disabled={page === 0} onClick={() => onPage(page - 1)}>
            <ChevronLeft className="size-4" aria-hidden /> Previous
          </button>
          <button type="button" className={button} disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>
            Next <ChevronRight className="size-4" aria-hidden />
          </button>
        </div>
      )}
    </nav>
  );
}
