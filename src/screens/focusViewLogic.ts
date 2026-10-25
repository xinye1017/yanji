/**
 * Yanji "Focus" pure view logic — the immersion field's geometry, computed
 * rather than rendered.
 *
 * The countdown ring is the one piece of this page that has to survive a window
 * it was not designed in. Android freeform windows, split screen and landscape
 * all hand the screen far less height than a phone in portrait, while the ring
 * sits between a fixed header and a fixed action block. Sizing it from the
 * window *width* alone therefore kept its portrait diameter and painted it over
 * both neighbours, so the rule lives here where it can be asserted without
 * React.
 *
 * No React and no React Native imports live here on purpose.
 */

/**
 * The ring never grows past this, however much room a tablet-sized window has.
 * It is a clock face, not a diagram: past this size the empty middle starts to
 * dominate the numbers it is holding.
 */
const RING_MAX_SIZE = 320;

/** Portrait intent: the ring takes about three quarters of the window width. */
const RING_PREFERRED_RATIO = 0.76;

/**
 * Separation between the ring and whatever sits above or below it. The stroke
 * is drawn *inside* the diameter, so this is breathing room, not paint padding.
 *
 * It is a maximum, not a fixed cost: on a window so short that a full gap would
 * leave no ring at all, the gap shrinks with the slot. Reserving a fixed 16dp
 * per side collapsed the ring to zero at a 28dp slot — the clock vanished
 * instead of getting small, which is a worse failure than a tight fit.
 */
const RING_GAP = 16;
const RING_GAP_RATIO = 0.1;

/** The clock's size relative to its ring, and the bounds that keep it sane. */
const CLOCK_RATIO = 0.24;
const CLOCK_MAX_SIZE = 78;
/** Below this the digits stop reading as a clock, so it is a floor, not a target. */
const CLOCK_MIN_SIZE = 12;
/** `displayTimer` is 76/82; a line box a hair taller than the face keeps descenders clear. */
const CLOCK_LINE_RATIO = 1.08;

/**
 * The ring's size in a window with room to spare — the portrait design.
 *
 * Exposed so a caller can paint something sensible before the first layout
 * pass, and so tests can state "the designed size" without restating the ratio.
 */
export function preferredRingSize(windowWidth: number): number {
  return Math.min(RING_MAX_SIZE, Math.round(windowWidth * RING_PREFERRED_RATIO));
}

/**
 * The ring's diameter, bounded by the box it actually has to live in.
 *
 * `freeWidth` / `freeHeight` are the measured slot between the header and the
 * action block, or `null` before the first layout pass. The ring is the smaller
 * of its designed size and what that slot can hold, so a short window shrinks
 * the ring instead of overlapping its neighbours.
 *
 * `null` and a measured `0` are deliberately different: an unmeasured slot must
 * paint the designed ring, while a slot measured as empty genuinely has no room
 * and must not paint anything. Treating both as "unknown" would put the full
 * portrait ring back on a window that just told us it has nothing to give.
 */
export function focusRingSize(input: {
  windowWidth: number;
  freeWidth: number | null;
  freeHeight: number | null;
}): number {
  if (input.freeWidth === null || input.freeHeight === null) {
    return preferredRingSize(input.windowWidth);
  }
  const slot = Math.min(input.freeWidth, input.freeHeight);
  const gap = Math.min(RING_GAP, Math.floor(slot * RING_GAP_RATIO));
  const fits = Math.floor(slot - gap * 2);
  return Math.max(0, Math.min(preferredRingSize(input.windowWidth), fits));
}

/**
 * The clock's type, set from the ring it sits in, so a shrunk ring takes its
 * numbers with it. `lineHeight` scales with `fontSize` on purpose: the display
 * style carries an 82dp line box for the 76dp portrait face, and leaving it
 * behind on a small ring made the text taller than the ring it was inside.
 * `adjustsFontSizeToFit` remains the last resort for the glyphs themselves.
 */
export function focusClockType(ringSize: number): { fontSize: number; lineHeight: number } {
  const fontSize = Math.min(CLOCK_MAX_SIZE, Math.max(CLOCK_MIN_SIZE, Math.round(ringSize * CLOCK_RATIO)));
  return { fontSize, lineHeight: Math.round(fontSize * CLOCK_LINE_RATIO) };
}
