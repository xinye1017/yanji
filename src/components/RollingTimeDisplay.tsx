/**
 * RollingTimeDisplay — Digit flip / ticker effect for Focus countdown dial.
 *
 * Each changed digit flips vertically within its slot bounds:
 * - When increasing (sliding right): old digit moves down and exits, new digit enters from above.
 * - When decreasing (sliding left): old digit moves up and exits, new digit enters from below.
 * Unchanged digits stay stable.
 */
import React, { useEffect, useRef, useState } from 'react';
import {
  Text,
  View,
  useWindowDimensions,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useYanjiTheme } from '../theme/ThemeProvider';

/**
 * The dial's own font-scale window. It is the page's one oversized element, so
 * it follows the user's scale only within these bounds rather than without
 * limit; `RollingTimeDisplay` applies the clamp itself and renders every digit
 * with `allowFontScaling={false}` so the platform cannot apply a second one.
 */
const MAX_DISPLAY_SCALE = 1.15;
const MIN_DISPLAY_SCALE = 0.85;

interface DigitSlotProps {
  digit: string;
  direction: 1 | -1;
  height: number;
  textStyle: StyleProp<TextStyle>;
  color: string;
  reducedMotion?: boolean;
}

export function DigitSlot({
  digit,
  direction,
  height,
  textStyle,
  color,
  reducedMotion = false,
}: DigitSlotProps): React.JSX.Element {
  const [currentDigit, setCurrentDigit] = useState(digit);
  const [outgoingDigit, setOutgoingDigit] = useState<string | null>(null);

  const progress = useSharedValue(1);
  const dirValue = useSharedValue<1 | -1>(direction);

  useEffect(() => {
    if (digit === currentDigit) return;

    if (reducedMotion) {
      setCurrentDigit(digit);
      setOutgoingDigit(null);
      progress.value = 1;
      return;
    }

    dirValue.value = direction;
    setOutgoingDigit(currentDigit);
    setCurrentDigit(digit);
    progress.value = 0;
    progress.value = withTiming(1, {
      duration: 160,
      easing: Easing.out(Easing.cubic),
    });

    const timer = setTimeout(() => {
      setOutgoingDigit(null);
    }, 180);

    return () => clearTimeout(timer);
  }, [digit, direction, currentDigit, progress, dirValue, reducedMotion]);

  const incomingStyle = useAnimatedStyle(() => {
    'worklet';
    if (progress.value >= 1) {
      return {
        transform: [{ translateY: 0 }],
        opacity: 1,
      };
    }
    // direction = 1 (increasing, e.g. sliding right): enters from top (-height)
    // direction = -1 (decreasing, e.g. sliding left): enters from bottom (+height)
    const startY = -dirValue.value * height;
    const translateY = startY * (1 - progress.value);
    return {
      transform: [{ translateY }],
      opacity: progress.value,
    };
  });

  const outgoingStyle = useAnimatedStyle(() => {
    'worklet';
    if (progress.value >= 1) {
      return {
        transform: [{ translateY: dirValue.value * height }],
        opacity: 0,
      };
    }
    // direction = 1 (increasing, e.g. sliding right): exits to bottom (+height)
    // direction = -1 (decreasing, e.g. sliding left): exits to top (-height)
    const endY = dirValue.value * height;
    const translateY = endY * progress.value;
    return {
      transform: [{ translateY }],
      opacity: 1 - progress.value,
    };
  });

  return (
    <View
      style={{
        height,
        overflow: 'hidden',
        justifyContent: 'center',
        alignItems: 'center',
      }}
    >
      {outgoingDigit !== null ? (
        <Animated.View style={[{ position: 'absolute' }, outgoingStyle]}>
          <Text allowFontScaling={false} style={[textStyle, { color, textAlign: 'center' }]}>
            {outgoingDigit}
          </Text>
        </Animated.View>
      ) : null}
      <Animated.View style={incomingStyle}>
        <Text allowFontScaling={false} style={[textStyle, { color, textAlign: 'center' }]}>
          {currentDigit}
        </Text>
      </Animated.View>
    </View>
  );
}

interface RollingTimeDisplayProps {
  valueMinutes: number;
  color?: string;
  style?: StyleProp<ViewStyle>;
  testID?: string;
}

export function RollingTimeDisplay({
  valueMinutes,
  color,
  style,
  testID = 'focus-rolling-display',
}: RollingTimeDisplayProps): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const inkColor = color ?? paper.colors.ink;
  const reducedMotion = useReducedMotion();
  const { fontScale } = useWindowDimensions();

  const prevMinutes = useRef(valueMinutes);
  const lastDirection = useRef<1 | -1>(1);

  if (valueMinutes !== prevMinutes.current) {
    lastDirection.current = valueMinutes > prevMinutes.current ? 1 : -1;
  }
  const direction = lastDirection.current;

  useEffect(() => {
    prevMinutes.current = valueMinutes;
  }, [valueMinutes]);

  const safeMinutes = Math.max(0, Math.floor(valueMinutes));
  const hundreds = safeMinutes >= 100 ? String(Math.floor(safeMinutes / 100)) : null;
  const tens = String(Math.floor((safeMinutes % 100) / 10));
  const units = String(safeMinutes % 10);

  // This component scales the dial itself, so the nested `Text` must not scale
  // it a second time: with the platform's own scaling left on, the slot below is
  // capped at `MAX_DISPLAY_SCALE` while the glyphs render at the full system
  // scale, and the fixed-height `overflow: 'hidden'` slot clips them.
  // `maxFontSizeMultiplier` is not the fix — Android applies it to `fontSize`
  // but not to `lineHeight` (TextAttributeProps.kt scales the two through
  // different calls), so the line box would keep growing past the ceiling. The
  // display is clamped deliberately: it is the page's one oversized element, and
  // past this scale its glyphs push the subject row off a short viewport. Every
  // `Text` under this component therefore renders at the scale measured here.
  const effectiveScale = Math.min(MAX_DISPLAY_SCALE, Math.max(MIN_DISPLAY_SCALE, fontScale));
  const fontSize = Math.round(paper.typography.displayDial.fontSize * effectiveScale);
  const slotHeight = Math.ceil(paper.typography.displayDial.lineHeight * effectiveScale);

  const textStyle: TextStyle = {
    ...paper.typography.displayDial,
    fontSize,
    lineHeight: slotHeight,
    color: inkColor,
    includeFontPadding: false,
  };

  // The unit sits *under* the digits' baseline rather than beside them: 「84 分」
  // on one line made the block wider than the ruler beneath it, and dropping the
  // unit to the lower right keeps the number itself the widest thing on the page.
  const unitSize = Math.round(24 * effectiveScale);
  const unitStyle: TextStyle = {
    ...paper.typography.titleSection,
    fontSize: unitSize,
    lineHeight: Math.ceil(unitSize * 1.15),
    fontWeight: '500',
    color: paper.colors.secondary,
    includeFontPadding: false,
  };

  return (
    <View
      testID={testID}
      accessible
      accessibilityRole="text"
      accessibilityLabel={`专注时长 ${safeMinutes} 分钟`}
      style={[
        {
          flexDirection: 'row',
          // The digits stay centred in their slot; the unit is anchored to the
          // baseline edge below, so it hangs off the number's lower right corner.
          alignItems: 'flex-end',
          justifyContent: 'center',
          height: slotHeight,
        },
        style,
      ]}
    >
      {hundreds !== null ? (
        <DigitSlot
          key="hundreds"
          digit={hundreds}
          direction={direction}
          height={slotHeight}
          textStyle={textStyle}
          color={inkColor}
          reducedMotion={reducedMotion}
        />
      ) : null}
      <DigitSlot
        key="tens"
        digit={tens}
        direction={direction}
        height={slotHeight}
        textStyle={textStyle}
        color={inkColor}
        reducedMotion={reducedMotion}
      />
      <DigitSlot
        key="units"
        digit={units}
        direction={direction}
        height={slotHeight}
        textStyle={textStyle}
        color={inkColor}
        reducedMotion={reducedMotion}
      />
      {/* Sits in the corner the digits leave empty: the number's baseline is the
          bottom of its line box, and the unit is a small label tucked just under
          the last stroke rather than a second word beside it. */}
      <View style={{ justifyContent: 'flex-end', marginLeft: Math.round(2 * effectiveScale) }}>
        <Text allowFontScaling={false} style={[unitStyle, { marginBottom: Math.round(slotHeight * 0.04) }]}>分</Text>
      </View>
    </View>
  );
}
