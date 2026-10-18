import React from 'react';
import { Modal, Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { YanjiHairline, YanjiIcon } from './YanjiUI';

interface MomentActionSheetProps {
  visible: boolean;
  disabled: boolean;
  onClose: () => void;
  onEdit: () => void;
  /** Opens the existing editor's confirmation; never deletes directly. */
  onDelete: () => void;
}

export function MomentActionSheet({ visible, disabled, onClose, onEdit, onDelete }: MomentActionSheetProps): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  return (
    <Modal transparent visible={visible} animationType={reduceMotion ? 'none' : 'fade'} onRequestClose={onClose}>
      <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: colors.scrim, paddingTop: insets.top }}>
        <Pressable style={{ flex: 1 }} onPress={onClose} accessibilityRole="button" accessibilityLabel="关闭记录操作" />
        <View accessibilityViewIsModal style={{ flexShrink: 1, backgroundColor: colors.surface,
          borderTopLeftRadius: radius.sheet, borderTopRightRadius: radius.sheet,
          paddingHorizontal: layout.pageGutter, paddingTop: YanjiSpacing.md, paddingBottom: YanjiSpacing.sm + insets.bottom }}>
          <ScrollView style={{ flexGrow: 0 }} bounces={false}>
            <Text accessibilityRole="header" style={[typography.captionMeta, { color: colors.secondary, marginBottom: YanjiSpacing.xs }]}>记录操作</Text>
            <Pressable onPress={() => { onClose(); onEdit(); }} disabled={disabled}
              accessibilityRole="button" accessibilityLabel="编辑记录" accessibilityState={{ disabled }}
              style={({ pressed }) => ({ minHeight: layout.rowMinHeight, flexDirection: 'row', alignItems: 'center',
                gap: YanjiSpacing.md, paddingVertical: YanjiSpacing.md, borderRadius: radius.badge,
                backgroundColor: pressed ? colors.subtle : 'transparent', opacity: disabled ? 0.5 : 1 })}>
              <YanjiIcon name="compose" size={layout.momentIconSize} color={colors.secondary} />
              <Text style={[typography.bodyPrimary, { color: colors.ink, flex: 1 }]}>编辑记录</Text>
            </Pressable>
            <YanjiHairline />
            <Pressable onPress={() => { onClose(); onDelete(); }} disabled={disabled}
              accessibilityRole="button" accessibilityLabel="删除记录" accessibilityState={{ disabled }}
              style={({ pressed }) => ({ minHeight: layout.rowMinHeight, flexDirection: 'row', alignItems: 'center',
                gap: YanjiSpacing.md, paddingVertical: YanjiSpacing.md, borderRadius: radius.badge,
                backgroundColor: pressed ? colors.subtle : 'transparent', opacity: disabled ? 0.5 : 1 })}>
              <YanjiIcon name="delete" size={layout.momentIconSize} color={colors.danger} />
              <Text style={[typography.bodyPrimary, { color: colors.danger, flex: 1 }]}>删除记录</Text>
            </Pressable>
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}
