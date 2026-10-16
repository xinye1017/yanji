/**
 * The Paper & Chrono vernier, borderless edition.
 *
 * A real, horizontally scrollable 5-minute scale with no outer card frame: the
 * strip is full-bleed and only the ticks plus a central vermilion needle mark
 * the value. Visual 1-minute subdivisions are deliberately absent — native
 * Focus accepts 5-minute steps and no unrelated business value should be
 * synthesized.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ScrollView,
  Text,
  View,
  type NativeSyntheticEvent,
  type NativeScrollEvent,
} from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';

const TICK_WIDTH = 14;
const RULER_HEIGHT = 34;

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
  const offsetFor = useCallback(
    (v: number) =>
      Math.max(0, Math.min(ticks.length - 1, (v - minMinutes) / stepMinutes)) *
      TICK_WIDTH,
    [minMinutes, stepMinutes, ticks.length],
  );

  // A task's original plannedMinutes may not be divisible by five. Merely
  // mounting this ruler must never round it or write a new planned duration.
  useEffect(() => {
    if (width <= 0 || userDragging.current || ticks.length === 0) return;
    list.current?.scrollTo({ x: offsetFor(valueMinutes), animated: false });
  }, [width, valueMinutes, ticks.length, offsetFor]);

  const selectIndex = useCallback(
    (index: number) => {
      if (disabled || ticks.length === 0) return;
      const next = ticks[Math.max(0, Math.min(ticks.length - 1, index))];
      if (next !== valueMinutes) onChangeMinutes(next);
    },
    [disabled, onChangeMinutes, ticks, valueMinutes],
  );

  const selectOffset = useCallback(
    (e: NativeSyntheticEvent<NativeScrollEvent>) => {
      if (!userDragging.current) return;
      userDragging.current = false;
      selectIndex(Math.round(e.nativeEvent.contentOffset.x / TICK_WIDTH));
    },
    [selectIndex],
  );

  const adjust = useCallback(
    (direction: -1 | 1) => {
      if (disabled || ticks.length === 0) return;
      // From a non-step task preset, increase/decrease moves to the *next*
      // valid mark in that direction, never silently rounds on mount.
      const raw = (valueMinutes - minMinutes) / stepMinutes;
      const index =
        direction === 1
          ? Math.floor(raw + 0.000001) + 1
          : Math.ceil(raw - 0.000001) - 1;
      const target = Math.max(0, Math.min(ticks.length - 1, index));
      selectIndex(target);
      list.current?.scrollTo({ x: target * TICK_WIDTH, animated: !reducedMotion });
    },
    [disabled, minMinutes, reducedMotion, selectIndex, stepMinutes, ticks.length, valueMinutes],
  );

  const horizontalInset = Math.max(0, (width - TICK_WIDTH) / 2);

  return (
    <View testID="focus-vernier">
      <View
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={accessibilityLabel}
        accessibilityValue={{
          min: minMinutes,
          max: maxMinutes,
          now: valueMinutes,
          text: `${valueMinutes} 分钟`,
        }}
        accessibilityActions={[
          { name: 'increment', label: `增加${stepMinutes}分钟` },
          { name: 'decrement', label: `减少${stepMinutes}分钟` },
        ]}
        onAccessibilityAction={event => {
          if (event.nativeEvent.actionName === 'increment') adjust(1);
          if (event.nativeEvent.actionName === 'decrement') adjust(-1);
        }}
        onLayout={e => setWidth(e.nativeEvent.layout.width)}
        style={{ height: RULER_HEIGHT, justifyContent: 'flex-end' }}
      >
        <ScrollView
          ref={list}
          testID="focus-vernier-ruler"
          horizontal
          scrollEnabled={!disabled}
          showsHorizontalScrollIndicator={false}
          snapToInterval={TICK_WIDTH}
          snapToAlignment="start"
          decelerationRate={reducedMotion ? 'normal' : 'fast'}
          onScrollBeginDrag={() => {
            userDragging.current = true;
          }}
          onScrollEndDrag={e => {
            if (Math.abs(e.nativeEvent.velocity?.x ?? 0) < 0.1) selectOffset(e);
          }}
          onMomentumScrollEnd={selectOffset}
          contentContainerStyle={{
            paddingHorizontal: horizontalInset,
            alignItems: 'flex-end',
            height: RULER_HEIGHT,
          }}
        >
          {ticks.map(minutes => {
            // No numeric labels: the giant numeral above the strip is the
            // readout. Only the tick height carries the 15-minute accent.
            const major = minutes % 15 === 0;
            return (
              <View
                key={minutes}
                style={{
                  width: TICK_WIDTH,
                  height: RULER_HEIGHT,
                  alignItems: 'center',
                  justifyContent: 'flex-end',
                }}
              >
                <View
                  style={{
                    width: 1.5,
                    height: major ? 20 : 10,
                    borderRadius: paper.radius.tick,
                    backgroundColor: major
                      ? paper.colors.tickMajor
                      : paper.colors.tickMinor,
                  }}
                />
              </View>
            );
          })}
        </ScrollView>

        <View
          pointerEvents="none"
          style={{
            position: 'absolute',
            alignSelf: 'center',
            bottom: 0,
            width: 2.5,
            height: 28,
            borderRadius: paper.radius.tick,
            backgroundColor: paper.colors.brand,
          }}
        />
      </View>

      <Text
        style={[
          paper.typography.captionMeta,
          { color: paper.colors.secondary, textAlign: 'center', marginTop: YanjiSpacing.sm },
        ]}
      >
        ← 左右滑动刻度尺微调（{minMinutes}–{maxMinutes} 分钟）→
      </Text>
    </View>
  );
}
