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
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { NoteCategory, NoteEntry, YanjiDataNative } from '../bridge';
import { YanjiIcon } from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing } from '../theme/tokens';

const WHITE_GLYPH = '#FFFFFF'; // token-exempt: white contrast glyph on translucent dark scrim
const AMBER_GLYPH = '#FDE68A'; // token-exempt: amber highlight on dark scrim
const WAX_SEAL_BG = '#C84B31'; // token-exempt: vermilion wax seal stamp
const WAX_SEAL_SHADOW = '#9E2B18'; // token-exempt: vermilion wax seal stamp shadow

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
  const { colors, typography, radius } = theme.paper;

  const [notes, setNotes] = useState<NoteEntry[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [loading, setLoading] = useState(false);

  // 3D Tilt and Swipe animations
  const panX = useRef(new Animated.Value(0)).current;
  const scaleAnim = useRef(new Animated.Value(1)).current;

  const loadRandomBatch = useCallback(async () => {
    setLoading(true);
    try {
      const batch = await YanjiDataNative.getRandomNotes(30);
      if (batch && batch.length > 0) {
        setNotes(batch);
        setCurrentIndex(0);
      } else {
        const all = await YanjiDataNative.getAllNotes();
        setNotes(all || []);
        setCurrentIndex(0);
      }
    } catch {
      // Keep existing state on error
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (visible) {
      void loadRandomBatch();
    }
  }, [visible]);

  const goToNext = useCallback(() => {
    if (notes.length <= 1) return;
    Animated.timing(panX, {
      toValue: -300,
      duration: 150,
      useNativeDriver: true,
    }).start(() => {
      setCurrentIndex(prev => (prev + 1) % notes.length);
      panX.setValue(300);
      Animated.spring(panX, {
        toValue: 0,
        friction: 7,
        tension: 40,
        useNativeDriver: true,
      }).start();
    });
  }, [notes.length, panX]);

  const goToPrev = useCallback(() => {
    if (notes.length <= 1) return;
    Animated.timing(panX, {
      toValue: 300,
      duration: 150,
      useNativeDriver: true,
    }).start(() => {
      setCurrentIndex(prev => (prev - 1 + notes.length) % notes.length);
      panX.setValue(-300);
      Animated.spring(panX, {
        toValue: 0,
        friction: 7,
        tension: 40,
        useNativeDriver: true,
      }).start();
    });
  }, [notes.length, panX]);

  const drawRandom = useCallback(() => {
    if (notes.length <= 1) return;
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
    ]).start();

    let nextIdx = currentIndex;
    do {
      nextIdx = Math.floor(Math.random() * notes.length);
    } while (nextIdx === currentIndex && notes.length > 1);
    setCurrentIndex(nextIdx);
  }, [notes.length, currentIndex, scaleAnim]);

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
    [panX, goToNext, goToPrev]
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
      animationType="fade"
      onRequestClose={onClose}
      statusBarTranslucent
    >
      <View style={[styles.scrim, { paddingTop: insets.top + YanjiSpacing.sm, paddingBottom: insets.bottom + YanjiSpacing.md }]}>
        {/* Ambient Glow */}
        <View style={styles.ambientGlow} pointerEvents="none" />

        {/* Header */}
        <View style={styles.headerRow}>
          <View style={styles.headerLeft}>
            <Text style={[typography.titleGroup, styles.headerTitle]}>
              漫游随笔
            </Text>
            {notes.length > 0 ? (
              <View style={styles.counterBadge}>
                <Text style={styles.counterText}>
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
              { opacity: pressed ? 0.75 : 1, transform: [{ scale: pressed ? 0.94 : 1 }] },
            ]}
          >
            <YanjiIcon name="close" size={16} color={WHITE_GLYPH} />
          </Pressable>
        </View>

        {/* 3D Spatial Stage */}
        <View style={styles.stage}>
          {notes.length === 0 ? (
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
              <View style={styles.emptyIconWrapper}>
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
                在时间线点击「记录此刻」记录灵感与疑点，随笔将在此漫游浮现。
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
                  <View style={styles.photoContainer}>
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
                    <View style={styles.waxSeal}>
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
                opacity: notes.length <= 1 ? 0.3 : pressed ? 0.75 : 1,
                transform: [{ scale: pressed ? 0.94 : 1 }],
              },
            ]}
          >
            <YanjiIcon name="back" size={18} color={WHITE_GLYPH} />
          </Pressable>

          <Pressable
            onPress={drawRandom}
            disabled={notes.length <= 1}
            accessibilityRole="button"
            accessibilityLabel="随机抽取随笔"
            style={({ pressed }) => [
              styles.randomPillButton,
              {
                opacity: notes.length <= 1 ? 0.4 : pressed ? 0.85 : 1,
                transform: [{ scale: pressed ? 0.96 : 1 }],
              },
            ]}
          >
            <YanjiIcon name="sparkles" size={16} color={AMBER_GLYPH} />
            <Text style={styles.randomPillText}>随机抽取</Text>
          </Pressable>

          <Pressable
            onPress={goToNext}
            disabled={notes.length <= 1}
            accessibilityRole="button"
            accessibilityLabel="下一篇随笔"
            style={({ pressed }) => [
              styles.navCircleButton,
              {
                opacity: notes.length <= 1 ? 0.3 : pressed ? 0.75 : 1,
                transform: [{ scale: pressed ? 0.94 : 1 }],
              },
            ]}
          >
            <YanjiIcon name="forward" size={18} color={WHITE_GLYPH} />
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  scrim: {
    flex: 1,
    backgroundColor: 'rgba(21, 20, 18, 0.90)',
    justifyContent: 'space-between',
    paddingHorizontal: YanjiSpacing.md,
  },
  ambientGlow: {
    position: 'absolute',
    top: '30%',
    left: '20%',
    width: 260,
    height: 260,
    borderRadius: 260 / 2,
    backgroundColor: 'rgba(200, 75, 49, 0.12)',
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
    color: WHITE_GLYPH,
    fontFamily: 'serif',
    fontSize: 16,
    letterSpacing: 0.5,
  },
  counterBadge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: YanjiRadius.full,
    backgroundColor: 'rgba(255, 255, 255, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.2)',
  },
  counterText: {
    color: WHITE_GLYPH,
    fontSize: 10,
    fontWeight: '600',
  },
  closeButton: {
    width: 36,
    height: 36,
    borderRadius: 36 / 2,
    backgroundColor: 'rgba(255, 255, 255, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.2)',
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
    width: 280,
    padding: YanjiSpacing.xl,
    alignItems: 'center',
    borderWidth: 1,
  },
  emptyIconWrapper: {
    width: 52,
    height: 52,
    borderRadius: 52 / 2,
    backgroundColor: 'rgba(0, 0, 0, 0.04)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardsStack: {
    width: 295,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  depthDeck2: {
    position: 'absolute',
    width: 265,
    height: 340,
    borderWidth: 1,
    opacity: 0.2,
    transform: [{ rotate: '-5deg' }, { translateY: 14 }],
  },
  depthDeck1: {
    position: 'absolute',
    width: 280,
    height: 360,
    borderWidth: 1,
    opacity: 0.35,
    transform: [{ rotate: '3.5deg' }, { translateY: 8 }],
  },
  activeCard: {
    width: 295,
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
    borderLeftColor: 'rgba(0, 0, 0, 0.15)',
    borderRightColor: 'rgba(0, 0, 0, 0.15)',
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
    height: 140,
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
    width: 42,
    height: 42,
    borderRadius: 42 / 2,
    backgroundColor: 'rgba(255, 255, 255, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.2)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  randomPillButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: YanjiSpacing.lg,
    height: 40,
    borderRadius: 40 / 2,
    backgroundColor: 'rgba(217, 119, 6, 0.25)',
    borderWidth: 1,
    borderColor: 'rgba(245, 158, 11, 0.5)',
  },
  randomPillText: {
    color: AMBER_GLYPH,
    fontSize: 13,
    fontWeight: '700',
  },
});
