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
  PixelRatio,
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

interface FocusVernierDialProps {
  valueMinutes: number;
  onChangeMinutes: (value: number) => void;
  onSettleMinutes?: (value: number) => void;
  minMinutes: number;
  maxMinutes: number;
  stepMinutes: number;
  disabled?: boolean;
  accessibilityLabel?: string;
}

export function FocusVernierDial({
  valueMinutes,
  onChangeMinutes,
  onSettleMinutes,
  minMinutes,
  maxMinutes,
  stepMinutes,
  disabled = false,
  accessibilityLabel = '选择专注时长',
}: FocusVernierDialProps): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { fontScale } = useWindowDimensions();
  const reducedMotion = useReducedMotion();
  // Android truncates snapToInterval to physical pixels; align cells to the same grid.
  const tickWidth = PixelRatio.roundToNearestPixel(
    paper.layout.vernierTickWidth * Math.max(1, fontScale),
  );
  const labelHeight = Math.ceil(paper.typography.captionMeta.lineHeight * fontScale);
  const labelTop = YanjiSpacing.lg + paper.layout.vernierPointerHeight + YanjiSpacing.sm;
  const rulerHeight = Math.max(
    paper.layout.vernierMinHeight,
    labelTop + labelHeight + YanjiSpacing.lg,
  );
  const list = useRef<React.ElementRef<typeof ScrollView>>(null);
  const userDragging = useRef(false);
  const settleTimeout = useRef<any>(null);
  const [width, setWidth] = useState(0);

  useEffect(() => {
    return () => {
      if (settleTimeout.current) clearTimeout(settleTimeout.current);
    };
  }, []);

  const ticks = useMemo(() => {
    const values: number[] = [];
    if (stepMinutes <= 0 || maxMinutes < minMinutes) return values;
    for (let v = minMinutes; v <= maxMinutes; v += stepMinutes) values.push(v);
    return values;
  }, [minMinutes, maxMinutes, stepMinutes]);

  // Fractional offsets preserve non-grid task presets (e.g. 37 minutes)
  // exactly between the 35 and 40 marks. ScrollView's symmetric padding
  // already centres x = index * tickWidth; adding that padding to the
  // offset would move the mark away from the pointer.
  const offsetFor = useCallback(
    (v: number) =>
      Math.max(0, Math.min(ticks.length - 1, (v - minMinutes) / stepMinutes)) *
      tickWidth,
    [minMinutes, stepMinutes, ticks.length, tickWidth],
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

  const lastEmittedValue = useRef(valueMinutes);
  useEffect(() => {
    lastEmittedValue.current = valueMinutes;
  }, [valueMinutes]);

  const selectOffset = useCallback(
    (e: NativeSyntheticEvent<NativeScrollEvent>) => {
      if (!userDragging.current) return;
      userDragging.current = false;
      if (settleTimeout.current) clearTimeout(settleTimeout.current);
      const index = Math.round(e.nativeEvent.contentOffset.x / tickWidth);
      selectIndex(index);
      const target = ticks[Math.max(0, Math.min(ticks.length - 1, index))];
      if (target !== undefined && onSettleMinutes) {
        onSettleMinutes(target);
      }
    },
    [onSettleMinutes, selectIndex, tickWidth, ticks],
  );

  const handleScroll = useCallback(
    (e: NativeSyntheticEvent<NativeScrollEvent>) => {
      if (!userDragging.current || disabled || ticks.length === 0) return;
      const index = Math.round(e.nativeEvent.contentOffset.x / tickWidth);
      const target = ticks[Math.max(0, Math.min(ticks.length - 1, index))];
      if (target !== undefined && target !== lastEmittedValue.current) {
        lastEmittedValue.current = target;
        onChangeMinutes(target);
      }
      if (onSettleMinutes && target !== undefined) {
        if (settleTimeout.current) clearTimeout(settleTimeout.current);
        settleTimeout.current = setTimeout(() => {
          if (userDragging.current) {
            userDragging.current = false;
            onSettleMinutes(target);
          }
        }, 150);
        if (typeof (settleTimeout.current as any)?.unref === 'function') {
          (settleTimeout.current as any).unref();
        }
      }
    },
    [disabled, onChangeMinutes, onSettleMinutes, tickWidth, ticks],
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
      const next = ticks[target];
      if (next !== undefined && onSettleMinutes) {
        onSettleMinutes(next);
      }
      list.current?.scrollTo({ x: target * tickWidth, animated: !reducedMotion });
    },
    [disabled, minMinutes, onSettleMinutes, reducedMotion, selectIndex, stepMinutes, ticks, tickWidth, valueMinutes],
  );

  const horizontalInset = Math.max(0, (width - tickWidth) / 2);

  return (
    <View testID="focus-vernier">
      <View
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={accessibilityLabel}
        accessibilityState={{ disabled }}
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
        style={{ height: rulerHeight }}
      >
        <ScrollView
          ref={list}
          testID="focus-vernier-ruler"
          accessible={false}
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          horizontal
          scrollEnabled={!disabled}
          showsHorizontalScrollIndicator={false}
          snapToInterval={tickWidth}
          snapToAlignment="start"
          decelerationRate={reducedMotion ? 'normal' : 'fast'}
          scrollEventThrottle={16}
          onScroll={handleScroll}
          onScrollBeginDrag={() => {
            userDragging.current = true;
          }}
          onScrollEndDrag={e => {
            if (Math.abs(e.nativeEvent.velocity?.x ?? 0) < 0.1) selectOffset(e);
          }}
          onMomentumScrollBegin={() => {
            userDragging.current = true;
          }}
          onMomentumScrollEnd={selectOffset}
          contentContainerStyle={{
            paddingHorizontal: horizontalInset,
            height: rulerHeight,
          }}
        >
          {ticks.map(minutes => {
            const major = minutes % 15 === 0;
            return (
              <View
                key={minutes}
                style={{
                  width: tickWidth,
                  height: rulerHeight,
                  alignItems: 'center',
                }}
              >
                <View
                  style={{
                    position: 'absolute',
                    bottom: rulerHeight - labelTop + YanjiSpacing.sm,
                    width: 1.5,
                    height: major
                      ? paper.layout.vernierMajorTickHeight
                      : paper.layout.vernierMinorTickHeight,
                    borderRadius: paper.radius.tick,
                    backgroundColor: major
                      ? paper.colors.tickMajor
                      : paper.colors.tickMinor,
                  }}
                />
                {major ? (
                  <Text
                    numberOfLines={1}
                    style={[
                      paper.typography.captionMeta,
                      {
                        position: 'absolute',
                        top: labelTop,
                        width: tickWidth * 3,
                        textAlign: 'center',
                        color: paper.colors.secondary,
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
            bottom: rulerHeight - labelTop + YanjiSpacing.sm,
            width: 2.5,
            height: paper.layout.vernierPointerHeight,
            borderRadius: paper.radius.tick,
            backgroundColor: paper.colors.brand,
          }}
        />
      </View>
    </View>
  );
}
