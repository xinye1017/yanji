import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Animated,
  Image,
  Modal,
  PanResponder,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import Svg, { Defs, LinearGradient, RadialGradient, Rect, Stop } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { NoteCategory, NoteEntry, YanjiDataNative } from '../bridge';
import { YanjiIcon, YanjiSkeleton, YanjiSkeletonBone, YanjiSkeletonText } from './YanjiUI';
import type { YanjiIconName } from './icons';
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
/** How far the back of the deck hangs below the active card. */
const DECK_DEPTH = 14;
/**
 * The shortest the card may be. On device a one-line note (「测试2」) drew the
 * card as a thin banner about a seventh of the screen tall, floating in an
 * empty field. At this height a short note still reads as a card — text on
 * top, the footer and actions at its foot — and a long one is still capped by
 * the stage. Never taller than the stage allows.
 */
const CARD_MIN_HEIGHT = 240;
/** The wax seal's diameter — shared by the seal and its skeleton bone. */
const WAX_SEAL_SIZE = 36;
/** Off-screen travel for the swipe-to-turn animation. */
const SWIPE_TRAVEL = 300;
/** Drag distance at which the deck has fully risen toward the next card. */
const DECK_RISE_TRAVEL = 160;
/** Wireframe line widths for the card while the batch is drawn. */
const ROAMING_LINE_BONES = ['92%', '84%', '58%'] as const;

const DAY_MS = 24 * 60 * 60 * 1000;

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

/**
 * When a note was written, in the two forms the card needs.
 *
 * Roaming surfaces old notes, so the year is part of the date whenever it is
 * not this year — 「11月22日」 alone cannot tell this November from last. The
 * relative half (「今天」「3 天前」「5 个月前」「2 年前」) answers how far back the
 * card reaches without making the reader do date arithmetic. Days are counted
 * between local midnights, so a note from 23:50 yesterday is 「昨天」, not
 * 「今天」. A timestamp in the future (a clock that was ahead) gets no relative
 * half rather than a negative one.
 */
export function roamingDateLabel(epochMs: number, nowMs: number): { date: string; relative: string } {
  const at = new Date(epochMs);
  const now = new Date(nowMs);
  const h = String(at.getHours()).padStart(2, '0');
  const min = String(at.getMinutes()).padStart(2, '0');
  const day = `${at.getMonth() + 1}月${at.getDate()}日 ${h}:${min}`;
  const date = at.getFullYear() === now.getFullYear() ? day : `${at.getFullYear()}年${day}`;

  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOf(now) - startOf(at)) / DAY_MS);
  const relative = days < 0 ? ''
    : days === 0 ? '今天'
      : days === 1 ? '昨天'
        : days < 30 ? `${days} 天前`
          : days < 365 ? `${Math.floor(days / 30)} 个月前`
            : `${Math.floor(days / 365)} 年前`;
  return { date, relative };
}

/** The category line under the date: what kind of note this is, and for a doubt, where it stands. */
function categoryLabel(note: NoteEntry): { label: string; icon: YanjiIconName } {
  if (note.category === 'DOUBT') {
    return note.isResolved
      ? { label: '疑点 · 已解决', icon: 'doubtResolved' }
      : { label: '疑点 · 待解决', icon: 'doubtUnresolved' };
  }
  return { label: '灵感', icon: 'inspiration' };
}

export function RoamingNotesModal({
  visible,
  onClose,
  onEditNote,
}: RoamingNotesModalProps): React.JSX.Element | null {
  const theme = useYanjiTheme();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const { typography, layout } = theme.paper;
  // The roaming field is always dark, independent of the app's light/dark theme:
  // it is the one place that owns its whole screen, like the focus immersion.
  // The card is part of that field too — every colour below reads `immersed`,
  // never the theme's paper, so a light-mode phone no longer gets a glaring
  // parchment card on a near-black screen.
  const immersed = theme.paper.immersive.running;

  // Start in loading so reopening never flashes the "暂无随笔" empty state
  // before the random batch resolves.
  const [notes, setNotes] = useState<NoteEntry[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  /**
   * The stage's measured height. The card is capped to it so a long note
   * scrolls inside the card instead of pushing the card over the header and
   * the controls. Zero until the first layout: no cap is applied before the
   * stage has a size to cap against.
   */
  const [stageHeight, setStageHeight] = useState(0);
  /** A favourite write in flight; the toggle is disabled until Room answers. */
  const [favoriteBusy, setFavoriteBusy] = useState(false);

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

  /**
   * Flip the current card's favourite. Only a confirmed write changes the
   * card: `false` means the note is gone from Room, and a rejection leaves the
   * stored state as it was, so in both cases the card keeps showing the truth.
   */
  const toggleFavorite = useCallback(async (note: NoteEntry) => {
    if (favoriteBusy) return;
    setFavoriteBusy(true);
    try {
      const saved = await YanjiDataNative.toggleFavoriteNote(note.id);
      if (saved) {
        setNotes(prev => prev.map(item => (item.id === note.id ? { ...item, isFavorite: !item.isFavorite } : item)));
      }
    } catch {
      // The stored state is unchanged; the card already shows it.
    } finally {
      setFavoriteBusy(false);
    }
  }, [favoriteBusy]);

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
        // Horizontal drags turn the card; vertical ones stay with the note's
        // own scroll view, so a long note can be read without turning it.
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
  /**
   * The deck rises as the top card is dragged away: the card underneath
   * straightens, lifts and brightens toward where the next card will sit, so
   * the stack reads as real paper instead of two fixed grey slabs. Driven by
   * the same `panX` as the drag, so reduced motion (which never moves `panX`)
   * leaves it still.
   */
  const deckRise = (rest: number, risen: number) => panX.interpolate({
    inputRange: [-DECK_RISE_TRAVEL, 0, DECK_RISE_TRAVEL],
    outputRange: [risen, rest, risen],
    extrapolate: 'clamp',
  });
  const deckTurn = (rest: string, risen: string) => panX.interpolate({
    inputRange: [-DECK_RISE_TRAVEL, 0, DECK_RISE_TRAVEL],
    outputRange: [risen, rest, risen],
    extrapolate: 'clamp',
  });

  const cardMaxHeight = stageHeight > 0
    ? stageHeight - DECK_DEPTH - YanjiSpacing.md * 2
    : undefined;
  // A short stage (split screen, large font scale) wins over the floor.
  const cardMinHeight = cardMaxHeight === undefined
    ? CARD_MIN_HEIGHT
    : Math.min(CARD_MIN_HEIGHT, cardMaxHeight);
  const cardSurface = {
    backgroundColor: immersed.cardSurface,
    borderColor: immersed.cardHairline,
    borderWidth: layout.hairlineWidth,
    borderRadius: theme.radius.xl,
  };
  const bone = { backgroundColor: immersed.cardBone };
  const when = currentNote ? roamingDateLabel(currentNote.timestamp, Date.now()) : null;
  const category = currentNote ? categoryLabel(currentNote) : null;

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
            <Text accessibilityRole="header" style={[typography.titleGroup, styles.headerTitle, { color: immersed.ink }]}>
              漫游随笔
            </Text>
            {notes.length > 0 ? (
              <View
                style={[
                  styles.counterBadge,
                  {
                    backgroundColor: immersed.subtle,
                    borderColor: immersed.pillBorder,
                    borderWidth: layout.hairlineWidth,
                  },
                ]}
              >
                <Text style={[typography.captionMeta, { color: immersed.ink }]}>
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
        <View style={styles.stage} onLayout={event => setStageHeight(event.nativeEvent.layout.height)}>
          {loading ? (
            // The card itself, as a wireframe: same width, radius and body
            // padding, three lines of note, the date / seal footer and the
            // action bar — bones in the card's own dark tone.
            <YanjiSkeleton testID="roaming-skeleton" accessibilityLabel="正在拾取随笔"
              style={styles.cardsStack}>
              <View style={[styles.activeCard, cardSurface, { minHeight: cardMinHeight }]}>
                <View style={[styles.cardBody, styles.skeletonBody, { paddingTop: YanjiSpacing.xl }]}>
                  <View style={styles.noteBlock}>
                    {ROAMING_LINE_BONES.map(width => (
                      <YanjiSkeletonText key={width} type={typography.bodyWriting} width={width} style={bone} />
                    ))}
                  </View>
                  <View style={[styles.cardFooter, { borderTopColor: immersed.cardHairline, borderTopWidth: layout.hairlineWidth }]}>
                    <View style={styles.metaColumn}>
                      <YanjiSkeletonText type={typography.captionMeta} width={108} style={bone} />
                      <YanjiSkeletonText type={typography.captionMeta} width={72} style={bone} />
                    </View>
                    <YanjiSkeletonBone width={WAX_SEAL_SIZE} height={WAX_SEAL_SIZE} round style={bone} />
                  </View>
                </View>
                <View style={[styles.actionBar, { borderTopColor: immersed.cardHairline, borderTopWidth: layout.hairlineWidth }]}>
                  <View style={styles.actionHalf}>
                    <YanjiSkeletonText type={typography.captionMeta} width={44} style={bone} />
                  </View>
                  <View style={styles.actionHalf}>
                    <YanjiSkeletonText type={typography.captionMeta} width={44} style={bone} />
                  </View>
                </View>
              </View>
            </YanjiSkeleton>
          ) : notes.length === 0 ? (
            <View style={[styles.emptyCard, cardSurface]}>
              <View style={[styles.emptyIconWrapper, { backgroundColor: immersed.subtle }]}>
                <YanjiIcon name="sprout" size={28} color={immersed.secondary} />
              </View>
              <Text style={[typography.titleGroup, { color: immersed.ink, marginTop: YanjiSpacing.md }]}>
                暂无随笔
              </Text>
              <Text
                style={[
                  typography.bodyJournal,
                  { color: immersed.secondary, textAlign: 'center', marginTop: YanjiSpacing.sm },
                ]}
              >
                在时间线点击「记录此刻」写下随笔。
              </Text>
            </View>
          ) : currentNote && when && category ? (
            <View style={styles.cardsStack}>
              {/* Deck Depth 2 (Back) */}
              <Animated.View
                style={[
                  styles.depthDeck2,
                  cardSurface,
                  {
                    opacity: deckRise(0.35, 0.55),
                    transform: [
                      { translateY: deckRise(0, -4) },
                      { rotate: deckTurn('-5deg', '-3deg') },
                    ],
                  },
                ]}
                pointerEvents="none"
                importantForAccessibility="no-hide-descendants"
              />

              {/* Deck Depth 1 (Middle) */}
              <Animated.View
                style={[
                  styles.depthDeck1,
                  cardSurface,
                  {
                    opacity: deckRise(0.6, 0.9),
                    transform: [
                      { translateY: deckRise(0, -6) },
                      { rotate: deckTurn('3.5deg', '1deg') },
                    ],
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
                  cardSurface,
                  {
                    minHeight: cardMinHeight,
                    maxHeight: cardMaxHeight,
                    transform: [
                      { translateX: panX },
                      { rotateZ },
                      { scale: scaleAnim },
                    ],
                  },
                  theme.shadow.sheet(true),
                ]}
              >
                {/* Washi tape: a strip of paper holding the card, nothing written on it. */}
                <View
                  style={[styles.washiTape, { backgroundColor: immersed.cardTape }]}
                  pointerEvents="none"
                  importantForAccessibility="no-hide-descendants"
                />

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
                            <Stop offset="0" stopColor={immersed.cardSurface} stopOpacity="0" />
                            <Stop offset="0.6" stopColor={immersed.cardSurface} stopOpacity="0.4" />
                            <Stop offset="1" stopColor={immersed.cardSurface} stopOpacity="1" />
                          </LinearGradient>
                        </Defs>
                        <Rect x="0" y="0" width="100%" height="100%" fill="url(#roamingPaperFade)" />
                      </Svg>
                    </View>
                  </View>
                ) : null}

                {/* The note itself. It scrolls inside the card when it is longer
                    than the stage allows; the footer and actions stay put.
                    Keyed by note so a new card always opens at its first line. */}
                <ScrollView
                  key={currentNote.id}
                  style={styles.noteScroll}
                  contentContainerStyle={[
                    styles.cardBody,
                    { paddingTop: currentNote.imageUri ? YanjiSpacing.xs : YanjiSpacing.xl, paddingBottom: 0 },
                  ]}
                  showsVerticalScrollIndicator
                  persistentScrollbar
                >
                  {/* Decorative Quotation Mark */}
                  <Text
                    style={[styles.quoteMark, { color: immersed.accent }]}
                    pointerEvents="none"
                    importantForAccessibility="no"
                  >
                    “
                  </Text>
                  <Text style={[typography.bodyWriting, styles.noteText, { color: immersed.ink }]}>
                    {currentNote.content}
                  </Text>
                </ScrollView>

                {/* Card Footer: when and what, then the seal. */}
                <View style={styles.cardBodyFooter}>
                  <View style={[styles.cardFooter, { borderTopColor: immersed.cardHairline, borderTopWidth: layout.hairlineWidth }]}>
                    <View style={styles.metaColumn}>
                      <Text style={[typography.captionMeta, styles.dateText, { color: immersed.ink }]}>
                        {when.date}
                        {when.relative ? (
                          <Text style={{ color: immersed.subtitle, fontWeight: typography.captionMeta.fontWeight }}>
                            {`  ·  ${when.relative}`}
                          </Text>
                        ) : null}
                      </Text>
                      <View style={styles.categoryRow}>
                        <YanjiIcon name={category.icon} size={layout.momentTagIconSlot} color={immersed.secondary} />
                        <Text style={[typography.captionMeta, { color: immersed.secondary }]}>
                          {category.label}
                        </Text>
                      </View>
                    </View>

                    {/* Vermilion Wax Seal Stamp */}
                    <View style={styles.waxSeal} importantForAccessibility="no-hide-descendants">
                      <Text style={styles.waxSealKanji}>迹</Text>
                    </View>
                  </View>
                </View>

                {/* The card's own actions. Editing used to be a tap anywhere on
                    the text with nothing saying so; favouriting meant leaving
                    for the library. Both are now named buttons on the card. */}
                <View style={[styles.actionBar, { borderTopColor: immersed.cardHairline, borderTopWidth: layout.hairlineWidth }]}>
                  <Pressable
                    onPress={() => void toggleFavorite(currentNote)}
                    disabled={favoriteBusy}
                    accessibilityRole="button"
                    accessibilityLabel={currentNote.isFavorite ? '取消收藏这篇随笔' : '收藏这篇随笔'}
                    accessibilityState={{ selected: currentNote.isFavorite, busy: favoriteBusy, disabled: favoriteBusy }}
                    style={({ pressed }) => [styles.actionHalf, { opacity: favoriteBusy ? 0.5 : pressed ? 0.65 : 1 }]}
                  >
                    <YanjiIcon
                      name={currentNote.isFavorite ? 'bookmark' : 'bookmarkOutline'}
                      size={16}
                      color={currentNote.isFavorite ? immersed.accent : immersed.secondary}
                    />
                    <Text style={[typography.captionMeta, {
                      color: currentNote.isFavorite ? immersed.accent : immersed.secondary,
                    }]}>
                      {currentNote.isFavorite ? '已收藏' : '收藏'}
                    </Text>
                  </Pressable>
                  <View style={[styles.actionDivider, { backgroundColor: immersed.cardHairline, width: layout.hairlineWidth }]} />
                  <Pressable
                    onPress={() => onEditNote?.({
                      id: currentNote.id,
                      content: currentNote.content,
                      category: currentNote.category,
                      isFavorite: currentNote.isFavorite,
                      imageUri: currentNote.imageUri,
                    })}
                    disabled={!onEditNote}
                    accessibilityRole="button"
                    accessibilityLabel="编辑这篇随笔"
                    accessibilityState={{ disabled: !onEditNote }}
                    style={({ pressed }) => [styles.actionHalf, { opacity: !onEditNote ? 0.5 : pressed ? 0.65 : 1 }]}
                  >
                    <YanjiIcon name="compose" size={16} color={immersed.secondary} />
                    <Text style={[typography.captionMeta, { color: immersed.secondary }]}>编辑</Text>
                  </Pressable>
                </View>
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
            <Text style={[typography.titleLedger, { color: immersed.accent }]}>随机抽取</Text>
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
    gap: YanjiSpacing.sm,
  },
  headerTitle: {
    fontFamily: 'serif',
  },
  counterBadge: {
    paddingHorizontal: YanjiSpacing.sm,
    paddingVertical: 2,
    borderRadius: YanjiRadius.full,
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
    top: DECK_DEPTH,
    bottom: -DECK_DEPTH,
    left: 15,
    right: 15,
  },
  depthDeck1: {
    position: 'absolute',
    top: 8,
    bottom: -8,
    left: 8,
    right: 8,
  },
  activeCard: {
    width: '100%',
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
    zIndex: 20,
    transform: [{ rotate: '-1deg' }],
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
  /** Shrinks (never grows) so a short note keeps the card short and a long one scrolls. */
  noteScroll: {
    // Grows into the card's minimum height so a short note keeps its footer and
    // actions at the card's foot; shrinks under the stage cap so a long one
    // scrolls instead of pushing them off.
    flexGrow: 1,
    flexShrink: 1,
  },
  /** The wireframe's body fills the same minimum height as the real card. */
  skeletonBody: {
    flexGrow: 1,
    justifyContent: 'space-between',
  },
  cardBody: {
    padding: YanjiSpacing.lg,
    position: 'relative',
  },
  cardBodyFooter: {
    paddingHorizontal: YanjiSpacing.lg,
    paddingBottom: YanjiSpacing.md,
  },
  noteBlock: {
    marginBottom: YanjiSpacing.md,
  },
  quoteMark: {
    position: 'absolute',
    top: 6,
    left: 14,
    fontSize: 32,
    fontFamily: 'serif',
    opacity: 0.35,
  },
  noteText: {
    letterSpacing: 0.3,
    marginBottom: YanjiSpacing.md,
  },
  cardFooter: {
    paddingTop: YanjiSpacing.sm,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: YanjiSpacing.sm,
  },
  metaColumn: {
    flex: 1,
    gap: 2,
  },
  dateText: {
    fontWeight: '600',
  },
  categoryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: YanjiSpacing.xs,
  },
  waxSeal: {
    width: WAX_SEAL_SIZE,
    height: WAX_SEAL_SIZE,
    borderRadius: WAX_SEAL_SIZE / 2,
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
  /** One glyph, large enough to read — the old 6.5sp 「YANJI」 line was unreadable. */
  waxSealKanji: {
    fontSize: 16,
    lineHeight: 20,
    fontFamily: 'serif',
    fontWeight: '700',
    color: WHITE_GLYPH,
  },
  actionBar: {
    flexDirection: 'row',
    alignItems: 'stretch',
  },
  actionHalf: {
    flex: 1,
    minHeight: YanjiTouch.min,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  actionDivider: {
    alignSelf: 'stretch',
    marginVertical: YanjiSpacing.sm,
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
});
