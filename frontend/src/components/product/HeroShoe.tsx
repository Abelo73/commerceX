"use client";

import { useId } from "react";
import { colorwayHex, isLight, shade } from "@/lib/colors";

export type ShoePart = "plate" | "foam" | "upper";

/**
 * Detailed, shaded running shoe used for hero and spotlight moments. {@code highlight} lights up one part
 * (carbon plate, foam midsole or upper) for product storytelling.
 */
export function HeroShoe({
  color,
  highlight,
  label,
  className,
}: {
  color?: string;
  highlight?: ShoePart;
  label: string;
  className?: string;
}) {
  const id = useId().replace(/:/g, "");
  const base = colorwayHex(color);
  const light = isLight(base);
  const stripe = light ? "#2451c9" : "#ffffff";
  const glow = "#ff6a1a";

  return (
    <svg viewBox="30 95 570 200" role="img" aria-label={label} className={className}>
      <defs>
        <linearGradient id={`${id}-upper`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={shade(base, 0.28)} />
          <stop offset="0.55" stopColor={base} />
          <stop offset="1" stopColor={shade(base, -0.35)} />
        </linearGradient>
        <linearGradient id={`${id}-foam`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="1" stopColor="#d9e0ea" />
        </linearGradient>
        <pattern id={`${id}-mesh`} width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(30)">
          <circle cx="2" cy="2" r="1.1" fill={light ? "#1d2430" : "#ffffff"} />
        </pattern>
        <clipPath id={`${id}-clip`}>
          <path d={UPPER} />
        </clipPath>
        <filter id={`${id}-glow`} x="-20%" y="-50%" width="140%" height="200%">
          <feGaussianBlur stdDeviation="6" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      {/* ground shadow */}
      <ellipse cx="315" cy="282" rx="250" ry="10" fill="#000" opacity="0.18" />

      {/* upper */}
      <path
        d={UPPER}
        fill={`url(#${id}-upper)`}
        stroke={highlight === "upper" ? glow : light ? "#b9c2d0" : "none"}
        strokeWidth={highlight === "upper" ? 4 : 2}
        filter={highlight === "upper" ? `url(#${id}-glow)` : undefined}
      />
      <g clipPath={`url(#${id}-clip)`}>
        <rect x="60" y="100" width="520" height="140" fill={`url(#${id}-mesh)`} opacity={highlight === "upper" ? 0.32 : 0.12} />
        {/* heel counter and toe cap */}
        <path d="M60 240 L62 150 C66 132 80 124 100 124 L128 240 Z" fill={shade(base, -0.28)} opacity="0.85" />
        <path d="M468 176 C520 186 556 202 568 220 L568 240 L472 240 C462 216 460 194 468 176 Z" fill={shade(base, -0.22)} opacity="0.85" />
        {/* lane stripes */}
        <path d="M160 212 L440 190" stroke={stripe} strokeWidth="10" strokeLinecap="round" opacity="0.92" />
        <path d="M184 229 L466 206" stroke={stripe} strokeWidth="10" strokeLinecap="round" opacity="0.92" />
      </g>
      {/* laces */}
      <path
        d="M262 124 L246 150 M292 137 L276 163 M322 150 L306 176 M352 161 L338 185"
        stroke={stripe}
        strokeWidth="6"
        strokeLinecap="round"
        opacity="0.85"
      />
      {/* pull tab */}
      <path d="M72 150 C62 142 60 128 70 122" stroke={shade(base, -0.4)} strokeWidth="7" strokeLinecap="round" fill="none" />

      {/* midsole */}
      <path
        d={MIDSOLE}
        fill={`url(#${id}-foam)`}
        stroke={highlight === "foam" ? glow : "#c7cfdb"}
        strokeWidth={highlight === "foam" ? 4 : 2}
        filter={highlight === "foam" ? `url(#${id}-glow)` : undefined}
      />
      {/* carbon plate */}
      <path
        d="M86 252 C210 256 420 252 556 238"
        stroke={highlight === "plate" ? glow : "#2a313d"}
        strokeWidth={highlight === "plate" ? 6 : 3}
        strokeLinecap="round"
        fill="none"
        opacity={highlight === "plate" ? 1 : 0.35}
        filter={highlight === "plate" ? `url(#${id}-glow)` : undefined}
      />
      {/* outsole with tread */}
      <path d="M80 274 L552 268" stroke="#1d2430" strokeWidth="9" strokeLinecap="round" />
      <path
        d="M120 276 l8 -8 M170 276 l8 -8 M220 275 l8 -8 M270 275 l8 -8 M320 274 l8 -8 M370 274 l8 -8 M420 273 l8 -8 M470 272 l8 -8"
        stroke="#3a4252"
        strokeWidth="3"
        strokeLinecap="round"
      />
    </svg>
  );
}

const UPPER =
  "M64 240 L60 160 C60 138 74 124 100 124 C126 124 138 150 162 150 C178 130 196 110 222 110 L252 115 C292 135 342 160 418 175 C484 187 534 200 564 220 C574 230 570 240 556 240 Z";
const MIDSOLE =
  "M44 234 C150 242 452 245 578 222 C594 240 590 264 560 270 L82 276 C48 276 30 256 44 234 Z";
