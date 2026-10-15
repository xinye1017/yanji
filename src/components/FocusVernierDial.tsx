/**
 * The Paper & Chrono vernier: a real, horizontally scrollable 5-minute scale.
 * Visual 1-minute subdivisions are deliberately absent: native Focus accepts
 * 5-minute steps and no unrelated business value should be synthesized.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Pressable,
  ScrollView,
  Text,
  View,
  useWindowDimensions,
  type NativeSyntheticEvent,
  type NativeScrollEvent,
} from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';

const TICK_WIDTH = 26;

export interface FocusVernierDialProps {
  valueMinutes: number;
  onChangeMinutes: (value: number) => void;
  minMinutes: number;
  maxMinutes: number;
  stepMinutes: number;
  disabled?: boolean;
  accessibilityLabel?: string;
}

export function FocusVernierDial({
  valueMinutes,
  onChangeMinutes,
  minMinutes,
  maxMinutes,
  stepMinutes,
  disabled = false,
  accessibilityLabel = '选择专注时长',
}: FocusVernierDialProps): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const reducedMotion = useReducedMotion();
  const { fontScale } = useWindowDimensions();
  const list = useRef<React.ElementRef<typeof ScrollView>>(null);
  const userDragging = useRef(false);
  const [width, setWidth] = useState(0);

  const ticks = useMemo(() => {
    const values: number[] = [];
    if (stepMinutes <= 0 || maxMinutes < minMinutes) return values;
    for (let v = minMinutes; v <= maxMinutes; v += stepMinutes) values.push(v);
    return values;
  }, [minMinutes, maxMinutes, stepMinutes]);

  // Fractional offsets preserve non-grid task presets (e.g. 37 minutes)
  // exactly between the 35 and 40 marks. ScrollView's symmetric padding
  // already centres x = index * TICK_WIDTH; adding that padding to the
  // offset would move the mark away from the pointer.
  const offsetFor = useCallback((v: number) => Math.max(
    0,
    Math.min(ticks.length - 1, (v - minMinutes) / stepMinutes),
  ) * TICK_WIDTH, [minMinutes, stepMinutes, ticks.length]);

  // A task's original plannedMinutes may not be divisible by five. Merely
  // mounting this ruler must never round it or write a new planned duration.
  useEffect(() => {
    if (width <= 0 || userDragging.current || ticks.length === 0) return;
    list.current?.scrollTo({ x: offsetFor(valueMinutes), animated: false });
  }, [width, valueMinutes, ticks.length, offsetFor]);

  const selectIndex = useCallback((index: number) => {
    if (disabled || ticks.length === 0) return;
    const next = ticks[Math.max(0, Math.min(ticks.length - 1, index))];
    if (next !== valueMinutes) onChangeMinutes(next);
  }, [disabled, onChangeMinutes, ticks, valueMinutes]);

  const selectOffset = useCallback((e: NativeSyntheticEvent<NativeScrollEvent>) => {
    if (!userDragging.current) return;
    userDragging.current = false;
    selectIndex(Math.round(e.nativeEvent.contentOffset.x / TICK_WIDTH));
  }, [selectIndex]);

  const adjust = useCallback((direction: -1 | 1) => {
    if (disabled || ticks.length === 0) return;
    // From a non-step task preset, increase/decrease moves to the *next*
    // valid mark in that direction, never silently rounds on mount.
    const raw = (valueMinutes - minMinutes) / stepMinutes;
    const index = direction === 1
      ? Math.floor(raw + 0.000001) + 1
      : Math.ceil(raw - 0.000001) - 1;
    const target = Math.max(0, Math.min(ticks.length - 1, index));
    selectIndex(target);
    list.current?.scrollTo({ x: target * TICK_WIDTH, animated: !reducedMotion });
  }, [disabled, minMinutes, reducedMotion, selectIndex, stepMinutes, ticks.length, valueMinutes]);

  const horizontalInset = Math.max(0, (width - TICK_WIDTH) / 2);
  // Scale the label *container*, not just its font. At fontScale >= 2 the
  // original fixed 90dp cells clipped two- and three-digit major labels.
  const labelTop = 64;
  const labelHeight = Math.ceil(paper.typography.captionMeta.lineHeight * Math.max(1, fontScale));
  const tickHeight = Math.max(92, labelTop + labelHeight + 8);

  return (
    <View testID="focus-vernier" style={{ marginTop: YanjiSpacing.sm }}>
      <View
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={accessibilityLabel}
        accessibilityValue={{ min: minMinutes, max: maxMinutes, now: valueMinutes, text: `${valueMinutes} 分钟` }}
        accessibilityActions={[{ name: 'increment', label: `增加${stepMinutes}分钟` }, { name: 'decrement', label: `减少${stepMinutes}分钟` }]}
        onAccessibilityAction={event => {
          if (event.nativeEvent.actionName === 'increment') adjust(1);
          if (event.nativeEvent.actionName === 'decrement') adjust(-1);
        }}
        onLayout={e => setWidth(e.nativeEvent.layout.width)}
        style={{
          minHeight: tickHeight + 8,
          justifyContent: 'center',
          backgroundColor: paper.colors.subtle,
          borderRadius: paper.radius.surface,
          borderWidth: paper.layout.hairlineWidth,
          borderColor: paper.colors.hairline,
          overflow: 'hidden',
        }}
      >
        <View
          pointerEvents="none"
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: 22,
            height: paper.layout.hairlineWidth,
            backgroundColor: paper.colors.hairline,
          }}
        />
        <ScrollView
          ref={list}
          testID="focus-vernier-ruler"
          horizontal
          scrollEnabled={!disabled}
          showsHorizontalScrollIndicator={false}
          snapToInterval={TICK_WIDTH}
          snapToAlignment="start"
          decelerationRate={reducedMotion ? 'normal' : 'fast'}
          onScrollBeginDrag={() => { userDragging.current = true; }}
          onScrollEndDrag={e => {
            if (Math.abs(e.nativeEvent.velocity?.x ?? 0) < 0.1) selectOffset(e);
          }}
          onMomentumScrollEnd={selectOffset}
          contentContainerStyle={{
            paddingHorizontal: horizontalInset,
            alignItems: 'center',
            minHeight: tickHeight,
          }}
        >
          {ticks.map(minutes => {
            const major = minutes % 30 === 0;
            const medium = minutes % 15 === 0;
            const selected = minutes === valueMinutes;
            return (
              <View key={minutes} style={{ width: TICK_WIDTH, alignItems: 'center', height: tickHeight }}>
                <View
                  style={{
                    position: 'absolute',
                    top: 22,
                    width: selected ? 2 : major ? 1.5 : 1,
                    height: major ? 34 : medium ? 24 : 14,
                    borderRadius: paper.radius.tick,
                    backgroundColor: selected ? paper.colors.brandInk : paper.colors.secondary,
                    opacity: selected ? 1 : major ? 0.85 : 0.55,
                  }}
                />
                {major ? (
                  <Text
                    style={[
                      paper.typography.captionMeta,
                      {
                        color: selected ? paper.colors.brandInk : paper.colors.secondary,
                        fontWeight: selected ? '700' : '500',
                        position: 'absolute',
                        top: labelTop,
                        textAlign: 'center',
                      },
                    ]}
                  >
                    {minutes}
                  </Text>
                ) : null}
              </View>
            );
          })}
        </ScrollView>
        <View
          pointerEvents="none"
          style={{
            position: 'absolute',
            alignSelf: 'center',
            top: 6,
            alignItems: 'center',
          }}
        >
          <View
            style={{
              width: 6,
              height: 6,
              borderRadius: paper.radius.pill,
              backgroundColor: paper.colors.brand,
            }}
          />
          <View
            style={{
              width: 2,
              height: 52,
              borderRadius: paper.radius.tick,
              backgroundColor: paper.colors.brand,
            }}
          />
        </View>
      </View>

      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginTop: YanjiSpacing.sm,
          paddingHorizontal: YanjiSpacing.xs,
        }}
      >
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`减少${stepMinutes}分钟`}
          disabled={disabled || valueMinutes <= minMinutes}
          onPress={() => adjust(-1)}
          style={({ pressed }) => ({
            minWidth: 48,
            minHeight: 48,
            paddingHorizontal: YanjiSpacing.md,
            justifyContent: 'center',
            alignItems: 'center',
            borderRadius: paper.radius.pill,
            backgroundColor: paper.colors.subtle,
            borderWidth: paper.layout.hairlineWidth,
            borderColor: paper.colors.hairline,
            opacity: disabled || valueMinutes <= minMinutes ? 0.45 : pressed ? 0.7 : 1,
          })}
        >
          <Text style={[paper.typography.bodyPrimary, { fontWeight: '700', color: paper.colors.ink }]}>
            − {stepMinutes}m
          </Text>
        </Pressable>
        <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
          寸阴尺 · 每格 {stepMinutes} 分钟
        </Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`增加${stepMinutes}分钟`}
          disabled={disabled || valueMinutes >= maxMinutes}
          onPress={() => adjust(1)}
          style={({ pressed }) => ({
            minWidth: 48,
            minHeight: 48,
            paddingHorizontal: YanjiSpacing.md,
            justifyContent: 'center',
            alignItems: 'center',
            borderRadius: paper.radius.pill,
            backgroundColor: paper.colors.subtle,
            borderWidth: paper.layout.hairlineWidth,
            borderColor: paper.colors.hairline,
            opacity: disabled || valueMinutes >= maxMinutes ? 0.45 : pressed ? 0.7 : 1,
          })}
        >
          <Text style={[paper.typography.bodyPrimary, { fontWeight: '700', color: paper.colors.ink }]}>
            + {stepMinutes}m
          </Text>
        </Pressable>
      </View>
    </View>
  );
}
