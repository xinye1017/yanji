/**
 * Yanji visual language: quiet study journal.
 * Semantic surfaces may be elevated controls or unboxed plans and timelines.
 * Typography and readable rhythm, rather than card outlines, establish hierarchy.
 *
 * The 素笺与寸阴 paper domain (`YanjiPaper*`) is the only colour and type
 * vocabulary: every body surface and every shared component in `YanjiUI.tsx`
 * renders with it, and so does the navigation dock. The remaining `Yanji*`
 * scales are neutral measurements — radius, spacing, layout, touch, shadow,
 * motion — with no colour of their own.
 */

import type { ViewStyle } from 'react-native';

/** Semantic radius scale (Compose: YanjiRadius). Raw dp literals are forbidden at call sites. */
export const YanjiRadius = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 28,
  full: 9999,
} as const;

/** Spacing scale (Compose: Spacing.kt). */
export const YanjiSpacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  /** Horizontal page gutter — every screen's outermost inset. */
  page: 20,
} as const;

/** Shared composition rhythm for the three main screens. */
export const YanjiLayout = {
  sectionGap: 28,
  titleGap: 10,
  heroInset: 22,
  listRowMinHeight: 56,
  timerTopGap: 36,
} as const;

/** Card rule: subtle 1px hairline border (cardBorder) per modern refined minimal design. */
export const YanjiCardRules = {
  borderWidth: 1,
  hasBorderStroke: true,
  plainBorderWidth: 0,
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
  /** A card resting on the desk — subtle diffuse elevation. */
  sheet: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 12,
      offsetY: 2,
      opacity: isDark ? 0.36 : 0.05,
      elevation: 2,
    }),
  /** The tab bar and other sheets lifted clear of the desk. */
  floating: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 20,
      offsetY: 6,
      opacity: isDark ? 0.44 : 0.09,
      elevation: 8,
    }),
  /** Pressed feedback — a sheet pushed toward the desk. */
  pressed: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 4,
      offsetY: 1,
      opacity: isDark ? 0.28 : 0.04,
      elevation: 1,
    }),
} as const;

function PlatformShadow(
  isDark: boolean,
  spec: { radius: number; offsetY: number; opacity: number; elevation: number },
): ViewStyle {
  // Dark mode needs a deeper shadow colour to read at all; pure black is fine
  // as a *shadow* (the §三.6 ban is on background tokens), but a Midnight-Blue
  // tint keeps it in family.
  return {
    shadowColor: isDark ? '#000000' : '#172033',
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
    /** Selection changes that must feel immediate. */
    snappy: { damping: 20, stiffness: 260, mass: 0.9 },
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
    /** Paused / waiting accent. Measured >= 5.4:1 on every surface and on its own tint. */
    amber: '#875400',
    amberSubtle: 'rgba(135,84,0,0.07)',
    /** Modal / sheet scrim, derived from `ink` so the veil shares the paper's warmth. */
    scrim: 'rgba(28,30,33,0.32)',
    /** Destructive actions. Measured 5.69:1 on `surface`, 5.18:1 on `canvas`. */
    danger: '#BE3232',
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
    canvas: '#111315',
    elevated: '#16191D',
    surface: '#1A1D21',
    subtle: '#23272D',
    vernierWash: 'rgba(234,230,223,0.03)',
    ink: '#EAE6DF',
    secondary: '#9BA0A8',
    muted: '#636870',
    hairline: 'rgba(234,230,223,0.08)',
    tickMinor: 'rgba(234,230,223,0.22)',
    tickMajor: 'rgba(234,230,223,0.50)',
    brand: '#E06D53',
    /** Dark `brand` already clears 4.5:1 on canvas, so the ink tier is itself. */
    brandInk: '#E06D53',
    /** See the light-mode note: alpha is capped by the 4.5:1 label contrast. */
    brandSubtle: 'rgba(224,109,83,0.10)',
    onBrand: '#111315',
    amber: '#DDA059',
    amberSubtle: 'rgba(221,160,89,0.10)',
    /** Heavier veil than light mode, matching the dark surface stack. */
    scrim: 'rgba(0,0,0,0.56)',
    /** Destructive actions. Measured 6.11:1 on `surface`, 6.73:1 on `canvas`. */
    danger: '#F87171',
    /**
     * See the light-mode note. Dark needs its own value rather than a reuse of
     * light's: `#27684F` would be unreadable here, and the board's 竹青
     * `#2D7A5E` measures 4.39:1 on `subtle`. Measured worst case 5.65:1 on the
     * four steps, 4.80:1 on its own tint.
     */
    positive: '#4FB08A',
    positiveSubtle: 'rgba(79,176,138,0.10)',
  },
} as const;

/**
 * The immersion field for a running / paused focus session. The navigation is
 * hidden there, so this palette owns the whole screen — including the area
 * behind the status bar.
 */
export const YanjiPaperImmersive = {
  running: {
    canvas: '#15181C',
    ink: '#F6F4EF',
    secondary: '#C4C7CE',
    subtitle: '#8A8F99',
    muted: '#6E737D',
    /** Footage line. Decorative reassurance, so it sits under the 4.5:1 text bar. */
    footnote: '#52565E',
    accent: '#3A9B78',
    subtle: 'rgba(255,255,255,0.08)',
    pillBg: 'rgba(255,255,255,0.05)',
    pillBorder: 'rgba(255,255,255,0.08)',
    /**
     * Horizon tide — the wash that rises from the bottom edge.
     *
     * Colour and peak opacity are separate roles because `react-native-svg`
     * reads a gradient stop's alpha from `stopOpacity` alone: `extractGradient`
     * masks any alpha embedded in `stopColor` back out, and a missing
     * `stopOpacity` resolves to fully opaque. An `rgba(...)` string here would
     * therefore paint a solid slab instead of a wash.
     */
    tideColor: '#2D7A5E',
    tideOpacity: 0.14,
    secondaryActionBg: 'rgba(255,255,255,0.06)',
    secondaryActionBorder: 'rgba(255,255,255,0.10)',
    secondaryActionText: '#D5D8DF',
    primaryActionBg: 'rgba(246,244,239,0.12)',
    primaryActionBorder: 'rgba(246,244,239,0.22)',
    primaryActionText: '#F6F4EF',
  },
  paused: {
    canvas: '#15181C',
    ink: '#F6F4EF',
    secondary: '#C4C7CE',
    subtitle: '#8A8F99',
    muted: '#6E737D',
    footnote: '#52565E',
    accent: '#DDA059',
    subtle: 'rgba(255,255,255,0.08)',
    pillBg: 'rgba(255,255,255,0.05)',
    pillBorder: 'rgba(255,255,255,0.08)',
    /** See the running-state note: alpha must travel via `tideOpacity`. */
    tideColor: '#DDA059',
    tideOpacity: 0.14,
    secondaryActionBg: 'rgba(255,255,255,0.06)',
    secondaryActionBorder: 'rgba(255,255,255,0.10)',
    secondaryActionText: '#D5D8DF',
    primaryActionBg: 'rgba(221,160,89,0.18)',
    primaryActionBorder: 'rgba(221,160,89,0.34)',
    primaryActionText: '#F6F4EF',
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
    fontSize: 96,
    lineHeight: 102,
    /**
     * The running clock is the immersive field's single focal point, so it is
     * set ultra-light — the opposite of `displayDial`, which stays heavy for
     * the ready-state dial.
     *
     * The weight rides on the *family*, not `fontWeight`: RN resolves a named
     * family through `TypefaceStyle.nearestStyle` (ReactFontManager.kt), which
     * collapses every weight below 700 to NORMAL, and `sans-serif-medium` is
     * itself the medium cut. Asking for `fontWeight: '300'` on that family
     * would therefore still render at medium. `sans-serif-light` is the system
     * family that actually carries the 300 cut, so `fontWeight` is left at the
     * matching literal for intent and for Android's bold-text adjustment.
     */
    fontWeight: '300' as const,
    fontFamily: 'sans-serif-light',
    fontVariant: ['tabular-nums'] as const,
  },
  displayDial: {
    fontSize: 84,
    lineHeight: 90,
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
  metricSub: {
    fontSize: 28,
    lineHeight: 36,
    fontWeight: '700' as const,
    fontVariant: ['tabular-nums'] as const,
  },
  metricDaily: {
    fontSize: 48,
    lineHeight: 54,
    fontWeight: '700' as const,
    fontVariant: ['tabular-nums'] as const,
  },
  titleGreeting: {
    fontSize: 24,
    lineHeight: 33,
    fontWeight: '700' as const,
    fontFamily: 'serif',
  },
  titleEditorial: {
    fontSize: 22,
    lineHeight: 31,
    fontWeight: '700' as const,
    fontFamily: 'serif',
  },
  titleSection: {
    fontSize: 14,
    lineHeight: 21,
    /**
     * Section headings sit at the heaviest cut the system face offers, so a
     * group title outranks the 500-weight body copy beneath it by weight as
     * well as by size.
     *
     * 900 is a real cut here, not a synonym for bold: the device ships a
     * variable Han face with a 100–900 `wght` axis, and on API 28+ React
     * Native resolves a family-less weight through `Typeface.create(base,
     * weight, italic)`, which keeps the exact value. (The 700-and-above
     * collapse to BOLD lives in `TypefaceStyle.nearestStyle`, which only runs
     * on the *named-family* path — so this must stay family-less to hold 900.)
     */
    fontWeight: '900' as const,
  },
  bodyPrimary: {
    fontSize: 14,
    lineHeight: 23,
    fontWeight: '500' as const,
  },
  bodyJournal: {
    fontSize: 14,
    lineHeight: 23,
    fontWeight: '400' as const,
    fontFamily: 'serif',
  },
  bodyExcerpt: {
    fontSize: 15,
    lineHeight: 24,
    fontWeight: '600' as const,
    fontFamily: 'serif',
  },
  captionMeta: {
    fontSize: 12,
    lineHeight: 17,
    fontWeight: '500' as const,
    fontVariant: ['tabular-nums'] as const,
  },
} as const;

export const YanjiPaperLayout = {
  pageGutter: 22,
  sectionGap: 24,
  rowMinHeight: 48,
  /** Visible compact segment; the radio keeps a full row-height touch target. */
  compactSegmentHeight: 28,
  hairlineWidth: 1,
  vernierTickWidth: 14,
  vernierMinHeight: 96,
  vernierMinorTickHeight: 10,
  vernierMajorTickHeight: 20,
  vernierPointerHeight: 28,
  spectrumHeight: 10,
  spectrumGap: 2,
  subjectSwatchSize: 7,
  taskCheckboxSize: 18,
  taskCheckboxStroke: 1.5,
  taskCheckIconSize: 12,
  taskTagPadding: 2,
  /** The tag's horizontal inset — wider than the vertical so it reads as a chip. */
  taskTagPaddingHorizontal: 6,
  /** AB-01's ledger rhythm: 11dp above and below, 11dp between the columns. */
  taskRowPadding: 11,
  /** Row columns sit 11dp apart; the checkbox carries its 48dp target as hit slop. */
  taskRowGap: 11,
  /** The sub-line hugs its title — 2dp reads as one line, not two blocks. */
  taskSubMarginTop: 2,
  /**
   * Subject tag wash. Identity is carried by the tint and the swatch, never by
   * the label: at 12% every colour in the subject palette keeps the tag's own
   * `secondary` label above the 4.5:1 readable tier on all four paper steps
   * (measured worst case 4.88:1). Painting the label in the subject colour
   * instead fails that bar in 46 of 48 colour/surface combinations.
   */
  subjectTagTintOpacity: 0.12,
  compactActionHeight: 28,
  /** Inline capsule inset — AB-01's `5px 10px` play trigger. */
  compactActionPadding: 10,
  rhythmBarWidth: 4,
  rhythmBarGap: 3,
  rhythmBarHeight: 22,
  rhythmEmptyHeight: 2,
  timelineTimeColumn: 46,
  /** The spine between the clock column and the entry — node plus its rule. */
  timelineAxisColumn: 16,
  timelineNodeSize: 8,
  /**
   * How far the timeline's clock column and trailing delete control may sit
   * from the page gutter. Negative pulls the clock toward the screen edge and
   * the delete glyph past the gutter; the timeline reads as a full-bleed spine
   * rather than a column of text indented inside it.
   */
  timelineEdgeBleed: 8,
  /** A dated cell in the review filmstrip — day number above its subject dot. */
  filmstripCellMinHeight: 58,
  sheetTopRadius: 26,
  gridGap: 8,
} as const;

export const YanjiPaperRadius = {
  tick: 1,
  tag: 5,
  badge: 6,
  /** Subject-matrix cells — tighter than a card so the grid reads as one block. */
  compactCard: 12,
  surface: 14,
  /** The one full-width page action (开始专注). */
  cta: 18,
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
