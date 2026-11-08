/**
 * Navigation material and geometry. Keep glass confined to the floating dock.
 *
 * `tint` colours the sampled page inside the shader. `backdrop` is only the
 * container beneath that rendered image; it cannot veil an opaque capture.
 * Both share the paper palette's warmth. The native lens owns the directional
 * edge reflection, so there is no second outline around the capsule.
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
  saturation: 1.2,
  dragThreshold: 6,
  pressedOpacity: 0.68,
  light: {
    /** A warm neutral veil leaves room for the lit edge without whitening it. */
    tint: 'rgba(231,224,213,0.40)',
    backdrop: 'rgba(247,243,236,0.45)',
  },
  dark: {
    /** The shader skips white lifting for dark tints; this veil controls contrast. */
    tint: 'rgba(45,40,35,0.54)',
    backdrop: 'rgba(26,22,18,0.62)',
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
