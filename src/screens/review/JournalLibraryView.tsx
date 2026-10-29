import React, { useCallback, useEffect, useState } from 'react';
import {
  Image,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';
import { NoteCategory, NoteEntry, YanjiDataNative, onDataChanged } from '../../bridge';
import { useYanjiTheme } from '../../theme/ThemeProvider';
import { YanjiSpacing } from '../../theme/tokens';
import { YanjiIcon } from '../../components/YanjiUI';

export type JournalCategoryTab = 'favorite' | 'unresolved' | 'resolved';

export interface JournalLibraryViewProps {
  visible: boolean;
  onOpenRoaming: () => void;
  onEditNote?: (note: {
    id: string;
    content: string;
    category: NoteCategory;
    isFavorite?: boolean;
    imageUri?: string | null;
  }) => void;
}

export function JournalLibraryView({
  visible,
  onOpenRoaming,
  onEditNote,
}: JournalLibraryViewProps): React.JSX.Element {
  const theme = useYanjiTheme();
  const { colors, typography, radius } = theme.paper;

  const [notes, setNotes] = useState<NoteEntry[]>([]);
  const [activeTab, setActiveTab] = useState<JournalCategoryTab>('favorite');
  const [busyId, setBusyId] = useState<string | null>(null);

  const loadNotes = useCallback(async () => {
    try {
      const all = await YanjiDataNative.getAllNotes();
      setNotes(all || []);
    } catch {
      // Keep existing state on error
    }
  }, []);

  useEffect(() => {
    if (visible) {
      void loadNotes();
    }
  }, [visible, loadNotes]);

  useEffect(() => {
    const unsub = onDataChanged(event => {
      if (event?.type === 'notes') {
        void loadNotes();
      }
    });
    return unsub;
  }, [loadNotes]);

  const handleToggleFavorite = useCallback(async (noteId: string) => {
    if (busyId) return;
    setBusyId(noteId);
    try {
      await YanjiDataNative.toggleFavoriteNote(noteId);
      setNotes(prev =>
        prev.map(n => (n.id === noteId ? { ...n, isFavorite: !n.isFavorite } : n))
      );
    } catch {
      // revert silently
    } finally {
      setBusyId(null);
    }
  }, [busyId]);

  const handleToggleResolved = useCallback(async (noteId: string) => {
    if (busyId) return;
    setBusyId(noteId);
    try {
      await YanjiDataNative.toggleNoteResolved(noteId);
      setNotes(prev =>
        prev.map(n =>
          n.id === noteId
            ? { ...n, isResolved: !n.isResolved, resolvedAt: !n.isResolved ? Date.now() : null }
            : n
        )
      );
    } catch {
      // revert silently
    } finally {
      setBusyId(null);
    }
  }, [busyId]);

  const favoriteCount = notes.filter(n => n.isFavorite).length;
  const unresolvedCount = notes.filter(n => n.category === 'DOUBT' && !n.isResolved).length;
  const resolvedCount = notes.filter(n => n.category === 'DOUBT' && n.isResolved).length;

  const filteredNotes = notes.filter(n => {
    if (activeTab === 'favorite') return n.isFavorite;
    if (activeTab === 'unresolved') return n.category === 'DOUBT' && !n.isResolved;
    if (activeTab === 'resolved') return n.category === 'DOUBT' && n.isResolved;
    return true;
  });

  return (
    <View>
      {/* 1. Hero Card: 漫游随笔 入口 */}
      <Pressable
        onPress={onOpenRoaming}
        accessibilityRole="button"
        accessibilityLabel="漫游随笔，随机抽卡"
        style={({ pressed }) => [
          {
            backgroundColor: colors.surface,
            borderColor: colors.hairline,
            borderWidth: 1,
            borderRadius: theme.radius.lg,
            padding: YanjiSpacing.md,
            marginBottom: YanjiSpacing.md,
            overflow: 'hidden',
            opacity: pressed ? 0.92 : 1,
            transform: [{ scale: pressed ? 0.985 : 1 }],
          },
          theme.shadow.floating(theme.isDark),
        ]}
      >
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <View style={{ flex: 1, paddingRight: YanjiSpacing.sm }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              <View
                style={{
                  backgroundColor: theme.isDark ? 'rgba(200, 75, 49, 0.2)' : 'rgba(200, 75, 49, 0.1)',
                  paddingHorizontal: 6,
                  paddingVertical: 2,
                  borderRadius: radius.pill,
                }}
              >
                <Text
                  style={[
                    typography.captionMeta,
                    { color: colors.brand, fontSize: 10, fontWeight: '700', letterSpacing: 0.5 },
                  ]}
                >
                  ROAMING
                </Text>
              </View>
              <Text style={[typography.titleGroup, { color: colors.ink, fontWeight: '700', fontSize: 15 }]}>
                漫游随笔
              </Text>
            </View>
            <Text style={[typography.captionMeta, { color: colors.secondary, fontSize: 12, lineHeight: 18, marginBottom: 3 }]}>
              偶遇旧日灵感与沉思 · 随机抽取
            </Text>
            <Text style={[typography.captionMeta, { color: colors.muted, fontSize: 11 }]}>
              共 {notes.length} 篇随笔 · 左右滑动翻阅
            </Text>
          </View>
          <View
            style={{
              width: 44,
              height: 44,
              borderRadius: theme.radius.md,
              backgroundColor: colors.elevated,
              borderWidth: 1,
              borderColor: colors.hairline,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <YanjiIcon name="sparkles" size={22} color={colors.brand} />
          </View>
        </View>
      </Pressable>

      {/* 2. Three-state Category Filter Tabs */}
      <View
        style={{
          flexDirection: 'row',
          backgroundColor: colors.surface,
          borderColor: colors.hairline,
          borderWidth: 1,
          borderRadius: radius.pill,
          padding: 3,
          marginBottom: YanjiSpacing.md,
        }}
      >
        {([
          { key: 'favorite', label: '收藏', count: favoriteCount },
          { key: 'unresolved', label: '待解决', count: unresolvedCount },
          { key: 'resolved', label: '已解决', count: resolvedCount },
        ] as const).map(tab => {
          const selected = activeTab === tab.key;
          return (
            <Pressable
              key={tab.key}
              onPress={() => setActiveTab(tab.key)}
              accessibilityRole="tab"
              accessibilityState={{ selected }}
              accessibilityLabel={`${tab.label}，共 ${tab.count} 项`}
              style={({ pressed }) => ({
                flex: 1,
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 5,
                paddingVertical: 7,
                borderRadius: radius.pill,
                backgroundColor: selected ? colors.elevated : 'transparent',
                opacity: pressed ? 0.75 : 1,
              })}
            >
              <Text
                style={[
                  typography.captionMeta,
                  {
                    color: selected ? colors.ink : colors.secondary,
                    fontWeight: selected ? '700' : '500',
                    fontSize: 12,
                  },
                ]}
              >
                {tab.label}
              </Text>
              <View
                style={{
                  paddingHorizontal: 5,
                  paddingVertical: 1,
                  borderRadius: radius.pill,
                  backgroundColor: selected
                    ? (theme.isDark ? 'rgba(200, 75, 49, 0.25)' : 'rgba(200, 75, 49, 0.12)')
                    : (theme.isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.05)'),
                }}
              >
                <Text
                  style={[
                    typography.captionMeta,
                    {
                      color: selected ? colors.brand : colors.muted,
                      fontSize: 10,
                      fontWeight: '600',
                    },
                  ]}
                >
                  {tab.count}
                </Text>
              </View>
            </Pressable>
          );
        })}
      </View>

      {/* 3. Notes List */}
      {filteredNotes.length === 0 ? (
        <View
          style={{
            paddingVertical: YanjiSpacing.xl * 1.5,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Text style={[typography.captionMeta, { color: colors.muted, fontSize: 13 }]}>
            暂无相关随笔记录
          </Text>
        </View>
      ) : (
        filteredNotes.map(note => (
          <View
            key={note.id}
            style={[
              {
                backgroundColor: colors.surface,
                borderColor: colors.hairline,
                borderWidth: 1,
                borderRadius: theme.radius.lg,
                marginBottom: YanjiSpacing.sm,
                overflow: 'hidden',
              },
              theme.shadow.sheet(theme.isDark),
            ]}
          >
            {/* Scheme A Top Photo Fade */}
            {note.imageUri ? (
              <View style={{ width: '100%', height: 120, position: 'relative', overflow: 'hidden' }}>
                <Image
                  source={{ uri: note.imageUri }}
                  style={{ width: '100%', height: 120 }}
                  resizeMode="cover"
                />
                <Svg height={120} width="100%" style={StyleSheet.absoluteFill} pointerEvents="none">
                  <Defs>
                    <LinearGradient id={`paperFade_${note.id}`} x1="0" y1="0" x2="0" y2="1">
                      <Stop offset="0%" stopColor={colors.surface} stopOpacity="0" />
                      <Stop offset="35%" stopColor={colors.surface} stopOpacity="0.12" />
                      <Stop offset="75%" stopColor={colors.surface} stopOpacity="0.8" />
                      <Stop offset="100%" stopColor={colors.surface} stopOpacity="1" />
                    </LinearGradient>
                  </Defs>
                  <Rect x="0" y="0" width="100%" height="100%" fill={`url(#paperFade_${note.id})`} />
                </Svg>
              </View>
            ) : null}

            <View style={{ padding: YanjiSpacing.md, paddingTop: note.imageUri ? YanjiSpacing.xs : YanjiSpacing.md }}>
              {/* Header row: Category tag, date, bookmark icon */}
              <View
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: YanjiSpacing.xs,
                }}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                  <View
                    style={{
                      paddingHorizontal: 7,
                      paddingVertical: 2,
                      borderRadius: radius.pill,
                      backgroundColor:
                        note.category === 'DOUBT'
                          ? (theme.isDark ? 'rgba(200, 75, 49, 0.2)' : 'rgba(200, 75, 49, 0.1)')
                          : (theme.isDark ? 'rgba(42, 157, 143, 0.2)' : 'rgba(42, 157, 143, 0.1)'),
                    }}
                  >
                    <Text
                      style={[
                        typography.captionMeta,
                        {
                          fontSize: 10,
                          fontWeight: '600',
                          color: note.category === 'DOUBT' ? colors.brand : colors.positive,
                        },
                      ]}
                    >
                      {note.category === 'DOUBT' ? '疑点' : '灵感'}
                    </Text>
                  </View>
                  <Text style={[typography.captionMeta, { color: colors.secondary, fontSize: 11 }]}>
                    {note.date}
                  </Text>
                </View>

                <Pressable
                  onPress={() => handleToggleFavorite(note.id)}
                  hitSlop={8}
                  accessibilityRole="button"
                  accessibilityLabel={note.isFavorite ? '取消收藏' : '收藏'}
                  style={({ pressed }) => ({ opacity: pressed ? 0.6 : 1 })}
                >
                  <YanjiIcon
                    name={note.isFavorite ? 'bookmark' : 'bookmarkOutline'}
                    size={18}
                    color={note.isFavorite ? colors.amber : colors.secondary}
                  />
                </Pressable>
              </View>

              {/* Note content */}
              <Pressable
                onPress={() =>
                  onEditNote?.({
                    id: note.id,
                    content: note.content,
                    category: note.category,
                    isFavorite: note.isFavorite,
                    imageUri: note.imageUri,
                  })
                }
                accessibilityRole="button"
                accessibilityLabel={`编辑随笔：${note.content}`}
                style={({ pressed }) => ({ opacity: pressed ? 0.8 : 1 })}
              >
                <Text
                  style={[
                    typography.bodyWriting,
                    {
                      color: colors.ink,
                      fontSize: 14,
                      lineHeight: 22,
                      marginBottom: note.category === 'DOUBT' ? YanjiSpacing.sm : 0,
                    },
                  ]}
                >
                  {note.content}
                </Text>
              </Pressable>

              {/* If DOUBT: Action button to toggle resolved status */}
              {note.category === 'DOUBT' ? (
                <View style={{ flexDirection: 'row', justifyContent: 'flex-end', marginTop: 4 }}>
                  <Pressable
                    onPress={() => handleToggleResolved(note.id)}
                    accessibilityRole="button"
                    accessibilityLabel={note.isResolved ? '标记为待解决' : '标记为已解决'}
                    style={({ pressed }) => ({
                      flexDirection: 'row',
                      alignItems: 'center',
                      gap: 4,
                      paddingHorizontal: 10,
                      paddingVertical: 4,
                      borderRadius: radius.pill,
                      borderWidth: 1,
                      borderColor: note.isResolved
                        ? (theme.isDark ? 'rgba(42, 157, 143, 0.4)' : 'rgba(42, 157, 143, 0.3)')
                        : (theme.isDark ? 'rgba(200, 75, 49, 0.4)' : 'rgba(200, 75, 49, 0.3)'),
                      backgroundColor: note.isResolved
                        ? (theme.isDark ? 'rgba(42, 157, 143, 0.15)' : 'rgba(42, 157, 143, 0.08)')
                        : (theme.isDark ? 'rgba(200, 75, 49, 0.15)' : 'rgba(200, 75, 49, 0.08)'),
                      opacity: pressed ? 0.7 : 1,
                    })}
                  >
                    <YanjiIcon
                      name={note.isResolved ? 'check' : 'doubt'}
                      size={12}
                      color={note.isResolved ? colors.positive : colors.brand}
                    />
                    <Text
                      style={[
                        typography.captionMeta,
                        {
                          fontSize: 11,
                          fontWeight: '600',
                          color: note.isResolved ? colors.positive : colors.brand,
                        },
                      ]}
                    >
                      {note.isResolved ? '已攻克' : '待解决 · 点击标为已攻克'}
                    </Text>
                  </Pressable>
                </View>
              ) : null}
            </View>
          </View>
        ))
      )}
    </View>
  );
}
