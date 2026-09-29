import { colorwayHex, isLight } from "@/lib/colors";

type Kind = "shoe" | "shorts" | "top" | "socks";

/** Picks a silhouette from the product's category and tags. */
export function artKind(categorySlug?: string, tags: string[] = []): Kind {
  if (categorySlug !== "apparel") return "shoe";
  if (tags.includes("shorts")) return "shorts";
  if (tags.includes("socks")) return "socks";
  return "top";
}

/**
 * Tinted product silhouette used until real photography exists. The shoe carries the brand's two
 * lane stripes; the midsole is always light, like foam.
 */
export function ProductArt({
  kind,
  color,
  label,
  className,
}: {
  kind: Kind;
  color?: string;
  label: string;
  className?: string;
}) {
  const fill = colorwayHex(color);
  const stroke = isLight(fill) ? "#b9c2d0" : "none";
  const stripe = isLight(fill) ? "#2451c9" : "#ffffff";

  return (
    // The shoe is cropped tightly so it fills its frame; garments keep the square-ish canvas.
    <svg viewBox={kind === "shoe" ? "8 52 232 84" : "0 0 240 160"} role="img" aria-label={label} className={className}>
      {kind === "shoe" && (
        <g transform="translate(0 18)">
          {/* upper */}
          <path
            d="M30 94 L28 66 C28 56 34 50 44 50 C54 50 58 60 68 60 C74 52 80 44 90 44 L102 46 C118 54 138 64 168 70 C194 75 214 80 226 88 C230 92 228 94 222 94 Z"
            fill={fill}
            stroke={stroke}
            strokeWidth="1.5"
          />
          {/* lane stripes */}
          <path d="M64 84 L176 76" stroke={stripe} strokeWidth="4" strokeLinecap="round" opacity="0.9" />
          <path d="M72 90 L186 82" stroke={stripe} strokeWidth="4" strokeLinecap="round" opacity="0.9" />
          {/* laces */}
          <path d="M104 50 L98 60 M116 55 L110 65 M128 60 L122 70" stroke={stripe} strokeWidth="2.5" strokeLinecap="round" opacity="0.8" />
          {/* midsole */}
          <path
            d="M20 92 C60 95 180 96 230 88 C236 96 234 106 222 108 L32 110 C20 110 14 102 20 92 Z"
            fill="#f8fafc"
            stroke="#c7cfdb"
            strokeWidth="1.5"
          />
          {/* outsole */}
          <path d="M30 110 L222 108" stroke="#1d2430" strokeWidth="4" strokeLinecap="round" />
        </g>
      )}
      {kind === "shorts" && (
        <path
          d="M64 30 H176 L192 128 H134 L120 72 L106 128 H48 Z"
          fill={fill}
          stroke={stroke}
          strokeWidth="1.5"
        />
      )}
      {kind === "top" && (
        <path
          d="M88 22 C100 30 140 30 152 22 L206 44 L192 76 L172 68 V138 H68 V68 L48 76 L34 44 Z"
          fill={fill}
          stroke={stroke}
          strokeWidth="1.5"
        />
      )}
      {kind === "socks" && (
        <path
          d="M96 18 H140 V92 C140 110 162 112 176 118 C192 126 188 146 168 146 H110 C92 146 90 130 94 116 Z"
          fill={fill}
          stroke={stroke}
          strokeWidth="1.5"
        />
      )}
    </svg>
  );
}
