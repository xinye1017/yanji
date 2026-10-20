/**
 * 「记录此刻」composer — the inline capture bar on Today.
 *
 * It is meant to feel like leaving a comment, not filling in a form:
 *
 *  - Collapsed it is one line inside the journal section: the kind glyph, the
 *    invitation (or the unsaved draft) and a quiet 「＋」.
 *  - Tapping it focuses the field in place and raises the keyboard.
 *  - The field's width never depends on how the actions are arranged, so a
 *    wrapping line can never toggle the layout back and forth. Short text keeps
 *    the ✓ / × pair on the same line; once the text wraps, the pair settles to
 *    the bottom right while the field grows downward. Past `composerMaxHeight`
 *    the field scrolls internally instead of pushing the page.
 *  - Save (`✓`) and cancel (`×`) are the only actions. Cancel never touches
 *    saved notes; a failed save keeps the text so nothing is lost, and hiding
 *    the keyboard never drops it either.
 *
 * The kind glyph doubles as the category menu: 疑点 (DOUBT) / 灵感 (INSPIRATION),
 * the two categories the stored note model actually has. Picking one writes it
 * verbatim; leaving it unset keeps the decision model's automatic classification
 * (and, with no model configured, fails closed to 灵感 — the existing behaviour).
 *
 * All colour, type and geometry come from the theme. The component owns the
 * draft and the save request; Today owns the data refresh, which arrives through
 * the existing Room invalidation event.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Animated,
  BackHandler,
  Easing,
  Keyboard,
  Pressable,
  Text,
  TextInput,
  Vibration,
  View,
  useWindowDimensions,
} from 'react-native';
import type {
  ScrollViewInstance,
  TextInputContentSizeChangeEvent,
  TextInputInstance,
  ViewInstance,
} from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { YanjiDataNative, YanjiTimerNative } from '../bridge';
import type { NoteCategory } from '../bridge';
import { classifyMoment } from '../services/decisionClient';
import { YanjiIcon } from './YanjiUI';
import type { YanjiIconName } from './icons';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing, YanjiTouch } from '../theme/tokens';

interface RecordComposerProps {
  /** ISO date `yyyy-MM-dd` the note belongs to — Today's visible day. */
  date: string;
  /** The page's scroll view, used to lift the bar above the IME. */
  scrollRef?: React.RefObject<ScrollViewInstance | null>;
  /** Live content offset of that scroll view, owned by the screen. */
  scrollOffsetRef?: React.RefObject<number>;
}

/** One row of the kind menu; `null` means "let the decision model decide". */
interface KindOption {
  value: NoteCategory | null;
  label: string;
  icon: YanjiIconName;
  hint: string;
}

const KIND_OPTIONS: readonly KindOption[] = [
  { value: 'INSPIRATION', label: '灵感', icon: 'inspiration', hint: '记下想通的事或值得留住的念头' },
  { value: 'DOUBT', label: '疑点', icon: 'doubt', hint: '记下还没弄懂的地方或做错的题' },
];

/** The unset kind reads as a plain note; it is also the decision-model path. */
const PLAIN_KIND_ICON: YanjiIconName = 'comment';

/** Saved successfully. Short enough to read as a tick, not a buzz. */
const CONFIRM_VIBRATION_MS = 10;

/** Gap kept between the bar and the top edge of the keyboard. */
const KEYBOARD_GAP = YanjiSpacing.md;

function pulse(): void {
  try {
    Vibration.vibrate(CONFIRM_VIBRATION_MS);
  } catch {
    // A device without a vibrator is not an error worth surfacing.
  }
}

export function RecordComposer({
  date,
  scrollRef,
  scrollOffsetRef,
}: RecordComposerProps): React.JSX.Element {
  const theme = useYanjiTheme();
  const { paper } = theme;
  const { colors, typography, layout, radius } = paper;
  const reduceMotion = useReducedMotion();
  const { fontScale } = useWindowDimensions();

  const cardRef = useRef<ViewInstance>(null);
  const inputRef = useRef<TextInputInstance>(null);
  // The draft lives in a ref as well as state: the field can unmount (collapse,
  // tab switch) without taking the typed text with it.
  const draftRef = useRef('');
  const [text, setText] = useState('');
  const [editing, setEditing] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [kind, setKind] = useState<NoteCategory | null>(null);
  const [showKinds, setShowKinds] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSave = text.trim().length > 0 && !saving;

  // Entering the editor fades its content in; both states share the same
  // one-line height, so nothing jumps. Opacity only — no layout animation, so
  // typing can never be animated behind the caret.
  const reveal = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    reveal.stopAnimation();
    if (!editing) {
      reveal.setValue(0);
      return;
    }
    if (reduceMotion) {
      reveal.setValue(1);
      return;
    }
    Animated.timing(reveal, {
      toValue: 1,
      duration: paper.motion.selectionMs,
      easing: Easing.out(Easing.quad),
      useNativeDriver: true,
    }).start();
  }, [editing, reduceMotion, reveal, paper.motion.selectionMs]);

  // The platform scales the field's font and line height by the system font
  // scale (allowFontScaling defaults on), so the style below must stay in
  // unscaled token units. The thresholds and the height cap *do* need that
  // scale, because they are compared against the measured, already-scaled
  // content height.
  const lineHeight = typography.bodyWriting.lineHeight * fontScale;
  const fieldPadding = YanjiSpacing.xs;
  const singleLineHeight = lineHeight + fieldPadding * 2;
  const maxFieldHeight = Math.round(layout.composerMaxHeight * fontScale);

  /**
   * Scroll the page just enough to clear the keyboard. The page is edge-to-edge,
   * so the window is not resized for the IME and the bar has to move itself.
   */
  const keepVisible = useCallback(() => {
    const metrics = Keyboard.metrics();
    const scroller = scrollRef?.current;
    const card = cardRef.current;
    if (!metrics || !scroller || !card) return;
    card.measureInWindow((_x, y, _width, height) => {
      const overflow = y + height + KEYBOARD_GAP - metrics.screenY;
      if (overflow <= 0) return;
      const offset = scrollOffsetRef?.current ?? 0;
      scroller.scrollTo({ y: offset + overflow, animated: !reduceMotion });
    });
  }, [scrollRef, scrollOffsetRef, reduceMotion]);

  useEffect(() => {
    if (!editing) return;
    const show = Keyboard.addListener('keyboardDidShow', keepVisible);
    return () => show.remove();
  }, [editing, keepVisible]);

  // Android back closes the editor before it is allowed to leave the app. The
  // IME consumes the first press while it is open, so this only fires once the
  // keyboard is already down — and it keeps the draft, like collapsing by hand.
  useEffect(() => {
    if (!editing) return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      setEditing(false);
      setExpanded(false);
      setShowKinds(false);
      inputRef.current?.blur();
      return true;
    });
    return () => subscription.remove();
  }, [editing]);

  const updateText = useCallback((next: string) => {
    draftRef.current = next;
    setText(next);
  }, []);

  const begin = useCallback(() => {
    if (saving) return;
    setError(null);
    setEditing(true);
  }, [saving]);

  const collapse = useCallback(() => {
    setEditing(false);
    setExpanded(false);
    setShowKinds(false);
    inputRef.current?.blur();
  }, []);

  const cancel = useCallback(() => {
    if (saving) return;
    draftRef.current = '';
    setText('');
    setError(null);
    collapse();
    Keyboard.dismiss();
  }, [saving, collapse]);

  const save = useCallback(async () => {
    const content = text.trim();
    if (saving) return;
    if (content.length === 0) {
      setError('写点什么再保存吧');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      // Bind to the active session when one exists; the timer keeps running.
      let sessionId: string | null = null;
      try {
        const session = await YanjiTimerNative.getActiveSession();
        sessionId = session?.sessionId ?? null;
      } catch {
        sessionId = null;
      }
      // Only an explicit choice bypasses the decision model.
      let category: NoteCategory = kind ?? 'INSPIRATION';
      if (kind === null) {
        try {
          category = await classifyMoment(content);
        } catch {
          category = 'INSPIRATION';
        }
      }
      await YanjiDataNative.saveQuickNote(content, date, sessionId, category);

      draftRef.current = '';
      setText('');
      setExpanded(false);
      setShowKinds(false);
      setKind(null);
      setEditing(false);
      Keyboard.dismiss();
      pulse();
    } catch {
      // Preserve the user's text so nothing is lost on failure.
      setError('保存失败，内容已保留');
    } finally {
      setSaving(false);
    }
  }, [saving, text, kind, date]);

  const onContentSizeChange = useCallback(
    (event: TextInputContentSizeChangeEvent) => {
      const height = event.nativeEvent.contentSize.height;
      // Hysteresis: a wrapped line expands only once it clearly exceeds one
      // line, and collapses only once it clearly falls back under it, so text
      // resting on the boundary cannot flip the layout on every keystroke.
      const expandAt = singleLineHeight + lineHeight * 0.35;
      const collapseBelow = singleLineHeight + lineHeight * 0.15;
      setExpanded(current => (current ? height > collapseBelow : height > expandAt));
    },
    [lineHeight, singleLineHeight],
  );

  useEffect(() => {
    if (expanded) keepVisible();
  }, [expanded, keepVisible]);

  const kindIcon = kind === 'INSPIRATION' ? 'inspiration' : kind === 'DOUBT' ? 'doubt' : PLAIN_KIND_ICON;
  const kindColor = kind === 'INSPIRATION' ? colors.positive : kind === 'DOUBT' ? colors.brandInk : colors.secondary;
  const kindLabel = kind === 'INSPIRATION' ? '灵感' : kind === 'DOUBT' ? '疑点' : '自动判断';

  return (
    <View
      ref={cardRef}
      style={{
        marginTop: YanjiSpacing.xs,
        backgroundColor: editing ? colors.surface : colors.elevated,
        borderRadius: radius.surface,
        borderWidth: layout.hairlineWidth,
        borderColor: editing ? colors.tickMinor : colors.hairline,
        paddingLeft: YanjiSpacing.sm,
        paddingRight: YanjiSpacing.xs,
        paddingVertical: YanjiSpacing.xs,
      }}
    >
      <View style={{ flexDirection: 'row', alignItems: editing ? 'flex-start' : 'center' }}>
        <Pressable
          onPress={() => setShowKinds(open => !open)}
          disabled={saving}
          accessibilityRole="button"
          accessibilityLabel={`记录类型：${kindLabel}`}
          accessibilityState={{ expanded: showKinds }}
          accessibilityHint="选择这条记录是灵感还是疑点，不选则由决策模型判断"
          hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
          style={({ pressed }) => ({
            width: YanjiTouch.min,
            height: YanjiTouch.min,
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: radius.pill,
            opacity: pressed ? 0.6 : 1,
          })}
        >
          <YanjiIcon name={kindIcon} size={layout.momentIconSize} color={kindColor} />
        </Pressable>

        {editing ? (
          <Animated.View style={{ flex: 1, flexDirection: 'row', alignItems: 'flex-start', opacity: reveal }}>
            <TextInput
              ref={inputRef}
              value={text}
              onChangeText={updateText}
              placeholder="记录此刻的想法…"
              placeholderTextColor={colors.secondary}
              autoFocus
              multiline
              textAlignVertical="top"
              accessibilityLabel="记录内容"
              onContentSizeChange={onContentSizeChange}
              // The keyboard going away is not a cancel: the text stays.
              blurOnSubmit={false}
              style={{
                flex: 1,
                minWidth: 0,
                minHeight: YanjiTouch.min,
                maxHeight: maxFieldHeight,
                paddingTop: fieldPadding,
                paddingBottom: fieldPadding,
                paddingHorizontal: fieldPadding,
                color: colors.ink,
                fontSize: typography.bodyWriting.fontSize,
                lineHeight: typography.bodyWriting.lineHeight,
                fontFamily: typography.bodyWriting.fontFamily,
              }}
            />
            {/* The action column is reserved in BOTH states, so the field's
                width — and therefore its wrapping — never depends on whether
                the buttons sit inline or at the bottom. */}
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                alignSelf: expanded ? 'flex-end' : 'center',
              }}
            >
              <ComposerAction
                icon="check"
                label={saving ? '正在保存记录' : '保存记录'}
                disabled={!canSave}
                busy={saving}
                tone="primary"
                onPress={() => void save()}
              />
              <ComposerAction
                icon="close"
                label="取消编辑"
                disabled={saving}
                tone="quiet"
                onPress={cancel}
              />
            </View>
          </Animated.View>
        ) : (
          <Pressable
            onPress={begin}
            accessibilityRole="button"
            accessibilityLabel={text.trim() ? '继续编辑未保存的记录' : '记录此刻的想法'}
            accessibilityHint="展开输入框并弹出键盘"
            style={{ flex: 1, minWidth: 0, minHeight: YanjiTouch.min, justifyContent: 'center', paddingHorizontal: YanjiSpacing.xs }}
          >
            <Text
              numberOfLines={1}
              style={[typography.bodyWriting, { color: text.trim() ? colors.ink : colors.secondary }]}
            >
              {text.trim() || '记录此刻的想法…'}
            </Text>
          </Pressable>
        )}

        {!editing ? (
          <Pressable
            onPress={begin}
            accessibilityRole="button"
            accessibilityLabel="开始记录"
            hitSlop={{ top: 4, bottom: 4, left: 4, right: 4 }}
            style={({ pressed }) => ({
              width: YanjiTouch.min,
              height: YanjiTouch.min,
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: radius.pill,
              opacity: pressed ? 0.6 : 1,
            })}
          >
            <YanjiIcon name="add" size={layout.momentIconSize} color={colors.secondary} />
          </Pressable>
        ) : null}
      </View>

      {showKinds ? (
        <View
          style={{
            marginTop: YanjiSpacing.xs,
            borderTopWidth: layout.hairlineWidth,
            borderTopColor: colors.hairline,
            paddingTop: YanjiSpacing.xs,
          }}
        >
          {KIND_OPTIONS.map(option => {
            const selected = option.value === kind;
            return (
              <Pressable
                key={option.value}
                onPress={() => {
                  // Re-tapping the chosen kind returns to automatic judgement.
                  setKind(selected ? null : option.value);
                  setShowKinds(false);
                  if (editing) inputRef.current?.focus();
                }}
                accessibilityRole="radio"
                accessibilityState={{ selected }}
                accessibilityLabel={`${option.label}：${option.hint}`}
                style={({ pressed }) => ({
                  minHeight: layout.rowMinHeight,
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: YanjiSpacing.sm,
                  paddingHorizontal: YanjiSpacing.sm,
                  borderRadius: radius.tag,
                  backgroundColor: pressed ? colors.subtle : 'transparent',
                })}
              >
                <YanjiIcon
                  name={option.icon}
                  size={layout.momentPreviewIconSize}
                  color={selected ? colors.brandInk : colors.secondary}
                />
                <Text
                  style={[
                    typography.bodyPrimary,
                    { flex: 1, color: selected ? colors.ink : colors.secondary, fontWeight: selected ? '700' : '500' },
                  ]}
                >
                  {option.label}
                </Text>
                {selected ? <YanjiIcon name="check" size={layout.momentTagIconSlot} color={colors.brandInk} /> : null}
              </Pressable>
            );
          })}
        </View>
      ) : null}

      {error ? (
        <Text
          accessibilityRole="alert"
          style={[typography.captionMeta, { color: colors.danger, marginTop: YanjiSpacing.xs, paddingLeft: YanjiSpacing.sm }]}
        >
          {error}
        </Text>
      ) : null}
    </View>
  );
}

/**
 * One glyph control in the composer. The disc stays inside the one-line band
 * while the touch target reaches the 44dp floor.
 */
function ComposerAction({
  icon,
  label,
  onPress,
  disabled,
  busy = false,
  tone,
}: {
  icon: YanjiIconName;
  label: string;
  onPress: () => void;
  disabled: boolean;
  busy?: boolean;
  tone: 'primary' | 'quiet';
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, radius } = paper;
  const primary = tone === 'primary';
  const color = primary && !disabled ? colors.brandInk : disabled ? colors.muted : colors.secondary;
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled, busy }}
      style={({ pressed }) => ({
        width: YanjiTouch.min,
        height: YanjiTouch.min,
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: radius.pill,
        backgroundColor: primary && !disabled ? colors.brandSubtle : 'transparent',
        opacity: pressed ? 0.6 : 1,
      })}
    >
      <YanjiIcon name={icon} size={primary ? 19 : 17} color={color} />
    </Pressable>
  );
}
