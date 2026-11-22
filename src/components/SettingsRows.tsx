/**
 * Settings rows — the two shapes every option on the settings page is drawn as.
 *
 * Why they live here and not in `YanjiUI.tsx`: the shared file is the vocabulary
 * for the three primary screens, while these two are the settings page's own
 * grammar (a label / value / chevron row and its sheet). Keeping them in one
 * module is what stops the page from growing a third and fourth row layout.
 *
 * Both rows are full-width, hairline-separated and read as one ledger — the
 * page deliberately has no card around them: hierarchy comes from type scale,
 * whitespace and the 1dp hairline, not from stacked card outlines (see the
 * Paper & Chrono domain in `src/theme/tokens.ts`).
 */

import React from 'react';
import { ActivityIndicator, Pressable, Text, View, useWindowDimensions } from 'react-native';
import { YanjiIcon, YanjiHairline, YanjiSkeleton, YanjiSkeletonText } from './YanjiUI';
import type { YanjiIconName } from './icons';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';

/**
 * One settings line: `label` on the left, the current `value` and a chevron on
 * the right. Tapping it opens the sheet that owns that value.
 *
 * `pending` is what the row shows while the settings snapshot is still in
 * flight: claiming 「未设置」 before the read returns would be a statement about
 * stored data the page has not seen yet. The value slot is then a wireframe
 * bone where the value will sit — not a 「—」 and a spinner — so the row keeps
 * its shape and the value lands in place.
 */
export function YanjiSettingRow({
  label,
  value,
  hint,
  accessibilityLabel,
  isLast = false,
  pending = false,
  busy = false,
  disabled = false,
  onPress,
}: {
  label: string;
  /** The stored value as the page reports it; `未设置` when nothing is stored. */
  value?: string;
  /** Optional second line under the label — only when the value needs a reason. */
  hint?: string;
  accessibilityLabel?: string;
  isLast?: boolean;
  pending?: boolean;
  busy?: boolean;
  disabled?: boolean;
  onPress: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout } = paper;
  const { width, fontScale } = useWindowDimensions();
  const stacked = width / fontScale < layout.settingsStackedWidth;
  const unset = pending || value === '未设置';
  const shown = pending ? undefined : value;
  // Silent: the row's own label already says 「正在读取」.
  const valueBone = (
    <YanjiSkeleton>
      <YanjiSkeletonText type={typography.bodyPrimary} width={72} />
    </YanjiSkeleton>
  );
  // A value the row expects to show; actions such as 「时间表设置」 have none.
  const hasValueSlot = pending || Boolean(shown);

  return (
    <View>
      <Pressable
        onPress={onPress}
        disabled={disabled || pending || busy}
        accessibilityRole="button"
        accessibilityLabel={pending ? `${label}，正在读取` : accessibilityLabel ?? [label, shown, hint].filter(Boolean).join('，')}
        accessibilityState={{ disabled: disabled || pending || busy, busy: pending || busy }}
        style={({ pressed }) => ({
          minHeight: layout.rowMinHeight,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: YanjiSpacing.md,
          paddingVertical: YanjiSpacing.sm,
          opacity: disabled || pending || busy ? 0.6 : pressed ? 0.62 : 1,
        })}
      >
        <View style={{ flex: 1, gap: YanjiSpacing.xs }}>
          <Text style={[typography.bodyPrimary, { color: colors.ink }]}>{label}</Text>
          {stacked && hasValueSlot ? (pending ? valueBone : (
            <Text style={[typography.bodyPrimary, { color: unset ? colors.secondary : colors.ink, fontWeight: '600' }]}>
              {shown}
            </Text>
          )) : null}
          {hint ? (
            <Text style={[typography.captionMeta, { color: colors.secondary }]}>
              {hint}
            </Text>
          ) : null}
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs, flexShrink: 1 }}>
          {!stacked && pending ? valueBone : !stacked && shown ? <Text
            style={[
              typography.bodyPrimary,
              {
                color: unset ? colors.secondary : colors.ink,
                fontWeight: unset ? '500' : '600',
                fontVariant: ['tabular-nums'],
                flexShrink: 1,
              },
            ]}
          >
            {shown}
          </Text> : null}
          {busy ? <ActivityIndicator size="small" color={colors.secondary} /> : <YanjiIcon name="forward" size={15} color={colors.secondary} />}
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
  disabled = false,
  busy = false,
  pending = false,
  isLast = false,
  onPress,
}: {
  label: string;
  summary: string;
  icon?: YanjiIconName;
  expanded: boolean;
  disabled?: boolean;
  busy?: boolean;
  /** The summary is still being read: draw a bone where it goes. */
  pending?: boolean;
  isLast?: boolean;
  onPress: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout } = paper;
  const { width, fontScale } = useWindowDimensions();
  const stacked = width / fontScale < layout.settingsStackedWidth;
  const summaryNode = pending ? (
    <YanjiSkeleton>
      <YanjiSkeletonText type={typography.captionMeta} width={44} />
    </YanjiSkeleton>
  ) : null;

  return (
    <View>
      <Pressable
        onPress={onPress}
        disabled={disabled || busy}
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityHint={pending ? '正在读取' : summary}
        accessibilityState={{ expanded, disabled: disabled || busy, busy }}
        style={({ pressed }) => ({
          minHeight: layout.rowMinHeight,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: YanjiSpacing.md,
          paddingVertical: YanjiSpacing.sm,
          opacity: disabled ? 0.6 : pressed ? 0.62 : 1,
        })}
      >
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm, flex: 1 }}>
          {icon ? <YanjiIcon name={icon} size={16} color={colors.secondary} /> : null}
          <View style={{ flex: 1, gap: YanjiSpacing.xs }}>
            <Text style={[typography.bodyPrimary, { color: colors.ink }]}>{label}</Text>
            {stacked ? summaryNode ?? <Text style={[typography.captionMeta, { color: colors.secondary }]}>{summary}</Text> : null}
          </View>
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs, flexShrink: 1 }}>
          {!stacked ? summaryNode ?? <Text style={[typography.captionMeta, { color: colors.secondary, flexShrink: 1 }]}>
            {summary}
          </Text> : null}
          {busy ? <ActivityIndicator size="small" color={colors.secondary} /> : <YanjiIcon name={expanded ? 'chevronUp' : 'chevronDown'} size={15} color={colors.secondary} />}
        </View>
      </Pressable>
      {isLast ? null : <YanjiHairline />}
    </View>
  );
}
