/**
 * Navigation material and geometry. Keep glass confined to the floating dock.
 *
 * The tint is the paper domain's `canvas` in RGBA: the dock samples the paper
 * behind it, so its veil has to be the same warmth or it reads as a cold pane
 * laid over warm paper. `backdrop` and `border` are the dock's own surface and
 * hairline — the parts that stay when the blur is unavailable — and live here
 * so the dock reads one material instead of resolving half of it from the theme
 * and half from literals in the component. The geometry, blur radius,
 * downsampling and saturation below are the renderer's own inputs.
 */
export const YanjiLiquidGlass = {
  targetId: 'yanji-tab-content',
  padding: 4,
  minHeight: 60,
  iconSize: 23,
  labelSize: 12,
  labelLineHeight: 16,
  labelGap: 4,
  itemPadding: 3,
  blurRadius: 18,
  downsampling: 2,
  maxUpdateFps: 60,
  saturation: 1.6,
  light: {
    /**
     * A touch under the paper, so the lens has somewhere to shine.
     *
     * The shader adds its edge highlight *after* tinting, and clamps at white.
     * Tinting toward white left the interior at 251/255 — four levels of
     * headroom — so the highlight had nowhere to go and the dock read as a flat
     * white card. Sitting the body just below the canvas (238 vs 247) leaves
     * real room for the lit rim to show, which is what makes it look like glass.
     */
    tint: 'rgba(226,223,217,0.34)',
    /**
     * The dock's own veil. It used to be 85% opaque, which is why the glass
     * read as a solid white slab: the veil is painted under the overlay, so it
     * hid the blur instead of tinting it — a dark pixel behind the dock came
     * through at 227/255, essentially erased. At 45% the same pixel survives
     * at 177, so page content actually shows through the lens and the dock
     * reads as glass rather than as a card.
     */
    backdrop: 'rgba(246,244,239,0.45)',
    /** Hairline that keeps a light veil legible over a light page. */
    border: 'rgba(0,0,0,0.08)',
  },
  dark: {
    /** Same rule as light: sit a step under the surface so the rim highlight is visible. */
    tint: 'rgba(46,44,42,0.38)',
    /**
     * Dark needs more veil than light: a clear lens over a dark page turns into
     * a hole the labels float in. 62% keeps the dock a surface while still
     * letting the page's own light and dark areas move through it.
     */
    backdrop: 'rgba(28,30,35,0.62)',
    /** Hairline that separates a dark veil from a dark page. */
    border: 'rgba(255,255,255,0.16)',
  },

} as const;

/** Enough room to scroll the final action fully above the glass, at any font size. */
export function bottomTabLayout(fontScale: number, safeBottom: number, gap: number) {
  const glass = YanjiLiquidGlass;
  const height = Math.max(glass.minHeight, Math.ceil(
    glass.iconSize + glass.labelGap + glass.labelLineHeight * Math.max(1, fontScale)
      + 2 * (glass.padding + glass.itemPadding),
  ));
  const bottom = Math.max(safeBottom, gap);
  return { height, bottom, contentPadding: height + bottom + gap };
}
