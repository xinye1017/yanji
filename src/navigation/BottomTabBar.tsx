/**
 * Yanji bottom tab bar — exactly three destinations.
 *
 * Visual language: a floating glass lens over the real page, with the selected
 * destination marked by a soft accent pill that slides between slots on a
 * spring. Lifecycle: the bar is mounted for the whole session next to the three
 * always-mounted screens (see App.tsx), so `activeTab` — never a remount — is
 * what drives the selected state.
 */

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { LayoutChangeEvent, PanResponder, Pressable, StyleSheet, Text, View } from 'react-native';
import BlurOverlay from 'react-native-blur-overlay';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  useReducedMotion,
  withSpring,
} from 'react-native-reanimated';
import { useNavigationActions, useNavigationState, TABS } from './NavigationShell';
import type { TabKey } from './NavigationShell';
import { YanjiIcon } from '../components/YanjiUI';
import type { YanjiIconName } from '../components/icons';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiTouch } from '../theme/tokens';
import { YanjiLiquidGlass } from '../theme/liquidGlass';
import { useBottomTabLayout } from './useBottomTabLayout';

/** Tab glyph, kept here rather than in the shared registry: it is navigation-local. */
const TAB_ICONS: Record<TabKey, YanjiIconName> = {
  today: 'today',
  focus: 'focus',
  review: 'review',
};

/** Shared capsule inset keeps the selection aligned with the measured slots. */
const CAPSULE_PADDING = YanjiLiquidGlass.padding;

/**
 * Calculates the pill's animated offset while dragging, keeping the pill
 * centered under the finger and clamped within the valid tab slots [0, tabsCount - 1].
 */
export function calculateTabDragOffset(slotAreaX: number, slotWidth: number, tabsCount: number): number {
  if (slotWidth <= 0 || tabsCount <= 0) return 0;
  const target = (slotAreaX - slotWidth / 2) / slotWidth;
  return Math.max(0, Math.min(tabsCount - 1, target));
}

/**
 * Maps the finger's horizontal coordinate within the slot area to a valid tab slot index [0, tabsCount - 1].
 */
export function calculateTargetSlotIndex(slotAreaX: number, slotWidth: number, tabsCount: number): number {
  if (slotWidth <= 0 || tabsCount <= 0) return 0;
  const slot = Math.floor(slotAreaX / slotWidth);
  return Math.max(0, Math.min(tabsCount - 1, slot));
}

export interface PillDeformation {
  translateX: number;
  scaleX: number;
  scaleY: number;
}

/**
 * Calculates physical deformation (trailing stretch & vertical squish) based on
 * leading and trailing positions, with hard wall collision squash on the outer bounds.
 *
 * When inside the interior:
 *   The leading edge pulls forward and trailing edge lags behind, stretching the pill
 *   along the horizontal direction and slightly squishing vertically.
 * When colliding with the outer walls (left wall at 0 or right wall at tabsCount * slotWidth):
 *   The visual edge is strictly pinned flush against the wall (0px overflow), while the
 *   momentum squashes the pill horizontally (scaleX < 1) and bulges it vertically (scaleY > 1).
 * When at rest:
 *   scaleX = 1, scaleY = 1, translateX = lead * slotWidth.
 */
export function calculatePillDeformation(
  lead: number,
  trail: number,
  slotWidth: number,
  tabsCount: number = 3
): PillDeformation {
  'worklet';
  if (slotWidth <= 0 || tabsCount <= 0) {
    return { translateX: 0, scaleX: 1, scaleY: 1 };
  }

  const wallRight = tabsCount * slotWidth;

  // 1. Unconstrained fluid motion & trailing stretch
  const diff = lead - trail;
  const rawSpan = 1 + Math.abs(diff);
  const clampedSpan = Math.max(0.90, Math.min(1.22, rawSpan));
  const centerSlot = (lead + trail) / 2;

  const rawScaleX = clampedSpan;
  const rawScaleY = Math.max(0.92, 1 - (clampedSpan - 1) * 0.35);
  const rawTranslateX = centerSlot * slotWidth;

  // 2. Measure visual edges against physical boundaries [0, wallRight]
  const halfScaledWidth = (slotWidth * rawScaleX) / 2;
  const visualLeft = rawTranslateX + slotWidth / 2 - halfScaledWidth;
  const visualRight = rawTranslateX + slotWidth / 2 + halfScaledWidth;

  // 3. Wall Collision & Squash on Impact (subtle, non-exaggerated physical feel)
  if (visualLeft < 0) {
    // Colliding with Left Wall
    const overshoot = -visualLeft;
    const compression = Math.min(0.12, (overshoot / slotWidth) * 0.45);
    const squashedScaleX = Math.max(0.88, 1 - compression);
    const squashedScaleY = Math.min(1.03, 1 + compression * 0.22);

    // Pin visual left edge to 0 (hard boundary, 0px overflow)
    const newHalfWidth = (slotWidth * squashedScaleX) / 2;
    const clampedTranslateX = newHalfWidth - slotWidth / 2;

    return {
      translateX: clampedTranslateX,
      scaleX: squashedScaleX,
      scaleY: squashedScaleY,
    };
  }

  if (visualRight > wallRight) {
    // Colliding with Right Wall
    const overshoot = visualRight - wallRight;
    const compression = Math.min(0.12, (overshoot / slotWidth) * 0.45);
    const squashedScaleX = Math.max(0.88, 1 - compression);
    const squashedScaleY = Math.min(1.03, 1 + compression * 0.22);

    // Pin visual right edge to wallRight (hard boundary, 0px overflow)
    const newHalfWidth = (slotWidth * squashedScaleX) / 2;
    const clampedTranslateX = wallRight - slotWidth / 2 - newHalfWidth;

    return {
      translateX: clampedTranslateX,
      scaleX: squashedScaleX,
      scaleY: squashedScaleY,
    };
  }

  // Completely within boundaries
  return {
    translateX: rawTranslateX,
    scaleX: rawScaleX,
    scaleY: rawScaleY,
  };
}

export function BottomTabBar({ visible = true }: { visible?: boolean }): React.JSX.Element {
  const theme = useYanjiTheme();
  const { paper } = theme;
  const { height, bottom } = useBottomTabLayout();
  const reduceMotion = useReducedMotion();
  const material = theme.isDark ? YanjiLiquidGlass.dark : YanjiLiquidGlass.light;
  const { activeTab } = useNavigationState();
  const { selectTab } = useNavigationActions();

  const [slotWidth, setSlotWidth] = useState(0);
  const index = Math.max(0, TABS.findIndex(tab => tab.key === activeTab));

  const isDraggingRef = useRef(false);
  const hasMovedRef = useRef(false);
  const rowRef = useRef<React.ElementRef<typeof View>>(null);
  const rowPageXRef = useRef<number>(theme.spacing.lg);
  const slotWidthRef = useRef(0);
  const activeSlotRef = useRef(index);
  const selectTabRef = useRef(selectTab);
  const reduceMotionRef = useRef(reduceMotion);

  const snappyMotion = theme.motion.spring.snappy;
  const leadSpring = useMemo(() => ({
    ...snappyMotion,
    damping: 13,
    stiffness: 280,
    mass: 0.7,
  }), [snappyMotion]);

  const trailTapSpring = useMemo(() => ({
    ...snappyMotion,
    damping: 14,
    stiffness: 210,
    mass: 0.85,
  }), [snappyMotion]);

  const trailDragSpring = useMemo(() => ({
    ...snappyMotion,
    damping: 15,
    stiffness: 180,
    mass: 0.65,
  }), [snappyMotion]);

  const leadSpringRef = useRef(leadSpring);
  const trailTapSpringRef = useRef(trailTapSpring);
  const trailDragSpringRef = useRef(trailDragSpring);

  useEffect(() => {
    leadSpringRef.current = leadSpring;
    trailTapSpringRef.current = trailTapSpring;
    trailDragSpringRef.current = trailDragSpring;
  }, [leadSpring, trailTapSpring, trailDragSpring]);

  useEffect(() => {
    activeSlotRef.current = index;
  }, [index]);

  useEffect(() => {
    selectTabRef.current = selectTab;
    reduceMotionRef.current = reduceMotion;
  }, [selectTab, reduceMotion]);

  /**
   * The pill slides by measured pixels rather than by a percentage of its own
   * width: Yoga resolves an absolutely-positioned child's percentage width
   * against the parent's inner box while its `left` inset is measured from the
   * padding edge, so `width: 33.33%` + `translateX: 100%` is not guaranteed to
   * land on a tab slot. Measuring once removes the ambiguity.
   */
  const onRowLayout = (event: LayoutChangeEvent) => {
    const rowWidth = event.nativeEvent.layout.width;
    const next = (rowWidth - CAPSULE_PADDING * 2) / TABS.length;
    if (next > 0) {
      slotWidthRef.current = next;
      if (Math.abs(next - slotWidth) > 0.5) {
        setSlotWidth(next);
      }
    }
    rowRef.current?.measure((_x: number, _y: number, _w: number, _h: number, pageX: number) => {
      if (typeof pageX === 'number' && !isNaN(pageX)) {
        rowPageXRef.current = pageX;
      }
    });
  };

  const lead = useSharedValue(index);
  const trail = useSharedValue(index);

  useEffect(() => {
    if (!isDraggingRef.current) {
      if (reduceMotion) {
        lead.value = index;
        trail.value = index;
      } else {
        lead.value = withSpring(index, leadSpring);
        trail.value = withSpring(index, trailTapSpring);
      }
    }
  }, [index, lead, trail, reduceMotion, leadSpring, trailTapSpring]);

  const panResponder = useMemo(() => {
    const getSlotAreaX = (screenX: number) => {
      return screenX - rowPageXRef.current - CAPSULE_PADDING;
    };

    return PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: () => {
        hasMovedRef.current = false;
        isDraggingRef.current = false;
      },
      onPanResponderMove: (_evt, gestureState) => {
        const sw = slotWidthRef.current;
        if (sw <= 0) return;

        const dx = gestureState.dx;
        if (!hasMovedRef.current && Math.abs(dx) > 4) {
          hasMovedRef.current = true;
          isDraggingRef.current = true;
        }

        if (isDraggingRef.current) {
          const currentX = gestureState.moveX || (gestureState.x0 + gestureState.dx);
          const slotAreaX = getSlotAreaX(currentX);
          const clampedOffset = calculateTabDragOffset(slotAreaX, sw, TABS.length);
          lead.value = clampedOffset;
          trail.value = withSpring(clampedOffset, trailDragSpringRef.current);

          const targetSlot = calculateTargetSlotIndex(slotAreaX, sw, TABS.length);
          if (targetSlot !== activeSlotRef.current) {
            activeSlotRef.current = targetSlot;
            selectTabRef.current(TABS[targetSlot].key);
          }
        }
      },
      onPanResponderRelease: (_evt, gestureState) => {
        const sw = slotWidthRef.current;
        const wasDragging = isDraggingRef.current;
        isDraggingRef.current = false;
        hasMovedRef.current = false;

        if (sw <= 0) return;

        const currentX = wasDragging
          ? (gestureState.moveX || (gestureState.x0 + gestureState.dx))
          : gestureState.x0;
        const slotAreaX = getSlotAreaX(currentX);
        const finalSlot = calculateTargetSlotIndex(slotAreaX, sw, TABS.length);
        activeSlotRef.current = finalSlot;
        selectTabRef.current(TABS[finalSlot].key);

        if (reduceMotionRef.current) {
          lead.value = finalSlot;
          trail.value = finalSlot;
        } else {
          lead.value = withSpring(finalSlot, leadSpringRef.current);
          trail.value = withSpring(finalSlot, trailTapSpringRef.current);
        }
      },
      onPanResponderTerminate: () => {
        isDraggingRef.current = false;
        hasMovedRef.current = false;
        if (reduceMotionRef.current) {
          lead.value = activeSlotRef.current;
          trail.value = activeSlotRef.current;
        } else {
          lead.value = withSpring(activeSlotRef.current, leadSpringRef.current);
          trail.value = withSpring(activeSlotRef.current, trailTapSpringRef.current);
        }
      },
    });
  }, [lead, trail]);

  const pillStyle = useAnimatedStyle(() => {
    'worklet';
    const deformation = calculatePillDeformation(lead.value, trail.value, slotWidth, TABS.length);
    return {
      transform: [
        { translateX: deformation.translateX },
        { scaleX: deformation.scaleX },
        { scaleY: deformation.scaleY },
      ],
    };
  });

  const contentHeight = height - 2 * paper.layout.hairlineWidth;

  return (
    <View
      testID="bottom-tab-bar"
      pointerEvents={visible ? 'auto' : 'none'}
      accessibilityElementsHidden={!visible}
      importantForAccessibility={visible ? 'auto' : 'no-hide-descendants'}
      style={[{
        position: 'absolute',
        left: theme.spacing.lg,
        right: theme.spacing.lg,
        bottom,
        height,
        borderRadius: height / 2,
        backgroundColor: material.backdrop,
        borderWidth: paper.layout.hairlineWidth,
        borderColor: material.border,
        justifyContent: 'center',
        ...theme.shadow.floating(theme.isDark),
      }, { opacity: visible ? 1 : 0 }]}
    >
      <BlurOverlay
        visible={visible}
        blurMode="glass"
        blurTargetId={YanjiLiquidGlass.targetId}
        glassVariant="clear"
        glassTint={material.tint}
        blurRadius={YanjiLiquidGlass.blurRadius}
        downsampling={YanjiLiquidGlass.downsampling}
        maxUpdateFps={YanjiLiquidGlass.maxUpdateFps}
        saturation={YanjiLiquidGlass.saturation}
        interactive={!reduceMotion}
        fadeDuration={0}
        style={[{ ...StyleSheet.absoluteFill, borderRadius: height / 2, overflow: 'hidden' }]}
      >
        <View
          ref={rowRef}
          onLayout={onRowLayout}
          {...panResponder.panHandlers}
          style={{
            flexDirection: 'row',
            width: '100%',
            height: contentHeight,
            borderRadius: YanjiRadius.full,
            padding: CAPSULE_PADDING,
            overflow: 'hidden',
          }}
        >
          {/* Sliding selection pill — behind the labels, one slot wide. */}
          {slotWidth > 0 ? (
            <Animated.View
              pointerEvents="none"
              style={[
                {
                  position: 'absolute',
                  top: CAPSULE_PADDING,
                  bottom: CAPSULE_PADDING,
                  left: CAPSULE_PADDING,
                  width: slotWidth,
                  borderRadius: YanjiRadius.full,
                  backgroundColor: material.selection,
                },
                pillStyle,
              ]}
            />
          ) : null}

          {TABS.map((tab, tabIndex) => {
            const isActive = tabIndex === index;
            const foreground = isActive && !theme.isDark
              ? paper.colors.brandInk
              : (isActive ? paper.colors.brand : paper.colors.ink);
            return (
              <Pressable
                key={tab.key}
                testID={`tab-${tab.key}`}
                onPress={() => selectTab(tab.key as TabKey)}
                accessibilityRole="tab"
                accessibilityState={{ selected: isActive }}
                accessibilityLabel={tab.label}
                style={{
                  flex: 1,
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  paddingVertical: YanjiLiquidGlass.itemPadding,
                  paddingHorizontal: CAPSULE_PADDING,
                  borderRadius: YanjiRadius.full,
                  minHeight: YanjiTouch.min,
                }}
              >
                <YanjiIcon
                  name={TAB_ICONS[tab.key]}
                  size={YanjiLiquidGlass.iconSize}
                  color={foreground}
                />
                <Text
                  style={{
                    fontSize: YanjiLiquidGlass.labelSize,
                    lineHeight: YanjiLiquidGlass.labelLineHeight,
                    marginTop: YanjiLiquidGlass.labelGap,
                    fontWeight: isActive ? '700' : '500',
                    color: foreground,
                    letterSpacing: 0.2,
                  }}
                >
                  {tab.label}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </BlurOverlay>
    </View>
  );
}
