/** Navigation material and geometry. Keep glass confined to the floating dock. */
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
    tint: 'rgba(242,247,255,0.36)',
    selection: 'rgba(53,106,230,0.12)',
  },
  dark: {
    tint: 'rgba(13,17,26,0.76)',
    selection: 'rgba(113,151,247,0.20)',
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
