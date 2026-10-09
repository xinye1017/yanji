/**
 * Yanji icon registry — the single place the app references lucide-react-native.
 *
 * Why a registry instead of importing icons at call sites:
 * - "不要用 Emoji" is a hard requirement, so every glyph in the product is a
 *   vector icon. Funnelling them through one map keeps the visual language
 *   coherent (one stroke weight, one size scale) and makes an audit for stray
 *   Emoji a single-file check.
 * - If the icon set is ever swapped, only this file changes.
 *
 * The map holds exactly the names the product uses — no speculative entries.
 * Add a name here only when a call site needs it.
 *
 * Stroke weight is pinned here rather than left to lucide's default (2): at the
 * small sizes this app uses, 2 reads heavy and muddies the "paper" lightness.
 */

import {
  CalendarDays,
  ChartColumn,
  ChartNoAxesCombined,
  Check,
  ChevronLeft,
  ChevronRight,
  ListChecks,
  Minus,
  Pause,
  PenLine,
  Play,
  Plus,
  RotateCcw,
  Settings,
  Sprout,
  Sun,
  Target,
  Timer,
  Trash,
  X,
} from 'lucide-react-native';
import type { LucideIcon } from 'lucide-react-native';

/** Semantic icon names. Add here first, then map below. */
export type YanjiIconName =
  | 'today'
  | 'focus'
  | 'review'
  | 'sprout'
  | 'settings'
  | 'check'
  | 'close'
  | 'compose'
  | 'target'
  | 'add'
  | 'remove'
  | 'back'
  | 'forward'
  | 'play'
  | 'pause'
  | 'delete'
  | 'calendar'
  | 'tasks'
  | 'chartMinimal'
  | 'reset';

const ICONS: Record<YanjiIconName, LucideIcon> = {
  today: Sun,
  focus: Timer,
  review: ChartColumn,
  sprout: Sprout,
  settings: Settings,
  check: Check,
  close: X,
  compose: PenLine,
  target: Target,
  add: Plus,
  remove: Minus,
  back: ChevronLeft,
  forward: ChevronRight,
  play: Play,
  pause: Pause,
  delete: Trash,
  calendar: CalendarDays,
  tasks: ListChecks,
  chartMinimal: ChartNoAxesCombined,
  reset: RotateCcw,
};

/** Stroke width shared by every icon so the set reads as one family. */
export const YANJI_ICON_STROKE = 1.75;

export function getYanjiIcon(name: YanjiIconName): LucideIcon {
  return ICONS[name];
}
