/**
 * Navigation material and geometry. Keep glass confined to the floating dock.
 *
 * The tint and selection are the paper domain's `canvas` and `brand` in RGBA:
 * the dock samples the paper behind it, so its veil has to be the same warmth
 * or it reads as a cold pane laid over warm paper. The geometry, blur radius,
 * downsampling and saturation below are the renderer's own inputs and stay put.
 */
export const YanjiLiquidGlass = {
  targetId: 'yanji-tab-content',
  padding: 5,
  minHeight: 72,
  iconSize: 20,
  labelSize: 14,
  labelLineHeight: 20,
  labelGap: 4,
  itemPadding: 8,
  blurRadius: 10,
  downsampling: 2,
  maxUpdateFps: 60,
  saturation: 1.35,
  light: {
    /** paper light `canvas` #F6F4EF */
    tint: 'rgba(246,244,239,0.36)',
    /** paper light `brand` #C84B31 */
    selection: 'rgba(200,75,49,0.14)',
  },
  dark: {
    /** paper dark `canvas` #111315 */
    tint: 'rgba(17,19,21,0.76)',
    /** paper dark `brand` #E06D53 */
    selection: 'rgba(224,109,83,0.20)',
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
