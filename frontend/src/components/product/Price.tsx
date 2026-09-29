import { formatMoney } from "@/lib/format";
import type { Money } from "@/lib/types";

/** Current price, with the compare-at price struck through when the item is reduced. */
export function Price({ price, compareAt, from = false }: { price: Money; compareAt?: Money; from?: boolean }) {
  const reduced = compareAt && compareAt.amount > price.amount;
  return (
    <span className="figures inline-flex items-baseline gap-2">
      <span className={reduced ? "text-split font-semibold" : "font-semibold"}>
        {from ? "From " : ""}
        {formatMoney(price)}
      </span>
      {reduced && (
        <s className="text-mute text-sm">
          <span className="sr-only">was </span>
          {formatMoney(compareAt)}
        </s>
      )}
    </span>
  );
}
