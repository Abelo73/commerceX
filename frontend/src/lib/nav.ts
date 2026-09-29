/** The four lanes of the store, in track order. */
export const LANES = [
  { lane: 1, slug: "road", name: "Road", blurb: "Cushioned miles on pavement" },
  { lane: 2, slug: "trail", name: "Trail", blurb: "Grip for dirt, rock and mud" },
  { lane: 3, slug: "track", name: "Track", blurb: "Spikes and flats for the oval" },
  { lane: 4, slug: "apparel", name: "Apparel", blurb: "Layers that keep up" },
] as const;
