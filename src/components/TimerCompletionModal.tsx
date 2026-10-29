import React, { useEffect, useState } from 'react';
import { Modal, ScrollView, Text, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { onTimerCompleted, YanjiTimerNative } from '../bridge';
import type { TimerCompletion } from '../bridge';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { YanjiPrimaryButton } from './YanjiUI';

export function completionDuration(seconds: number): string {
  const total = Math.max(0, Math.floor(seconds));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor(total % 3600 / 60);
  const remainder = total % 60;
  return `${hours ? `${hours} 小时 ` : ''}${minutes ? `${minutes} 分钟 ` : ''}${remainder || total === 0 ? `${remainder} 秒` : ''}`.trim();
}

/** Mounted at the app root so completing on Today or in settings also produces a result. */
export function TimerCompletionModal({ foreground }: { foreground: boolean }): React.JSX.Element | null {
  const { paper } = useYanjiTheme();
  const reducedMotion = useReducedMotion();
  const [completion, setCompletion] = useState<TimerCompletion | null>(null);
  const [dismissing, setDismissing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    let receivedEvent = false;
    const unsubscribe = onTimerCompleted(next => {
      receivedEvent = true;
      setCompletion(next);
      setError(null);
    });
    YanjiTimerNative.getTimerCompletion().then(next => {
      if (active && !receivedEvent) setCompletion(next);
    }).catch(() => {});
    return () => { active = false; unsubscribe(); };
  }, []);

  const dismiss = async () => {
    if (!completion || dismissing) return;
    const id = completion.sessionId;
    setDismissing(true);
    try {
      await YanjiTimerNative.acknowledgeTimerCompletion(id);
      setCompletion(current => current?.sessionId === id ? null : current);
      setError(null);
    } catch { setError('暂时无法关闭，请重试'); }
    finally { setDismissing(false); }
  };

  if (!foreground || !completion) return null;
  return (
    <Modal transparent visible animationType={reducedMotion ? 'none' : 'fade'} onRequestClose={() => void dismiss()}>
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', padding: paper.layout.pageGutter, backgroundColor: paper.colors.scrim }}>
        <View accessibilityViewIsModal style={{ width: '100%', maxWidth: paper.layout.completionDialogMaxWidth, maxHeight: '85%', backgroundColor: paper.colors.surface, borderRadius: paper.radius.sheet, padding: YanjiSpacing.xl }}>
          <ScrollView showsVerticalScrollIndicator={false}>
            <Text accessibilityRole="header" style={[paper.typography.titleSection, { color: paper.colors.ink, textAlign: 'center' }]}>已计时完成</Text>
            <Text style={[paper.typography.bodyPrimary, { color: paper.colors.secondary, textAlign: 'center', marginTop: YanjiSpacing.lg }]}>{completion.subjectName}</Text>
            <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, textAlign: 'center', marginTop: YanjiSpacing.md }]}>{completion.isExam ? '本次模考时长' : '本次专注时长'}</Text>
            <Text style={[paper.typography.titleGroup, { color: paper.colors.ink, textAlign: 'center', marginTop: YanjiSpacing.xs }]}>{completionDuration(completion.elapsedSeconds)}</Text>
            {error ? <Text accessibilityRole="alert" style={[paper.typography.captionMeta, { color: paper.colors.danger, marginTop: YanjiSpacing.md }]}>{error}</Text> : null}
            <View style={{ marginTop: YanjiSpacing.xl }}><YanjiPrimaryButton label="完成" onPress={() => void dismiss()} disabled={dismissing} /></View>
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}
