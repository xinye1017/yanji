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
// One accent per page. 靛青 indigo is the only chromatic UI accent: controls,
// selection states and every data series draw from this one hue. Subjects stay
// identifiable through their labels and through the single-hue `accentRamp`
// below, never through a second colour. The orange / terracotta and bright
// corporate-blue families are deliberately absent. `positive`, `danger` and
// `amber` are semantic feedback only — never decorative page accents.
//
// Every neutral is a warm grey (a trace of red over blue), so canvas and
// charcoal read as paper rather than as a blue-grey dashboard.
//
// Contrast policy (measured, WCAG 2.1 relative luminance):
//   `ink`      > 11:1 on every canvas/surface/subtle/elevated step of its mode.
//   `secondary` >= 4.5:1 on every step — this is the readable body/meta tier.
//   `muted`     < 4.5:1 by design: decorative rules, tick marks, disabled
//               controls and struck-through archive rows only. It must never
//               carry a timestamp, an axis label or an unread task title.
//   `brand`     the fill/mark tier: >= 3:1 on every step, >= 4.5:1 against
//               `onBrand` for solid fills.
//   `brandInk`  the readable brand text tier: >= 4.5:1 on every step and on
//               its own tint.
//   `accentRamp` five solid steps of the same indigo, each >= 3:1 on its
//               canvas; adjacent steps stay visually distinct so a stacked
//               chart can be read without a second hue.
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
    canvas: '#F7F3EC',
    elevated: '#FBF7F1',
    surface: '#FFFCF7',
    subtle: '#EFE8DD',
    /** Full-bleed wash behind the 寸阴尺 strip — a hair of ink, not a surface. */
    vernierWash: 'rgba(42,36,29,0.03)',
    ink: '#2A241D',
    secondary: '#5E564B',
    muted: '#9B9184',
    hairline: 'rgba(42,36,29,0.09)',
    /** Vernier ticks. Decorative by definition, so they sit below the 4.5:1 text bar. */
    tickMinor: 'rgba(42,36,29,0.22)',
    tickMajor: 'rgba(42,36,29,0.48)',
    /** 靛青 — the page accent. A deep, slightly muted indigo, not a corporate blue. */
    brand: '#33587C',
    /** Readable brand text on parchment. Measured 8.29:1 on canvas, 8.96:1 on surface. */
    brandInk: '#2C4A69',
    /**
     * Selection tints. The alpha is measured, not aesthetic: it is the highest
     * value that still keeps the capsule's own label (`brandInk` / `amber` at
     * `captionMeta`, 12sp) above 4.5:1 when composited over every surface the
     * capsule can land on — canvas, elevated, surface and subtle. Raising it
     * fails dark-mode badges and selected chips, so it stays here.
     */
    brandSubtle: 'rgba(51,88,124,0.07)',
    /** Single-hue data series. Measured 8.33 / 5.88 / 4.41 / 3.35 / 3.08:1 on canvas. */
    accentRamp: ['#2C4A66', '#3C6183', '#4E7596', '#6289A8', '#6C8FAB'],
    onBrand: '#FFFCF7',
    /** Paused / waiting accent: 茶金 brass, not orange. Measured >= 5.53:1 across the paper steps. */
    amber: '#6E5926',
    amberSubtle: 'rgba(110,89,38,0.07)',
    /** Modal / sheet scrim, derived from `ink` so the veil shares the paper's warmth. */
    scrim: 'rgba(42,36,29,0.32)',
    /** Destructive actions. Measured 6.31:1 on `surface`, 5.83:1 on `canvas`, and 5.66:1 on its own tint. */
    danger: '#A9362E',
    /** 7% tint matching `brandSubtle` / `amberSubtle` / `positiveSubtle` while keeping `danger` >= 4.5:1 on `subtle`. */
    dangerSubtle: 'rgba(169,54,46,0.07)',
    /**
     * A period that improved on the one before it — 竹青, kept as feedback only.
     * Measured worst case 5.17:1 across the four paper steps, and 5.58:1 on its
     * own tint below.
     */
    positive: '#2E6B50',
    /** See the tint note above: alpha is capped by the badge label's 4.5:1. */
    positiveSubtle: 'rgba(46,107,80,0.07)',
  },
  dark: {
    /**
     * Warm charcoal, not a near-black. The old blue-black sat at 0.6% luminance,
     * which read as a hole punched in the screen: every surface above it had to
     * climb hard to separate, and the warm paper identity of the light theme
     * disappeared entirely. This stack keeps the same hue family as the
     * parchment (a trace of red over blue) and starts at ~1.0% instead, so the
     * page reads as dark paper rather than as an off screen. Each step is a
     * visible move, not a rounding error.
     */
    canvas: '#1A1612',
    elevated: '#211D18',
    surface: '#27221C',
    subtle: '#322B24',
    vernierWash: 'rgba(243,236,224,0.03)',
    ink: '#F3ECE0',
    secondary: '#B4A99A',
    muted: '#7D7265',
    hairline: 'rgba(243,236,224,0.10)',
    tickMinor: 'rgba(243,236,224,0.22)',
    tickMajor: 'rgba(243,236,224,0.50)',
    /** The same 靛青, lifted for a dark field. */
    brand: '#8FB0CE',
    /** Lifted from `brand` so labels stay >= 4.5:1 over the 10% `brandSubtle` wash on `subtle`. */
    brandInk: '#A9C4DC',
    /** See the light-mode tint note: 10% dark wash paired with `brandInk` to stay >= 4.5:1 across all surfaces. */
    brandSubtle: 'rgba(143,176,206,0.10)',
    /** Single-hue data series for the dark field. Measured 11.63 / 9.51 / 7.31 / 5.43 / 4.08:1 on canvas. */
    accentRamp: ['#BBD3E6', '#A3C0D9', '#89A9C4', '#6F91AE', '#587C99'],
    onBrand: '#1A1612',
    amber: '#D6BB82',
    amberSubtle: 'rgba(214,187,130,0.10)',
    /** Heavier veil than light mode, matching the dark surface stack. */
    /** Warm veil rather than pure black, so even the dimmed page stays on the paper family. */
    scrim: 'rgba(20,16,12,0.58)',
    /** Destructive actions. Measured 6.50:1 on `surface`, 7.41:1 on `canvas`. */
    danger: '#EE8B83',
    /** See the light-mode tint note: keep the destructive label above 4.5:1. */
    dangerSubtle: 'rgba(238,139,131,0.10)',
    /** 竹青 lifted for the dark field. Measured worst case 7.04:1 on the four steps. */
    positive: '#7AC8A2',
    positiveSubtle: 'rgba(122,200,162,0.10)',
  },
} as const;

const paperImmersiveBase = {
  /**
   * The immersion field is the darkest surface the app shows, but it still has
   * to read as a room with the lights down rather than as an unlit screen: it
   * sits a step below the paper canvas and keeps the same warm bias.
   */
  canvas: '#12100C',
  ink: '#F7F1E7',
  secondary: '#C9C0B1',
  subtitle: '#9D9385',
  muted: '#7A7164',
  /** Destructive text on the always-dark immersion field; paper `danger` is too dark here. */
  danger: '#F0877E',
  subtle: 'rgba(255,246,232,0.08)',
  pillBg: 'rgba(255,246,232,0.05)',
  pillBorder: 'rgba(255,246,232,0.09)',
  /**
   * Horizon tide — the wash that rises from the bottom edge.
   *
   * Colour and peak opacity are separate roles because `react-native-svg`
   * reads a gradient stop's alpha from `stopOpacity` alone: `extractGradient`
   * masks any alpha embedded in `stopColor` back out, and a missing
   * `stopOpacity` resolves to fully opaque. An `rgba(...)` string in `tideColor`
   * would therefore paint a solid slab instead of a wash.
   */
  tideOpacity: 0.16,
  secondaryActionBg: 'rgba(255,246,232,0.06)',
  secondaryActionBorder: 'rgba(255,246,232,0.10)',
  secondaryActionText: '#DDD3C4',
  primaryActionText: '#F7F1E7',
} as const;

/**
 * The immersion field for a running / paused focus session. The navigation is
 * hidden there, so this palette owns the whole screen — including the area
 * behind the status bar.
 *
 * Both states stay on the same 靛青 accent: pausing does not introduce a second
 * hue, it lowers the accent's energy and the tide's height. The whole screen
 * therefore still reads as one page with one accent.
 */
export const YanjiPaperImmersive = {
  running: {
    ...paperImmersiveBase,
    accent: '#9DBAD4',
    tideColor: '#557A9B',
    primaryActionBg: 'rgba(157,186,212,0.16)',
    primaryActionBorder: 'rgba(157,186,212,0.30)',
  },
  paused: {
    ...paperImmersiveBase,
    accent: '#8798A8',
    tideColor: '#6F8595',
    tideOpacity: 0.09,
    primaryActionBg: 'rgba(135,152,168,0.14)',
    primaryActionBorder: 'rgba(135,152,168,0.26)',
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
  settingsStackedWidth: 360,
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
