/**
 * Settings rows — the two shapes every option on the settings page is drawn as.
 *
 * Why they live here and not in `YanjiUI.tsx`: the shared file is the vocabulary
 * for the three primary screens, while these two are the settings page's own
 * grammar (a label / value / chevron row and its sheet). Keeping them in one
 * module is what stops the page from growing a third and fourth row layout.
 *
 * Both rows are full-width, hairline-separated and read as one ledger — the
 * page deliberately has no card around them (see DESIGN.md §去卡片化).
 */

import React from 'react';
import { Pressable, Text, View } from 'react-native';
import { YanjiIcon, YanjiHairline } from './YanjiUI';
import type { YanjiIconName } from './icons';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';

/**
 * One settings line: `label` on the left, the current `value` and a chevron on
 * the right. Tapping it opens the sheet that owns that value.
 *
 * `pending` is what the row shows while the settings snapshot is still in
 * flight: claiming 「未设置」 before the read returns would be a statement about
 * stored data the page has not seen yet.
 */
export function YanjiSettingRow({
  label,
  value,
  hint,
  accessibilityLabel,
  isLast = false,
  pending = false,
  disabled = false,
  onPress,
}: {
  label: string;
  /** The stored value as the page reports it; `未设置` when nothing is stored. */
  value: string;
  /** Optional second line under the label — only when the value needs a reason. */
  hint?: string;
  accessibilityLabel?: string;
  isLast?: boolean;
  pending?: boolean;
  disabled?: boolean;
  onPress: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout } = paper;
  const unset = pending || value === '未设置';
  const shown = pending ? '—' : value;

  return (
    <View>
      <Pressable
        onPress={onPress}
        disabled={disabled || pending}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel ?? `${label}，${shown}`}
        accessibilityState={{ disabled: disabled || pending }}
        style={({ pressed }) => ({
          minHeight: layout.rowMinHeight,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: YanjiSpacing.md,
          paddingVertical: YanjiSpacing.sm,
          opacity: disabled || pending ? 0.45 : pressed ? 0.62 : 1,
        })}
      >
        <View style={{ flexShrink: 1 }}>
          <Text style={[typography.bodyPrimary, { color: colors.ink }]}>{label}</Text>
          {hint ? (
            <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: 2 }]}>
              {hint}
            </Text>
          ) : null}
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs, flexShrink: 1 }}>
          <Text
            numberOfLines={1}
            style={[
              typography.bodyPrimary,
              {
                color: unset ? colors.secondary : colors.ink,
                fontWeight: unset ? '500' : '700',
                fontVariant: ['tabular-nums'],
                flexShrink: 1,
              },
            ]}
          >
            {shown}
          </Text>
          <YanjiIcon name="forward" size={15} color={colors.secondary} />
        </View>
      </Pressable>
      {isLast ? null : <YanjiHairline />}
    </View>
  );
}

/**
 * A disclosure line that expands in place. Used for the one advanced block on
 * the page, so a rarely touched form never occupies the first screen.
 */
export function YanjiSettingDisclosureRow({
  label,
  summary,
  icon,
  expanded,
  isLast = false,
  onPress,
}: {
  label: string;
  summary: string;
  icon?: YanjiIconName;
  expanded: boolean;
  isLast?: boolean;
  onPress: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout } = paper;

  return (
    <View>
      <Pressable
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityHint={summary}
        accessibilityState={{ expanded }}
        style={({ pressed }) => ({
          minHeight: layout.rowMinHeight,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: YanjiSpacing.md,
          paddingVertical: YanjiSpacing.sm,
          opacity: pressed ? 0.62 : 1,
        })}
      >
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm, flexShrink: 1 }}>
          {icon ? <YanjiIcon name={icon} size={16} color={colors.secondary} /> : null}
          <Text style={[typography.bodyPrimary, { color: colors.ink }]}>{label}</Text>
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs, flexShrink: 1 }}>
          <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.secondary, flexShrink: 1 }]}>
            {summary}
          </Text>
          <YanjiIcon name={expanded ? 'chevronUp' : 'chevronDown'} size={15} color={colors.secondary} />
        </View>
      </Pressable>
      {isLast ? null : <YanjiHairline />}
    </View>
  );
}
