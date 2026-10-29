/**
 * Yanji visual language: quiet study journal.
 * Semantic surfaces may be elevated controls or unboxed plans and timelines.
 * Typography and readable rhythm, rather than card outlines, establish hierarchy.
 *
 * The 素笺与寸阴 paper domain (`YanjiPaper*`) is the only colour and type
 * vocabulary: every body surface and every shared component in `YanjiUI.tsx`
 * renders with it, and so does the navigation dock. The remaining `Yanji*`
 * scales provide neutral geometry, touch targets, motion and shared shadows.
 */

import type { ViewStyle } from 'react-native';

/** Semantic radius scale. Raw dp literals are forbidden at call sites. */
export const YanjiRadius = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 28,
  full: 9999,
} as const;

/** Spacing scale shared by every layout in `src/**`. */
export const YanjiSpacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const;

/**
 * Minimum touch targets (WCAG 2.5.5 / platform HIG). Icon-only controls must
 * reach `min`; primary actions should use `comfortable`.
 */
export const YanjiTouch = {
  min: 44,
  comfortable: 48,
} as const;

/**
 * Cast shadows — diffuse elevation.
 *
 * Centralised because iOS and Android disagree: iOS wants shadowColor/Offset/
 * Radius/Opacity, Android only understands `elevation`.
 */
export const YanjiShadow = {
  /** A card resting on the desk — flat and pure, relying on 0.5px hairline and surface steps rather than heavy shadow. */
  sheet: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 4,
      offsetY: 1,
      opacity: isDark ? 0.18 : 0.03,
      elevation: 0.5,
    }),
  /** Floating modals & sheets — gentle, clean scrim-depth elevation. */
  floating: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 12,
      offsetY: 4,
      opacity: isDark ? 0.30 : 0.06,
      elevation: 3,
    }),
  /** Pressed feedback — a sheet pushed toward the desk. */
  pressed: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 2,
      offsetY: 0.5,
      opacity: isDark ? 0.12 : 0.02,
      elevation: 0,
    }),
} as const;

function PlatformShadow(
  isDark: boolean,
  spec: { radius: number; offsetY: number; opacity: number; elevation: number },
): ViewStyle {
  // Dark surfaces need black for shadow depth; light elevation shares the
  // paper palette's ink so it cannot drift into a separate colour family.
  return {
    shadowColor: isDark ? '#000000' : YanjiPaperColors.light.ink,
    shadowOffset: { width: 0, height: spec.offsetY },
    shadowOpacity: spec.opacity,
    shadowRadius: spec.radius,
    elevation: spec.elevation,
  };
}

/**
 * Motion — 「纸上研迹」moves like paper: light, spring-settled, never bouncy.
 * Values are reanimated spring configs, shared by every animated component.
 */
export const YanjiMotion = {
  spring: {
    /** Default settle for sheets, pills and fades. */
    gentle: { damping: 18, stiffness: 140, mass: 1 },
    /** Selection changes that must feel immediate with lively bounce. */
    snappy: { damping: 14, stiffness: 240, mass: 0.8 },
    /** Press-down feedback. */
    press: { damping: 15, stiffness: 320, mass: 0.8 },
  },
  duration: {
    fast: 120,
    base: 220,
    slow: 360,
  },
} as const;

// ---------------------------------------------------------------------------
// 素笺与寸阴 (Paper & Chrono) — the body design domain.
//
// The scales above stay exactly as the liquid-glass navigation consumes them
// (BottomTabBar / liquidGlass / useBottomTabLayout are frozen). Everything the
// *body* renders — Today, Review, Focus, Settings and the shared sheets — moves
// onto this domain instead, so there is one paper vocabulary rather than a
// second set of numbers scattered across screens.
//
// Contrast policy (measured, WCAG 2.1 relative luminance):
//   `ink`      > 11:1 on every canvas/surface/subtle/elevated step of its mode.
//   `secondary` >= 4.5:1 on every step — this is the readable body/meta tier.
//   `muted`     < 4.5:1 by design: decorative rules, tick marks, disabled
//               controls and struck-through archive rows only. It must never
//               carry a timestamp, an axis label or an unread task title.
//   `brandInk` is the readable brand text tier on the warm parchment canvas
//               (plain `brand` is only 4.24:1 there — good enough for a CTA
//               fill, not for text). Use `brand` + `onBrand` for fills.
//   `amber`     the paused/waiting accent, readable as text on every step.
//
// The *Subtle tints are measured too, and for the same reason. A capsule
// (badge, chip, selected row, week cell) paints its own label at `captionMeta`
// — 12sp — on top of its tint, so the tint's alpha is capped by the 4.5:1 that
// label needs once the tint is composited over whatever surface the capsule
// lands on. Raising the alpha for a stronger wash is what breaks dark-mode
// badges and selected chips, so those numbers are constraints, not taste.
// ---------------------------------------------------------------------------

export const YanjiPaperColors = {
  light: {
    canvas: '#F6F4EF',
    elevated: '#FAF8F4',
    surface: '#FFFFFF',
    subtle: '#EDEAE2',
    /** Full-bleed wash behind the 寸阴尺 strip — a hair of ink, not a surface. */
    vernierWash: 'rgba(28,30,33,0.03)',
    ink: '#1C1E21',
    secondary: '#565960',
    muted: '#94969C',
    hairline: 'rgba(28,30,33,0.08)',
    /** Vernier ticks. Decorative by definition, so they sit below the 4.5:1 text bar. */
    tickMinor: 'rgba(28,30,33,0.20)',
    tickMajor: 'rgba(28,30,33,0.45)',
    brand: '#C84B31',
    /** Readable brand text on parchment. Measured 5.43:1 on canvas, 5.96:1 on surface. */
    brandInk: '#B23A22',
    /**
     * Selection tints. The alpha is measured, not aesthetic: it is the highest
     * value that still keeps the capsule's own label (`brandInk` / `amber` at
     * `captionMeta`, 12sp) above 4.5:1 when composited over every surface the
     * capsule can land on — canvas, elevated, surface and subtle. Raising it
     * fails dark-mode badges and selected chips, so it stays here.
     */
    brandSubtle: 'rgba(200,75,49,0.07)',
    onBrand: '#FFFFFF',
    /** Paused / waiting accent. Measured >= 5.30:1 across the four paper steps, and 4.81:1 on its own tint. */
    amber: '#875400',
    amberSubtle: 'rgba(135,84,0,0.07)',
    /** Modal / sheet scrim, derived from `ink` so the veil shares the paper's warmth. */
    scrim: 'rgba(28,30,33,0.32)',
    /** Destructive actions. Measured 6.28:1 on `surface`, 5.71:1 on `canvas`, and 4.71:1 on its own tint. */
    danger: '#B52B2B',
    /** 7% tint matching `brandSubtle` / `amberSubtle` / `positiveSubtle` while keeping `danger` >= 4.5:1 on `subtle`. */
    dangerSubtle: 'rgba(181,43,43,0.07)',
    /**
     * A period that improved on the one before it. Measured worst case 5.49:1
     * across the four paper steps, and 5.00:1 on its own tint below — the same
     * readable-text bar `amber` and `brandInk` hold.
     *
     * The board's 竹青 `#2D7A5E` is the *subject* colour for 408 and stays one;
     * as badge text it measures 4.31:1 on `subtle` and would fail here.
     */
    positive: '#27684F',
    /** See the tint note above: alpha is capped by the badge label's 4.5:1. */
    positiveSubtle: 'rgba(39,104,79,0.07)',
  },
  dark: {
    /**
     * Warm charcoal, not a near-black. The old #111315 sat at 0.6% luminance,
     * which read as a hole punched in the screen: every surface above it had to
     * climb hard to separate, and the warm paper identity of the light theme
     * disappeared entirely. This stack keeps the same hue family as the
     * parchment (a trace of red over blue) and starts at ~1.0% instead, so the
     * page reads as dark paper rather than as an off screen. Each step is a
     * visible move, not a rounding error.
     */
    canvas: '#1B1917',
    elevated: '#211F1C',
    surface: '#262320',
    subtle: '#302C28',
    vernierWash: 'rgba(239,234,225,0.03)',
    ink: '#EFEAE1',
    secondary: '#A8A199',
    muted: '#6E6862',
    hairline: 'rgba(239,234,225,0.09)',
    tickMinor: 'rgba(239,234,225,0.22)',
    tickMajor: 'rgba(239,234,225,0.50)',
    brand: '#E8705A',
    /** Lifted from `brand` so labels stay >= 4.5:1 (measured 4.93:1) over the 10% `brandSubtle` wash on `subtle`. */
    brandInk: '#F08A74',
    /** See the light-mode tint note: 10% dark wash paired with `brandInk` to stay >= 4.5:1 across all surfaces. */
    brandSubtle: 'rgba(232,112,90,0.10)',
    onBrand: '#1B1917',
    amber: '#DFA76A',
    amberSubtle: 'rgba(223,167,106,0.10)',
    /** Heavier veil than light mode, matching the dark surface stack. */
    scrim: 'rgba(0,0,0,0.56)',
    /** Destructive actions. Measured 6.24:1 on `surface`, 6.61:1 on `canvas`. */
    danger: '#F5827F',
    /** See the light-mode tint note: keep the destructive label above 4.5:1. */
    dangerSubtle: 'rgba(245,130,127,0.10)',
    /**
     * See the light-mode note. Dark needs its own value rather than a reuse of
     * light's: `#27684F` would be unreadable here, and the board's 竹青
     * `#2D7A5E` measures 4.39:1 on `subtle`. Measured worst case 5.09:1 on the
     * four steps, 4.80:1 on its own tint.
     */
    positive: '#5FBE96',
    positiveSubtle: 'rgba(95,190,150,0.10)',
  },
} as const;

const paperImmersiveBase = {
  /**
   * The immersion field is the darkest surface the app shows, but it still has
   * to read as a room with the lights down rather than as an unlit screen: it
   * sits a step below the paper canvas and keeps the same warm bias.
   */
  canvas: '#141210',
  ink: '#F6F4EF',
  secondary: '#C4C7CE',
  subtitle: '#8A8F99',
  muted: '#6E737D',
  /** Destructive text on the always-dark immersion field; paper `danger` is too dark here. */
  danger: '#F87171',
  subtle: 'rgba(255,255,255,0.08)',
  pillBg: 'rgba(255,255,255,0.05)',
  pillBorder: 'rgba(255,255,255,0.08)',
  /**
   * Horizon tide — the wash that rises from the bottom edge.
   *
   * Colour and peak opacity are separate roles because `react-native-svg`
   * reads a gradient stop's alpha from `stopOpacity` alone: `extractGradient`
   * masks any alpha embedded in `stopColor` back out, and a missing
   * `stopOpacity` resolves to fully opaque. An `rgba(...)` string in `tideColor`
   * would therefore paint a solid slab instead of a wash.
   */
  tideOpacity: 0.14,
  secondaryActionBg: 'rgba(255,255,255,0.06)',
  secondaryActionBorder: 'rgba(255,255,255,0.10)',
  secondaryActionText: '#D5D8DF',
  primaryActionText: '#F6F4EF',
} as const;

/**
 * The immersion field for a running / paused focus session. The navigation is
 * hidden there, so this palette owns the whole screen — including the area
 * behind the status bar.
 */
export const YanjiPaperImmersive = {
  running: {
    ...paperImmersiveBase,
    accent: '#3A9B78',
    tideColor: '#2D7A5E',
    primaryActionBg: 'rgba(246,244,239,0.12)',
    primaryActionBorder: 'rgba(246,244,239,0.22)',
  },
  paused: {
    ...paperImmersiveBase,
    accent: '#DDA059',
    tideColor: '#DDA059',
    primaryActionBg: 'rgba(221,160,89,0.18)',
    primaryActionBorder: 'rgba(221,160,89,0.34)',
  },
} as const;

/**
 * Paper type scale. `fontFamily`/`fontVariant` are additive to the frozen
 * numeric scale: Android ships `serif` and `monospace` system faces, so the
 * editorial serif and tabular figures need no bundled font file. Letter
 * tracking from the HTML source is expressed in `em` and is deliberately NOT
 * ported — RN takes dp, and a negative dp track on CJK serif clips strokes.
 */
export const YanjiPaperTypography = {
  displayTimer: {
    fontSize: 76,
    lineHeight: 82,
    fontWeight: '600' as const,
    fontFamily: 'sans-serif-medium',
    fontVariant: ['tabular-nums'] as const,
  },
  displayDial: {
    fontSize: 84,
    lineHeight: 92,
    fontWeight: '700' as const,
    fontFamily: 'sans-serif-medium',
    fontVariant: ['tabular-nums'] as const,
  },
  metricHero: {
    fontSize: 44,
    lineHeight: 52,
    fontWeight: '700' as const,
    fontVariant: ['tabular-nums'] as const,
  },
  /**
   * A summary metric — the three numbers on 回顾. One step under `metricHero`:
   * three of these sit side by side, so the hero size made them compete with the
   * page title instead of reading as a row of facts.
   */
  metricSummary: {
    fontSize: 30,
    lineHeight: 38,
    fontWeight: '700' as const,
    fontFamily: 'serif',
    fontVariant: ['tabular-nums'] as const,
  },
  metricSub: {
    fontSize: 20,
    lineHeight: 28,
    fontWeight: '700' as const,
    fontVariant: ['tabular-nums'] as const,
  },
  metricDaily: {
    fontSize: 52,
    lineHeight: 56,
    fontWeight: '700' as const,
    fontFamily: 'serif',
    fontVariant: ['tabular-nums'] as const,
  },
  metricSession: {
    fontSize: 20,
    lineHeight: 28,
    fontWeight: '600' as const,
    fontVariant: ['tabular-nums'] as const,
  },
  titleGreeting: {
    fontSize: 24,
    lineHeight: 32,
    fontWeight: '700' as const,
    fontFamily: 'serif',
  },
  titleLedger: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '600' as const,
  },
  titleEditorial: {
    fontSize: 20,
    lineHeight: 28,
    fontWeight: '600' as const,
  },
  /**
   * A section heading inside a page — 「今日计划」「今日随笔」「今日学习节奏」.
   *
   * It ranks above the body by *weight* more than by size: these are set at
   * 16/900 with a hair of tracking, so a group title outranks the 15sp body
   * beneath it and can be found by scanning the left edge of the page.
   *
   * This is the *page* tier. Compact headings inside a sheet or a card
   * (`YanjiPaperSectionHeading` inside TaskEditorModal, the settings sheets and
   * the backup section) stay on `titleSection`: they sit in a column that is
   * already framed, so a page-sized heading would shout inside it.
   */
  titlePageSection: {
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '900' as const,
    letterSpacing: 0.3,
  },
  /**
   * A compact group heading inside a sheet, card or row.
   *
   * Matches `.app-sec h4` in the design reference: 13/900 with a hair of
   * tracking, which outranks 15sp body without growing the row it sits in.
   */
  titleSection: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '900' as const,
    letterSpacing: 0.4,
  },
  titleGroup: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '700' as const,
  },
  bodyPrimary: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '400' as const,
  },
  /** A plan title — `.row-title` is 13.5/1.5/500, one step under body copy. */
  bodyTask: {
    fontSize: 13.5,
    lineHeight: 20,
    fontWeight: '500' as const,
  },
  bodyJournal: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '400' as const,
    fontFamily: 'serif',
  },
  bodyWriting: {
    fontSize: 15,
    lineHeight: 24,
    fontWeight: '400' as const,
    fontFamily: 'serif',
  },
  bodyExcerpt: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '600' as const,
  },
  captionMeta: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500' as const,
    fontVariant: ['tabular-nums'] as const,
  },
} as const;

export const YanjiPaperLayout = {
  completionDialogMaxWidth: 340,
  pageGutter: 22,
  sectionGap: 24,
  rowMinHeight: 48,
  activeSessionMinHeight: 76,
  activeSessionReadoutMinWidth: 132,
  /** Visible compact segment; the radio keeps a full row-height touch target. */
  compactSegmentHeight: 28,
  borderlessSegmentHeight: 34,
  borderlessSegmentPadding: 2,
  /** Visible subject chip inside a full-height touch target. */
  subjectChipHeight: 32,
  hairlineWidth: 0.5,
  vernierTickWidth: 14,
  vernierMinHeight: 96,
  vernierMinorTickHeight: 10,
  vernierMajorTickHeight: 20,
  vernierPointerHeight: 28,
  /** The distribution bar under the hero: a rule, not a block (`.spectrum` is 6px). */
  spectrumHeight: 6,
  spectrumGap: 2,
  subjectSwatchSize: 7,
  taskCheckboxSize: 18,
  taskCheckboxStroke: 1.5,
  taskCheckIconSize: 12,
  taskTagPadding: 2,
  /** The tag's horizontal inset — wider than the vertical so it reads as a chip. */
  taskTagPaddingHorizontal: 6,
  /** The checkbox owns a full touch column; compact gaps keep the text nearby. */
  taskRowPadding: 8,
  taskRowGap: 4,
  /** The sub-line hugs its title — 2dp reads as one line, not two blocks. */
  taskSubMarginTop: 2,
  compactActionHeight: 28,
  /** Inline capsule inset — AB-01's `5px 10px` play trigger. */
  compactActionPadding: 10,
  /**
   * The day's rhythm strip. Bars share the full width — a fixed 4dp bar with a
   * 3dp gap reads as a row of dots, which is what made the section look like a
   * pagination indicator.
   */
  rhythmBarGap: 3,
  /** Tall enough for an hour of study to have a readable slope, not a tick. */
  rhythmChartHeight: 46,
  /**
   * The height a bar falls back to when an hour has a measurable amount that
   * rounds below a pixel — and the tick marking the current hour, which is only
   * drawn once the strip has a bar to relate to. Hours with nothing saved draw
   * nothing at all, so an empty day is a clean axis rather than a second
   * dashed line.
   */
  rhythmEmptyHeight: 2,
  timelineTimeColumn: 46,
  /** The spine between the clock column and the entry — node plus its rule. */
  timelineAxisColumn: 16,
  timelineNodeSize: 8,
  /** Home moments share the paper palette without inheriting Review's clock column. */
  momentAxisGap: 8,
  /** Compact one-line rows: the inter-item gap doubles as the visible rail length. */
  momentItemGap: 12,
  momentIconSize: 19,
  momentPreviewIconSize: 17,
  /** Fixed slot for the category glyph so every tag label starts on one line. */
  momentTagIconSlot: 14,
  /**
   * Pre-layout estimate for the anchored action menu: the panel then
   * shrink-wraps its widest row, so this is only used for the first frame's
   * edge math (and as the test-visible width before layout).
   */
  momentMenuWidth: 176,
  /**
   * The band every element of an entry's first line centres on: the clock, the
   * spine node, the title, `+时长` and the trailing delete control. Sharing one
   * height is what keeps them collinear — without it the clock and the node
   * anchor to the top of the row while the title centres in it, and they drift
   * apart by a few dp.
   *
   * The trailing controls are sized to this band too, so they cannot grow the
   * line past the alignment it exists to hold. `YanjiIconButton` expands its
   * touch area to the 44dp `YanjiTouch.min` floor — 8dp per side at this band —
   * without widening the visible glyph.
   *
   * That slop deliberately reaches past the band, which the public `hitSlop`
   * docs read as impossible ("the touch area never extends past the parent view
   * bounds"). Fabric is what makes it work: `YogaLayoutableShadowNode::
   * getContentBounds` unions each child's frame with its `hitSlop` before the
   * parent's `overflowInset` is computed, and `TouchTargetHelper` tests a point
   * against `hitSlopRect` before the view's own bounds — so a touch above or
   * below the band still routes to the control. The effective floor is
   * `timelineHeadBand + 2 * hitSlop`; shrinking the slop to stay inside the band
   * is what would actually drop these controls below `YanjiTouch.min`.
   */
  timelineHeadBand: 28,
  /**
   * How far the timeline's clock column and trailing delete control may sit
   * from the page gutter. Negative pulls the clock toward the screen edge and
   * the delete glyph past the gutter; the timeline reads as a full-bleed spine
   * rather than a column of text indented inside it.
   */
  timelineEdgeBleed: 12,
  /** A dated cell in the review filmstrip — day number above its subject dot. */
  filmstripCellMinHeight: 58,
  gridGap: 8,
} as const;

export const YanjiPaperRadius = {
  tick: 1,
  tag: 5,
  badge: 6,
  /** Subject-matrix cells — tighter than a card so the grid reads as one block. */
  compactCard: 12,
  surface: 14,
  sheet: 26,
  pill: 9999,
} as const;

export const YanjiPaperMotion = {
  pressMs: 120,
  sheetEnterMs: 280,
  sheetExitMs: 210,
  selectionMs: 160,
  opacityReducedMs: 120,
} as const;

/**
 * Resolves a translucent background wash from a hex or rgba color string.
 */
export function toSubtleColor(hexOrColor: string, alpha = 0.14): string {
  if (hexOrColor.startsWith('#') && hexOrColor.length === 7) {
    const r = parseInt(hexOrColor.slice(1, 3), 16);
    const g = parseInt(hexOrColor.slice(3, 5), 16);
    const b = parseInt(hexOrColor.slice(5, 7), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }
  return hexOrColor;
}
