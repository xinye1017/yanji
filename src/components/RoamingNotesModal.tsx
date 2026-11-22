import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Animated,
  Image,
  Modal,
  PanResponder,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import Svg, { Defs, LinearGradient, RadialGradient, Rect, Stop } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { NoteCategory, NoteEntry, YanjiDataNative } from '../bridge';
import { YanjiIcon, YanjiSkeleton, YanjiSkeletonBone, YanjiSkeletonText } from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing, YanjiTouch } from '../theme/tokens';

const WHITE_GLYPH = '#FFFFFF'; // token-exempt: white contrast glyph on the wax-seal illustration
/** Deep carmine wax seal — deliberately outside the paper palette, it is illustration not UI surface. */
const WAX_SEAL_BG = '#8E2F35'; // token-exempt: wax-seal illustration
const WAX_SEAL_SHADOW = '#6E2127'; // token-exempt: wax-seal illustration shadow

/** Card geometry. Named so the deck insets and the responsive width stay in sync. */
const CARD_MAX_WIDTH = 295;
const EMPTY_CARD_MAX_WIDTH = 280;
const PHOTO_HEIGHT = 140;
/** Off-screen travel for the swipe-to-turn animation. */
const SWIPE_TRAVEL = 300;
/** The roaming card's note type (`styles.noteText`), so bone lines match real ones. */
const NOTE_LINE = { fontSize: 14, lineHeight: 22 } as const;
/** Wireframe line widths for the card while the batch is drawn. */
const ROAMING_LINE_BONES = ['92%', '84%', '58%'] as const;

export interface RoamingNotesModalProps {
  visible: boolean;
  onClose: () => void;
  onEditNote?: (note: {
    id: string;
    content: string;
    category: NoteCategory;
    isFavorite?: boolean;
    imageUri?: string | null;
  }) => void;
}

function formatNoteDateTime(epochMs: number): string {
  const d = new Date(epochMs);
  const m = d.getMonth() + 1;
  const day = d.getDate();
  const h = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${m}月${day}日 ${h}:${min}`;
}

export function RoamingNotesModal({
  visible,
  onClose,
  onEditNote,
}: RoamingNotesModalProps): React.JSX.Element | null {
  const theme = useYanjiTheme();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const { colors, typography } = theme.paper;
  // The roaming field is always dark, independent of the app's light/dark theme:
  // it is the one place that owns its whole screen, like the focus immersion.
  const immersed = theme.paper.immersive.running;

  // Start in loading so reopening never flashes the "暂无随笔" empty state
  // before the random batch resolves.
  const [notes, setNotes] = useState<NoteEntry[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [loading, setLoading] = useState(true);

  const panX = useRef(new Animated.Value(0)).current;
  const scaleAnim = useRef(new Animated.Value(1)).current;
  /** Guards against a second turn starting while a card is still in flight. */
  const animatingRef = useRef(false);

  const loadRandomBatch = useCallback(async () => {
    setLoading(true);
    try {
      const batch = await YanjiDataNative.getRandomNotes(30);
      if (batch && batch.length > 0) {
        setNotes(batch);
      } else {
        const all = await YanjiDataNative.getAllNotes();
        setNotes(all || []);
      }
      setCurrentIndex(0);
    } catch {
      // Keep existing state on error.
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (visible) {
      void loadRandomBatch();
    }
    // loadRandomBatch is memoized with empty deps, so `visible` is the only trigger.
  }, [visible]);

  const advance = useCallback(
    (delta: 1 | -1) => {
      if (animatingRef.current || notes.length <= 1) return;
      const step = () => setCurrentIndex(prev => (prev + delta + notes.length) % notes.length);
      if (reduceMotion) {
        step();
        return;
      }
      animatingRef.current = true;
      const toValue = delta > 0 ? -SWIPE_TRAVEL : SWIPE_TRAVEL;
      Animated.timing(panX, {
        toValue,
        duration: 150,
        useNativeDriver: true,
      }).start(() => {
        step();
        panX.setValue(-toValue);
        Animated.spring(panX, {
          toValue: 0,
          friction: 7,
          tension: 40,
          useNativeDriver: true,
        }).start(() => {
          animatingRef.current = false;
        });
      });
    },
    [notes.length, panX, reduceMotion]
  );

  const goToNext = useCallback(() => advance(1), [advance]);
  const goToPrev = useCallback(() => advance(-1), [advance]);

  const drawRandom = useCallback(() => {
    if (notes.length <= 1 || animatingRef.current) return;
    let nextIdx = currentIndex;
    do {
      nextIdx = Math.floor(Math.random() * notes.length);
    } while (nextIdx === currentIndex && notes.length > 1);

    if (reduceMotion) {
      setCurrentIndex(nextIdx);
      return;
    }
    animatingRef.current = true;
    Animated.sequence([
      Animated.timing(scaleAnim, {
        toValue: 0.9,
        duration: 120,
        useNativeDriver: true,
      }),
      Animated.spring(scaleAnim, {
        toValue: 1,
        friction: 5,
        tension: 40,
        useNativeDriver: true,
      }),
    ]).start(() => {
      setCurrentIndex(nextIdx);
      animatingRef.current = false;
    });
  }, [notes.length, currentIndex, scaleAnim, reduceMotion]);

  const panResponder = useMemo(
    () =>
      PanResponder.create({
        onMoveShouldSetPanResponderCapture: (_evt, { dx, dy }) =>
          Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(dy) * 1.2,
        onPanResponderMove: (_evt, { dx }) => {
          panX.setValue(dx);
        },
        onPanResponderRelease: (_evt, { dx, vx }) => {
          if (dx < -60 || vx < -0.5) {
            goToNext();
          } else if (dx > 60 || vx > 0.5) {
            goToPrev();
          } else if (reduceMotion) {
            panX.setValue(0);
          } else {
            Animated.spring(panX, {
              toValue: 0,
              friction: 6,
              tension: 40,
              useNativeDriver: true,
            }).start();
          }
        },
      }),
    [panX, goToNext, goToPrev, reduceMotion]
  );

  if (!visible) return null;

  const currentNote = notes[currentIndex] as NoteEntry | undefined;

  const rotateZ = panX.interpolate({
    inputRange: [-200, 0, 200],
    outputRange: ['-8deg', '0deg', '8deg'],
  });

  return (
    <Modal
      visible={visible}
      transparent
      animationType={reduceMotion ? 'none' : 'fade'}
      onRequestClose={onClose}
      statusBarTranslucent
    >
      <View
        style={[
          styles.scrim,
          {
            backgroundColor: immersed.canvas,
            paddingTop: insets.top + YanjiSpacing.sm,
            paddingBottom: insets.bottom + YanjiSpacing.md,
          },
        ]}
        accessibilityViewIsModal
      >
        {/* Ambient Glow — uses the immersive tide accent so it reads on the always-dark field in every theme. */}
        <Svg width={260} height={260} style={styles.ambientGlow} pointerEvents="none">
          <Defs>
            <RadialGradient id="roamingAmbientGlow" cx="50%" cy="50%" r="50%">
              <Stop offset="0%" stopColor={immersed.accent} stopOpacity="0.14" />
              <Stop offset="100%" stopColor={immersed.accent} stopOpacity="0" />
            </RadialGradient>
          </Defs>
          <Rect x="0" y="0" width="260" height="260" fill="url(#roamingAmbientGlow)" />
        </Svg>

        {/* Header */}
        <View style={styles.headerRow}>
          <View style={styles.headerLeft}>
            <Text style={[typography.titleGroup, styles.headerTitle, { color: immersed.ink }]}>
              漫游随笔
            </Text>
            {notes.length > 0 ? (
              <View
                style={[
                  styles.counterBadge,
                  { backgroundColor: immersed.subtle, borderColor: immersed.pillBorder },
                ]}
              >
                <Text style={[styles.counterText, { color: immersed.ink }]}>
                  {`第 ${currentIndex + 1} / ${notes.length} 篇`}
                </Text>
              </View>
            ) : null}
          </View>

          <Pressable
            onPress={onClose}
            accessibilityRole="button"
            accessibilityLabel="关闭漫游随笔"
            style={({ pressed }) => [
              styles.closeButton,
              {
                backgroundColor: immersed.pillBg,
                borderColor: immersed.pillBorder,
                opacity: pressed ? 0.75 : 1,
                transform: [{ scale: pressed ? 0.94 : 1 }],
              },
            ]}
          >
            <YanjiIcon name="close" size={16} color={immersed.ink} />
          </Pressable>
        </View>

        {/* 3D Spatial Stage */}
        <View style={styles.stage}>
          {loading ? (
            // The card itself, as a wireframe: same width, radius and body
            // padding, three lines of note and the date / seal footer.
            <YanjiSkeleton testID="roaming-skeleton" accessibilityLabel="正在拾取随笔"
              style={styles.cardsStack}>
              <View style={[
                styles.activeCard,
                {
                  backgroundColor: colors.surface,
                  borderColor: colors.hairline,
                  borderRadius: theme.radius.xl,
                },
              ]}>
                <View style={[styles.cardBody, { paddingTop: YanjiSpacing.xl }]}>
                  <View style={{ marginBottom: YanjiSpacing.md }}>
                    {ROAMING_LINE_BONES.map(width => (
                      <YanjiSkeletonText key={width} type={NOTE_LINE} width={width} />
                    ))}
                  </View>
                  <View style={[styles.cardFooter, { borderTopColor: colors.hairline }]}>
                    <View style={styles.metaColumn}>
                      <YanjiSkeletonText type={{ fontSize: 11, lineHeight: 16 }} width={92} />
                      <View style={{ marginTop: 2 }}>
                        <YanjiSkeletonText type={typography.captionMeta} width={64} />
                      </View>
                    </View>
                    <YanjiSkeletonBone width={36} height={36} round />
                  </View>
                </View>
              </View>
            </YanjiSkeleton>
          ) : notes.length === 0 ? (
            <View
              style={[
                styles.emptyCard,
                {
                  backgroundColor: colors.surface,
                  borderColor: colors.hairline,
                  borderRadius: theme.radius.xl,
                },
              ]}
            >
              <View style={[styles.emptyIconWrapper, { backgroundColor: immersed.subtle }]}>
                <YanjiIcon name="sprout" size={28} color={colors.secondary} />
              </View>
              <Text style={[typography.titleGroup, { color: colors.ink, marginTop: YanjiSpacing.md }]}>
                暂无随笔
              </Text>
              <Text
                style={[
                  typography.bodyJournal,
                  { color: colors.secondary, textAlign: 'center', marginTop: YanjiSpacing.sm, lineHeight: 20 },
                ]}
              >
                在时间线点击「记录此刻」写下随笔。
              </Text>
            </View>
          ) : currentNote ? (
            <View style={styles.cardsStack}>
              {/* Deck Depth 2 (Back) */}
              <View
                style={[
                  styles.depthDeck2,
                  {
                    backgroundColor: colors.surface,
                    borderColor: colors.hairline,
                    borderRadius: theme.radius.xl,
                  },
                ]}
                pointerEvents="none"
                importantForAccessibility="no-hide-descendants"
              />

              {/* Deck Depth 1 (Middle) */}
              <View
                style={[
                  styles.depthDeck1,
                  {
                    backgroundColor: colors.surface,
                    borderColor: colors.hairline,
                    borderRadius: theme.radius.xl,
                  },
                ]}
                pointerEvents="none"
                importantForAccessibility="no-hide-descendants"
              />

              {/* Active 3D Floating Card */}
              <Animated.View
                {...panResponder.panHandlers}
                style={[
                  styles.activeCard,
                  {
                    backgroundColor: colors.surface,
                    borderColor: colors.hairline,
                    borderRadius: theme.radius.xl,
                    transform: [
                      { translateX: panX },
                      { rotateZ },
                      { scale: scaleAnim },
                    ],
                  },
                  theme.shadow.sheet(theme.isDark),
                ]}
              >
                {/* Washi Tape */}
                <View
                  style={[
                    styles.washiTape,
                    {
                      backgroundColor: theme.isDark
                        ? 'rgba(255, 255, 255, 0.18)'
                        : 'rgba(255, 255, 255, 0.70)',
                    },
                  ]}
                  pointerEvents="none"
                  importantForAccessibility="no-hide-descendants"
                >
                  <Text
                    style={[
                      styles.washiTapeText,
                      { color: colors.ink },
                    ]}
                  >
                    YANJI ARCHIVE
                  </Text>
                </View>

                {/* Top Photo Frame (Scheme A Fade) */}
                {currentNote.imageUri ? (
                  <View style={styles.photoContainer} importantForAccessibility="no-hide-descendants">
                    <View style={styles.photoInner}>
                      <Image
                        source={{ uri: currentNote.imageUri }}
                        style={StyleSheet.absoluteFill}
                        resizeMode="cover"
                      />
                      <Svg width="100%" height="100%" style={StyleSheet.absoluteFill} pointerEvents="none">
                        <Defs>
                          <LinearGradient id="roamingPaperFade" x1="0" y1="0" x2="0" y2="1">
                            <Stop offset="0" stopColor={colors.surface} stopOpacity="0" />
                            <Stop offset="0.6" stopColor={colors.surface} stopOpacity="0.4" />
                            <Stop offset="1" stopColor={colors.surface} stopOpacity="1" />
                          </LinearGradient>
                        </Defs>
                        <Rect x="0" y="0" width="100%" height="100%" fill="url(#roamingPaperFade)" />
                      </Svg>
                    </View>
                  </View>
                ) : null}

                {/* Card Content */}
                <Pressable
                  onPress={() => {
                    if (onEditNote) {
                      onEditNote({
                        id: currentNote.id,
                        content: currentNote.content,
                        category: currentNote.category,
                        isFavorite: currentNote.isFavorite,
                        imageUri: currentNote.imageUri,
                      });
                    }
                  }}
                  accessibilityRole="button"
                  accessibilityLabel="编辑这篇随笔"
                  style={[
                    styles.cardBody,
                    {
                      paddingTop: currentNote.imageUri ? YanjiSpacing.xs : YanjiSpacing.xl,
                    },
                  ]}
                >
                  {/* Decorative Quotation Mark */}
                  <Text
                    style={[
                      styles.quoteMark,
                      { color: colors.brand },
                    ]}
                    pointerEvents="none"
                    importantForAccessibility="no"
                  >
                    “
                  </Text>

                  {/* Note text */}
                  <Text
                    style={[
                      typography.bodyWriting,
                      styles.noteText,
                      { color: colors.ink },
                    ]}
                  >
                    {currentNote.content}
                  </Text>

                  {/* Card Footer */}
                  <View style={[styles.cardFooter, { borderTopColor: colors.hairline }]}>
                    <View style={styles.metaColumn}>
                      <Text style={[styles.dateText, { color: colors.ink }]}>
                        {formatNoteDateTime(currentNote.timestamp)}
                      </Text>
                      <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: 2 }]}>
                        {currentNote.category === 'DOUBT'
                          ? currentNote.isResolved
                            ? '已解决疑点'
                            : '待解决疑点'
                          : '灵感'}
                      </Text>
                    </View>

                    {/* Vermilion Wax Seal Stamp */}
                    <View style={styles.waxSeal} importantForAccessibility="no-hide-descendants">
                      <Text style={styles.waxSealLatin}>YANJI</Text>
                      <Text style={styles.waxSealKanji}>迹</Text>
                    </View>
                  </View>
                </Pressable>
              </Animated.View>
            </View>
          ) : null}
        </View>

        {/* Bottom Navigation Controls */}
        <View style={styles.footerControls}>
          <Pressable
            onPress={goToPrev}
            disabled={notes.length <= 1}
            accessibilityRole="button"
            accessibilityLabel="上一篇随笔"
            style={({ pressed }) => [
              styles.navCircleButton,
              {
                backgroundColor: immersed.pillBg,
                borderColor: immersed.pillBorder,
                opacity: notes.length <= 1 ? 0.3 : pressed ? 0.75 : 1,
                transform: [{ scale: pressed ? 0.94 : 1 }],
              },
            ]}
          >
            <YanjiIcon name="back" size={18} color={immersed.ink} />
          </Pressable>

          <Pressable
            onPress={drawRandom}
            disabled={notes.length <= 1}
            accessibilityRole="button"
            accessibilityLabel="随机抽取随笔"
            style={({ pressed }) => [
              styles.randomPillButton,
              {
                backgroundColor: immersed.primaryActionBg,
                borderColor: immersed.primaryActionBorder,
                opacity: notes.length <= 1 ? 0.4 : pressed ? 0.85 : 1,
                transform: [{ scale: pressed ? 0.96 : 1 }],
              },
            ]}
          >
            <YanjiIcon name="sparkles" size={16} color={immersed.accent} />
            <Text style={[styles.randomPillText, { color: immersed.accent }]}>随机抽取</Text>
          </Pressable>

          <Pressable
            onPress={goToNext}
            disabled={notes.length <= 1}
            accessibilityRole="button"
            accessibilityLabel="下一篇随笔"
            style={({ pressed }) => [
              styles.navCircleButton,
              {
                backgroundColor: immersed.pillBg,
                borderColor: immersed.pillBorder,
                opacity: notes.length <= 1 ? 0.3 : pressed ? 0.75 : 1,
                transform: [{ scale: pressed ? 0.94 : 1 }],
              },
            ]}
          >
            <YanjiIcon name="forward" size={18} color={immersed.ink} />
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  scrim: {
    flex: 1,
    justifyContent: 'space-between',
    paddingHorizontal: YanjiSpacing.md,
  },
  ambientGlow: {
    position: 'absolute',
    top: '30%',
    left: '20%',
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: YanjiSpacing.xs,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  headerTitle: {
    fontFamily: 'serif',
    fontSize: 16,
    letterSpacing: 0.5,
  },
  counterBadge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: YanjiRadius.full,
    borderWidth: 1,
  },
  counterText: {
    fontSize: 10,
    fontWeight: '600',
  },
  closeButton: {
    width: YanjiTouch.min,
    height: YanjiTouch.min,
    borderRadius: YanjiTouch.min / 2,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stage: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: YanjiSpacing.md,
  },
  emptyCard: {
    width: '100%',
    maxWidth: EMPTY_CARD_MAX_WIDTH,
    padding: YanjiSpacing.xl,
    alignItems: 'center',
    borderWidth: 1,
  },
  emptyIconWrapper: {
    width: 52,
    height: 52,
    borderRadius: 52 / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardsStack: {
    width: '100%',
    maxWidth: CARD_MAX_WIDTH,
    position: 'relative',
  },
  /**
   * The deck follows the active card's layout box: `top` / `bottom` insets
   * resolve against the stack, whose height is the card's own content height.
   * A fixed height would leave a short note floating over a tall grey slab and
   * disappear behind a long one.
   */
  depthDeck2: {
    position: 'absolute',
    top: 14,
    bottom: -14,
    left: 15,
    right: 15,
    borderWidth: 1,
    opacity: 0.2,
    transform: [{ rotate: '-5deg' }],
  },
  depthDeck1: {
    position: 'absolute',
    top: 8,
    bottom: -8,
    left: 8,
    right: 8,
    borderWidth: 1,
    opacity: 0.35,
    transform: [{ rotate: '3.5deg' }],
  },
  activeCard: {
    width: '100%',
    borderWidth: 1,
    overflow: 'hidden',
    position: 'relative',
  },
  washiTape: {
    position: 'absolute',
    top: -1,
    left: '50%',
    marginLeft: -48,
    width: 96,
    height: 16,
    borderRadius: YanjiRadius.xs,
    borderLeftWidth: 2,
    borderRightWidth: 2,
    borderLeftColor: 'rgba(42, 36, 29, 0.15)',
    borderRightColor: 'rgba(42, 36, 29, 0.15)',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 20,
    transform: [{ rotate: '-1deg' }],
  },
  washiTapeText: {
    fontSize: 7.5,
    fontWeight: '700',
    fontFamily: 'monospace',
    letterSpacing: 1.5,
    opacity: 0.5,
  },
  photoContainer: {
    width: '100%',
    height: PHOTO_HEIGHT,
    padding: YanjiSpacing.sm,
    paddingBottom: 0,
  },
  photoInner: {
    width: '100%',
    height: '100%',
    borderRadius: YanjiRadius.lg,
    overflow: 'hidden',
    position: 'relative',
  },
  cardBody: {
    padding: YanjiSpacing.lg,
    position: 'relative',
  },
  quoteMark: {
    position: 'absolute',
    top: 6,
    left: 14,
    fontSize: 32,
    fontFamily: 'serif',
    opacity: 0.25,
  },
  noteText: {
    fontFamily: 'serif',
    fontSize: 14,
    lineHeight: 22,
    letterSpacing: 0.3,
    marginBottom: YanjiSpacing.md,
  },
  cardFooter: {
    borderTopWidth: 1,
    paddingTop: YanjiSpacing.sm,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  metaColumn: {
    flex: 1,
  },
  dateText: {
    fontSize: 11,
    fontWeight: '700',
  },
  waxSeal: {
    width: 36,
    height: 36,
    borderRadius: 36 / 2,
    backgroundColor: WAX_SEAL_BG,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.35)',
    alignItems: 'center',
    justifyContent: 'center',
    transform: [{ rotate: '6deg' }],
    shadowColor: WAX_SEAL_SHADOW,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.35,
    shadowRadius: 4,
    elevation: 3,
  },
  waxSealLatin: {
    fontSize: 6.5,
    fontFamily: 'monospace',
    color: WHITE_GLYPH,
    opacity: 0.85,
    lineHeight: 8,
  },
  waxSealKanji: {
    fontSize: 11,
    fontFamily: 'serif',
    fontWeight: '700',
    color: WHITE_GLYPH,
    lineHeight: 12,
  },
  footerControls: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: YanjiSpacing.xl,
  },
  navCircleButton: {
    width: YanjiTouch.min,
    height: YanjiTouch.min,
    borderRadius: YanjiTouch.min / 2,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  randomPillButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: YanjiSpacing.lg,
    height: YanjiTouch.min,
    borderRadius: YanjiTouch.min / 2,
    borderWidth: 1,
  },
  randomPillText: {
    fontSize: 13,
    fontWeight: '700',
  },
});
