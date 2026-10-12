export const YanjiLiquidGlass = {
  targetId: 'yanji-tab-content',
  padding: 2,
  minHeight: 52,
  iconSize: 22,
  labelSize: 0,
  labelLineHeight: 0,
  labelGap: 0,
  itemPadding: 12,
  blurRadius: 16,
  downsampling: 2,
  maxUpdateFps: 60,
  saturation: 1.35,
  light: {
    tint: 'rgba(255, 255, 255, 0.72)',
    selection: 'rgba(59, 111, 246, 0.12)',
  },
  dark: {
    tint: 'rgba(21, 27, 40, 0.82)',
    selection: 'rgba(90, 136, 248, 0.22)',
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
