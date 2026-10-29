/**
 * Settings · 数据管理: manual export and staged, destructive restore.
 *
 * Preparing an import only stages and validates the chosen file. No Room write
 * happens until the user has seen the preview and explicitly confirms. The
 * section also owns cancellation cleanup so closing Settings cannot strand a
 * staged file.
 */

import React, { useEffect, useRef, useState } from 'react';
import { Modal, Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import {
  YanjiHairline,
  YanjiPaperSectionHeading,
  YanjiPaperSheetHeading,
  YanjiPrimaryButton,
} from './YanjiUI';
import { YanjiSettingRow } from './SettingsRows';
import { YanjiBackupNative } from '../bridge';
import type { BackupPreview } from '../bridge';
import { refreshSettingsSnapshot } from '../state/dataRefresh';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';

type Notice = { kind: 'success' | 'warning' | 'error'; message: string };
type Operation = 'export' | 'prepare' | 'restore' | 'discard' | null;

const COUNT_ROWS: Array<[keyof BackupPreview['counts'], string]> = [
  ['focusSessions', '专注记录'],
  ['examSessions', '模考记录'],
  ['noteEntries', '随笔'],
  ['chatSessions', '旧版聊天会话'],
  ['chatMessages', '旧版聊天消息'],
  ['checkIns', '打卡'],
  ['quickStartPresets', '快捷预设'],
  ['studyTasks', '学习任务'],
  ['subjects', '学科'],
  ['scheduleTemplates', '时间表模板'],
];

function errorMessage(error: unknown, fallback: string): string {
  return typeof error === 'object' && error !== null && 'message' in error &&
    typeof error.message === 'string' && error.message
    ? error.message
    : fallback;
}

function formatExportTime(epochMs: number): string {
  const date = new Date(epochMs);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function BackupDataSection(): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();

  const [operation, setOperation] = useState<Operation>(null);
  const [preview, setPreview] = useState<BackupPreview | null>(null);
  const [restoreError, setRestoreError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);

  const busyRef = useRef(false);
  const mountedRef = useRef(true);
  const previewRef = useRef<BackupPreview | null>(null);

  const discardStaged = async (token: string): Promise<boolean | null> => {
    try {
      return await YanjiBackupNative.discardBackupImport(token);
    } catch {
      return null;
    }
  };

  const releaseStaged = async (token: string) => {
    if (previewRef.current?.token !== token) return;
    const discarded = await discardStaged(token);
    if (discarded !== null && previewRef.current?.token === token) {
      previewRef.current = null;
    }
  };

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      if (previewRef.current) void releaseStaged(previewRef.current.token);
    };
  }, []);

  const beginOperation = (next: Exclude<Operation, null>): boolean => {
    if (busyRef.current) return false;
    busyRef.current = true;
    setOperation(next);
    setNotice(null);
    return true;
  };

  const endOperation = () => {
    busyRef.current = false;
    if (mountedRef.current) setOperation(null);
  };

  const handleExport = async () => {
    if (!beginOperation('export')) return;
    try {
      const result = await YanjiBackupNative.exportBackup();
      if (result && mountedRef.current) {
        setNotice({ kind: 'success', message: `备份已保存：${result.fileName}` });
      }
    } catch (error) {
      if (mountedRef.current) {
        setNotice({ kind: 'error', message: `导出失败：${errorMessage(error, '请稍后重试。')}` });
      }
    } finally {
      endOperation();
    }
  };

  const handlePrepare = async () => {
    if (!beginOperation('prepare')) return;
    try {
      const next = await YanjiBackupNative.prepareBackupImport();
      if (!next) return;
      if (!mountedRef.current) {
        await discardStaged(next.token);
        return;
      }
      previewRef.current = next;
      setPreview(next);
      setRestoreError(null);
    } catch (error) {
      if (mountedRef.current) {
        setNotice({ kind: 'error', message: `读取备份失败：${errorMessage(error, '请检查文件后重试。')}` });
      }
    } finally {
      endOperation();
    }
  };

  const handleCancelPreview = async () => {
    if (busyRef.current || !preview) return;
    const token = preview.token;
    busyRef.current = true;
    setOperation('discard');
    const discarded = await discardStaged(token);
    if (mountedRef.current) {
      if (discarded === null) setRestoreError('取消未完成，请重试。');
      else {
        previewRef.current = null;
        setPreview(null);
        setRestoreError(null);
        setNotice(null);
      }
    }
    endOperation();
  };

  const handleRestore = async () => {
    if (!preview || busyRef.current) return;
    busyRef.current = true;
    setOperation('restore');
    setRestoreError(null);
    setNotice(null);
    try {
      const result = await YanjiBackupNative.restoreBackupImport(preview.token);
      if (!mountedRef.current) return;

      previewRef.current = null;
      setPreview(null);
      try {
        await refreshSettingsSnapshot();
        if (mountedRef.current) {
          setNotice({
            kind: 'success',
            message: result.warnings.length > 0
              ? `备份已恢复。${result.warnings.join('；')}`
              : '备份已恢复。',
          });
        }
      } catch (error) {
        if (mountedRef.current) {
          setNotice({
            kind: 'warning',
            message: `恢复操作已提交，但设置刷新失败：${errorMessage(error, '请稍后重新进入设置。')}` +
              (result.warnings.length > 0 ? ` ${result.warnings.join('；')}` : ''),
          });
        }
      }
    } catch (error) {
      const code = typeof error === 'object' && error !== null && 'code' in error ? error.code : null;
      if (mountedRef.current && (code === 'E_BACKUP_RESTORE_UNKNOWN' || code === 'E_BACKUP_INTERRUPTED')) {
        previewRef.current = null;
        setPreview(null);
        setNotice({ kind: 'warning', message: errorMessage(error, '未能确认恢复结果，请先检查本机数据，再重新选择备份。') });
      } else if (mountedRef.current) setRestoreError(`恢复失败：${errorMessage(error, '请稍后重试。')}`);
      else await releaseStaged(preview.token);
    } finally {
      busyRef.current = false;
      if (mountedRef.current) setOperation(null);
    }
  };

  const countGrid = preview ? (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', marginTop: YanjiSpacing.sm }}>
      {COUNT_ROWS.map(([key, label]) => (
        <View
          key={key}
          style={{
            width: '50%',
            minHeight: layout.rowMinHeight,
            paddingVertical: YanjiSpacing.xs,
            paddingRight: YanjiSpacing.sm,
            justifyContent: 'center',
          }}
        >
          <Text style={[typography.captionMeta, { color: colors.secondary }]}>{label}</Text>
          <Text style={[typography.bodyPrimary, { color: colors.ink, fontWeight: '700', fontVariant: ['tabular-nums'] }]}>
            {preview.counts[key]}
          </Text>
        </View>
      ))}
    </View>
  ) : null;

  const noticeColor = notice?.kind === 'success'
    ? colors.positive
    : notice?.kind === 'warning'
      ? colors.amber
      : colors.danger;
  const previewBusy = operation === 'restore' || operation === 'discard';

  return (
    <View>
      <YanjiPaperSectionHeading
        title="数据管理"
        spaceBefore={layout.sectionGap}
        titleStyle={typography.titleGroup}
      />
      <Text style={[typography.captionMeta, { color: colors.secondary, marginBottom: YanjiSpacing.sm }]}>
        手动保存和迁移学习数据
      </Text>
      <YanjiSettingRow
        label="导出数据"
        value={operation === 'export' ? '导出中…' : '本地文件'}
        hint="将记录保存为本地备份文件"
        accessibilityLabel="导出数据，将记录保存为本地备份文件"
        disabled={operation !== null}
        onPress={() => void handleExport()}
      />
      <YanjiSettingRow
        label="从备份恢复"
        value={operation === 'prepare' ? '校验中…' : '选择文件'}
        hint="导入前校验文件与记录信息"
        accessibilityLabel="从备份恢复，导入前校验文件与记录信息"
        isLast
        disabled={operation !== null}
        onPress={() => void handlePrepare()}
      />

      {notice ? (
        <Text
          accessibilityRole="alert"
          accessibilityLiveRegion="polite"
          style={[typography.captionMeta, { color: noticeColor, marginTop: YanjiSpacing.sm }]}
        >
          {notice.message}
        </Text>
      ) : null}

      <Modal
        visible={preview !== null}
        transparent
        animationType={reduceMotion ? 'none' : 'slide'}
        onRequestClose={() => { if (!previewBusy) void handleCancelPreview(); }}
      >
        <View style={{ flex: 1, backgroundColor: colors.scrim, justifyContent: 'flex-end' }}>
          <Pressable
            style={{ flex: 1 }}
            onPress={() => void handleCancelPreview()}
            disabled={previewBusy}
            accessibilityState={{ disabled: previewBusy }}
            accessibilityLabel="关闭"
          />
          {preview ? (
            <ScrollView
              showsVerticalScrollIndicator={false}
              overScrollMode="never"
              style={{
                flexGrow: 0,
                maxHeight: '88%',
                backgroundColor: colors.surface,
                borderTopLeftRadius: radius.sheet,
                borderTopRightRadius: radius.sheet,
              }}
              contentContainerStyle={{
                paddingHorizontal: layout.pageGutter,
                paddingTop: YanjiSpacing.md,
                paddingBottom: YanjiSpacing.lg + insets.bottom,
              }}
            >
              <View
                style={{
                  alignSelf: 'center',
                  width: 36,
                  height: 4,
                  borderRadius: radius.pill,
                  backgroundColor: colors.hairline,
                  marginBottom: YanjiSpacing.lg,
                }}
              />
              <YanjiPaperSheetHeading
                title="恢复学习数据？"
                subtitle="恢复会覆盖或清空本机记录，确认前不会写入。"
                onClose={() => void handleCancelPreview()}
                disabled={previewBusy}
              />

              <View style={{ marginTop: YanjiSpacing.lg }}>
                <Text style={[typography.bodyPrimary, { color: colors.ink, fontWeight: '700' }]}>
                  {preview.fileName}
                </Text>
                <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.xs }]}>
                  格式版本 {preview.schemaVersion}  应用版本 {preview.appVersionName}  导出时间 
                  {formatExportTime(preview.exportedAt)}
                </Text>
              </View>

              <YanjiHairline style={{ marginTop: YanjiSpacing.md }} />
              {countGrid}
              <YanjiHairline style={{ marginTop: YanjiSpacing.md }} />

              <View style={{ marginTop: YanjiSpacing.md, gap: YanjiSpacing.sm }}>
                <Text style={[typography.bodyPrimary, { color: colors.danger, fontWeight: '700' }]}>
                  会替换或清空：专注记录、模考记录、随笔、学习任务、打卡、旧版聊天和快捷预设。
                  备份中为空的类别会清空本机现有数据。
                </Text>
                <Text style={[typography.captionMeta, { color: colors.secondary }]}>
                  {preview.replacesSettings ? '设置将按备份替换。' : '设置不会改变。'}
                  {preview.replacesSubjects ? '学科将按备份替换。' : '学科保持不变。'}
                </Text>
                <Text style={[typography.captionMeta, { color: colors.secondary }]}>
                  API 密钥等凭据不会写入备份，也不会覆盖本机凭据。
                </Text>
                <Text style={[typography.captionMeta, { color: colors.secondary }]}>
                  恢复前必须结束当前专注或模考，包括已暂停的会话。
                </Text>
              </View>

              {preview.warnings.length > 0 ? (
                <View
                  style={{
                    marginTop: YanjiSpacing.md,
                    padding: YanjiSpacing.sm,
                    borderRadius: radius.surface,
                    backgroundColor: colors.amberSubtle,
                  }}
                >
                  {preview.warnings.map((warning, index) => (
                    <Text
                      key={`${warning}-${index}`}
                      style={[typography.captionMeta, { color: colors.amber }]}
                    >
                      {warning}
                    </Text>
                  ))}
                </View>
              ) : null}

              {restoreError ? (
                <Text
                  accessibilityRole="alert"
                  style={[typography.captionMeta, { color: colors.danger, marginTop: YanjiSpacing.md }]}
                >
                  {restoreError}
                </Text>
              ) : null}

              <View style={{ flexDirection: 'row', gap: YanjiSpacing.md, marginTop: YanjiSpacing.lg }}>
                <YanjiPrimaryButton
                  label={operation === 'discard' ? '取消中…' : '取消'}
                  onPress={() => void handleCancelPreview()}
                  disabled={previewBusy}
                  variant="secondary"
                  style={{ flex: 1 }}
                />
                <YanjiPrimaryButton
                  label={operation === 'restore' ? '恢复中…' : '确认覆盖并恢复'}
                  onPress={() => void handleRestore()}
                  disabled={previewBusy}
                  variant="danger"
                  style={{ flex: 1 }}
                />
              </View>
            </ScrollView>
          ) : null}
        </View>
      </Modal>
    </View>
  );
}
