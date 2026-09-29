/** Colourway names used in the catalog → swatch colours. Unknown names get a stable neutral. */
const COLORWAYS: Record<string, string> = {
  "track blue": "#2451c9",
  asphalt: "#2a313d",
  chalk: "#fbfcfd",
  "signal red": "#c9302f",
  navy: "#1e2a4a",
  grey: "#8a94a6",
  moss: "#4f6b4a",
  sand: "#d6c6a5",
  black: "#1d2430",
  white: "#fbfcfd",
  red: "#c9302f",
  blue: "#2451c9",
};

export function colorwayHex(name: string | undefined): string {
  return (name && COLORWAYS[name.toLowerCase()]) || "#9aa3b2";
}

/** Light colourways need an outline to stay visible on chalk. */
export function isLight(hex: string): boolean {
  const value = parseInt(hex.slice(1), 16);
  const r = (value >> 16) & 255;
  const g = (value >> 8) & 255;
  const b = value & 255;
  return 0.2126 * r + 0.7152 * g + 0.0722 * b > 200;
}

/** Mixes a colour toward white (amount > 0) or black (amount < 0), for shading. */
export function shade(hex: string, amount: number): string {
  const value = parseInt(hex.slice(1), 16);
  const target = amount > 0 ? 255 : 0;
  const t = Math.abs(amount);
  const channel = (shift: number) => Math.round(((value >> shift) & 255) * (1 - t) + target * t);
  return `#${[16, 8, 0].map((s) => channel(s).toString(16).padStart(2, "0")).join("")}`;
}
