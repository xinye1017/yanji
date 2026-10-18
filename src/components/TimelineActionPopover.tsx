/**
 * Anchored action menu for one timeline row — shared by Today and Review.
 *
 * It is deliberately not a sheet. The screen renders this as a full-screen
 * overlay above the list: the backdrop owns every touch outside the panel (so a
 * tap can never fall through to a record or another 「···」), Android back
 * closes it, and the panel is placed from the button's window rect — below by
 * default, flipped above when the bottom is tight, and clamped to the screen's
 * horizontal edges. Because it floats instead of living in the row, opening it
 * never changes the row's height or pushes the list around.
 */
import React, { Fragment, useEffect, useState } from 'react';
import { BackHandler, Pressable, StyleSheet, Text, View } from 'react-native';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { YanjiHairline, YanjiIcon } from './YanjiUI';
import type { YanjiIconName } from './icons';

/** The 「···」 button's rect in window coordinates. */
export interface PopoverAnchor {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** One command in the menu; `danger` separates destructive actions with a rule. */
export interface TimelineAction {
  key: string;
  icon: YanjiIconName;
  label: string;
  danger?: boolean;
  onPress: () => void;
}

interface TimelineActionPopoverProps {
  anchor: PopoverAnchor;
  /** Status-bar height: the overlay's coordinate origin sits below it. */
  topInset: number;
  /** Size of the overlay's parent (the screen root), in dp. */
  containerWidth: number;
  containerHeight: number;
  /** Space already owned at the bottom: the floating dock and nav bar. */
  bottomInset: number;
  disabled: boolean;
  actions: readonly TimelineAction[];
  onClose: () => void;
}

interface MenuRowProps {
  action: TimelineAction;
  disabled: boolean;
  onPress: () => void;
}

function MenuRow({ action, disabled, onPress }: MenuRowProps): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const danger = !!action.danger;
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={action.label}
      accessibilityState={{ disabled }}
      style={({ pressed }) => ({
        minHeight: layout.rowMinHeight,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: YanjiSpacing.md,
        paddingHorizontal: YanjiSpacing.md,
        borderRadius: radius.badge,
        backgroundColor: pressed ? colors.subtle : 'transparent',
        opacity: disabled ? 0.5 : 1,
      })}
    >
      <YanjiIcon name={action.icon} size={layout.momentIconSize} color={danger ? colors.danger : colors.secondary} />
      <Text style={[typography.bodyPrimary, { color: danger ? colors.danger : colors.ink }]}>
        {action.label}
      </Text>
    </Pressable>
  );
}

export function TimelineActionPopover({
  anchor,
  topInset,
  containerWidth,
  containerHeight,
  bottomInset,
  disabled,
  actions,
  onClose,
}: TimelineActionPopoverProps): React.JSX.Element {
  const { paper, isDark, shadow } = useYanjiTheme();
  const { colors, layout, radius } = paper;
  /** Intrinsic panel size once laid out: the menu grows with the system font. */
  const [panel, setPanel] = useState<{ width: number; height: number } | null>(null);

  // Every action closes first: the menu is a one-shot command surface, and the
  // caller's handler then runs against the persisted row rather than a menu
  // that is still on screen.
  const act = (action: () => void) => () => {
    if (disabled) return;
    onClose();
    action();
  };

  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => subscription.remove();
  }, [onClose]);

  const hasDanger = actions.some(action => action.danger);
  const padding = YanjiSpacing.xs;
  const gap = YanjiSpacing.xs;
  const edge = YanjiSpacing.sm;
  // Before the first layout pass the panel is positioned from this estimate; it
  // keeps the menu off-screen-edge math valid for the single frame it takes the
  // intrinsic width/height to arrive.
  const width = panel?.width ?? layout.momentMenuWidth;
  // Height follows the number of rows, so the flip decision is made before the
  // panel has been measured; width is the estimate above until onLayout lands.
  const height = panel?.height
    ?? padding * 2 + actions.length * layout.rowMinHeight + (hasDanger ? layout.hairlineWidth : 0);

  // The overlay starts at the screen root's origin, which is below the status
  // bar; the anchor is in window space, so shift it into root space first.
  const anchorX = anchor.x;
  const anchorY = anchor.y - topInset;

  const left = Math.max(edge, Math.min(anchorX + anchor.width - width, containerWidth - width - edge));
  // The dock floats above the screen root, so the usable bottom stops above it
  // rather than at the window edge.
  const safeBottom = containerHeight - bottomInset - edge;
  let top = anchorY + anchor.height + gap;
  if (top + height > safeBottom) {
    const above = anchorY - height - gap;
    top = above >= edge ? above : Math.max(edge, safeBottom - height);
  }

  let dividerPlaced = false;

  return (
    <View style={StyleSheet.absoluteFill} accessibilityViewIsModal>
      <Pressable
        style={StyleSheet.absoluteFill}
        onPress={onClose}
        accessibilityRole="button"
        accessibilityLabel="关闭记录操作菜单"
      />
      <View
        onLayout={event => {
          const { width: nextWidth, height: nextHeight } = event.nativeEvent.layout;
          setPanel(current => current && current.width === nextWidth && current.height === nextHeight
            ? current
            : { width: nextWidth, height: nextHeight });
        }}
        style={[
          {
            position: 'absolute',
            left,
            top,
            // No fixed width: the panel shrink-wraps its widest action row, and
            // each row centres its icon + label as one group.
            maxWidth: containerWidth - edge * 2,
            // One invisible layout frame: the panel is then placed from its real
            // size, so a large system font cannot clip the labels or the box.
            opacity: panel ? 1 : 0,
            backgroundColor: colors.surface,
            borderRadius: radius.surface,
            borderWidth: layout.hairlineWidth,
            borderColor: colors.hairline,
            paddingVertical: padding,
          },
          shadow.floating(isDark),
        ]}
      >
        {actions.map(action => {
          const divider = !!action.danger && !dividerPlaced;
          if (divider) dividerPlaced = true;
          return (
            <Fragment key={action.key}>
              {divider ? <YanjiHairline inset={YanjiSpacing.md} /> : null}
              <MenuRow action={action} disabled={disabled} onPress={act(action.onPress)} />
            </Fragment>
          );
        })}
      </View>
    </View>
  );
}
