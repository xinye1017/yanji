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
  iconSize: 20,
  labelSize: 14,
  labelLineHeight: 20,
  labelGap: 3,
  itemPadding: 3,
  blurRadius: 10,
  downsampling: 2,
  maxUpdateFps: 60,
  saturation: 1.35,
  light: {
    /** paper light `canvas` #F6F4EF */
    tint: 'rgba(246,244,239,0.36)',
    /** The dock's own veil, sampled through the blur. */
    backdrop: 'rgba(246,244,239,0.85)',
    /** Hairline that keeps a light veil legible over a light page. */
    border: 'rgba(0,0,0,0.08)',
  },
  dark: {
    /** paper dark slate surface for contrast against deep canvas */
    tint: 'rgba(28,30,35,0.85)',
    /** Deeper than the tint: a dark dock has to read as a surface, not a hole. */
    backdrop: 'rgba(28,30,35,0.88)',
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
