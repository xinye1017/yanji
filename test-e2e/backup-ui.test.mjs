import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

const tokens = loadTypeScript('src/theme/tokens.ts');
const theme = {
  isDark: false,
  mode: 'SYSTEM',
  paper: {
    colors: tokens.YanjiPaperColors.light,
    typography: tokens.YanjiPaperTypography,
    layout: tokens.YanjiPaperLayout,
    radius: tokens.YanjiPaperRadius,
    motion: tokens.YanjiPaperMotion,
  },
  shadow: { sheet: () => ({}), floating: () => ({}) },
  motion: tokens.YanjiMotion,
};

function nodes(tree, predicate) {
  const found = [];
  function walk(node) {
    if (Array.isArray(node)) {
      node.forEach(walk);
      return;
    }
    if (!node || typeof node !== 'object' || !('type' in node)) return;
    if (predicate(node)) found.push(node);
    if (typeof node.type === 'function') {
      walk(node.type(node.props));
      return;
    }
    walk(node.props?.children);
  }
  walk(tree);
  return found;
}

function text(tree) {
  if (Array.isArray(tree)) return tree.map(text).join('');
  if (tree && typeof tree === 'object' && 'type' in tree) {
    if (typeof tree.type === 'function') return text(tree.type(tree.props));
    const props = tree.props ?? {};
    const visibleProps = ['label', 'value', 'hint', 'title', 'subtitle']
      .map(key => typeof props[key] === 'string' ? props[key] : '')
      .filter(Boolean)
      .join(' ');
    return `${visibleProps}${text(props.children)}`;
  }
  return typeof tree === 'string' || typeof tree === 'number' ? String(tree) : '';
}

const plain = value => JSON.parse(JSON.stringify(value));
const flush = () => new Promise(resolve => setImmediate(resolve));

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function backupPreview(changes = {}) {
  return {
    token: 'stage-token',
    fileName: 'yanji-learning-backup.json',
    schemaVersion: 3,
    exportedAt: new Date(2026, 9, 17, 9, 5).getTime(),
    appVersionName: '2.0.0',
    counts: {
      focusSessions: 11,
      examSessions: 2,
      noteEntries: 7,
      chatSessions: 3,
      chatMessages: 14,
      checkIns: 6,
      quickStartPresets: 5,
      studyTasks: 9,
      subjects: 8,
    },
    replacesSettings: false,
    replacesSubjects: false,
    warnings: ['有 2 条旧记录将被清空'],
    ...changes,
  };
}

function backupHarness({
  exportBackup = async () => ({ fileName: 'yanji-backup.json' }),
  prepareBackupImport = async () => backupPreview(),
  restoreBackupImport = async () => ({ warnings: [] }),
  discardBackupImport = async () => true,
  refreshSettingsSnapshot = async () => {},
} = {}) {
  const calls = [];
  const states = [];
  const refs = [];
  const effects = [];
  const cleanups = [];
  let hookIndex = 0;
  let refIndex = 0;
  let exportBackupImpl = exportBackup;
  let prepareBackupImportImpl = prepareBackupImport;
  let restoreBackupImportImpl = restoreBackupImport;
  let discardBackupImportImpl = discardBackupImport;
  let refreshSettingsSnapshotImpl = refreshSettingsSnapshot;

  const react = {
    useState: initial => {
      const index = hookIndex++;
      if (!(index in states)) {
        states[index] = typeof initial === 'function' ? initial() : initial;
      }
      return [states[index], next => {
        states[index] = typeof next === 'function' ? next(states[index]) : next;
      }];
    },
    useRef: initial => {
      const index = refIndex++;
      if (!(index in refs)) refs[index] = { current: initial };
      return refs[index];
    },
    useEffect: (effect, deps) => {
      const index = hookIndex++;
      const previous = effects[index];
      const unchanged = previous?.deps && deps &&
        previous.deps.length === deps.length &&
        deps.every((value, itemIndex) => Object.is(value, previous.deps[itemIndex]));
      if (!unchanged) effects[index] = { deps, effect, cleanup: previous?.cleanup };
    },
  };

  const bridge = {
    YanjiBackupNative: {
      exportBackup: async () => {
        calls.push(['exportBackup']);
        return exportBackupImpl();
      },
      prepareBackupImport: async () => {
        calls.push(['prepareBackupImport']);
        return prepareBackupImportImpl();
      },
      restoreBackupImport: async token => {
        calls.push(['restoreBackupImport', token]);
        return restoreBackupImportImpl(token);
      },
      discardBackupImport: async token => {
        calls.push(['discardBackupImport', token]);
        return discardBackupImportImpl(token);
      },
    },
  };

  const { BackupDataSection } = loadTypeScript('src/components/BackupDataSection.tsx', {
    react,
    'react-native': {
      Modal: 'Modal',
      Pressable: 'Pressable',
      ScrollView: 'ScrollView',
      Text: 'Text',
      View: 'View',
    },
    'react-native-safe-area-context': {
      useSafeAreaInsets: () => ({ bottom: 0, top: 0 }),
    },
    'react-native-reanimated': { useReducedMotion: () => true },
    '../bridge': bridge,
    './YanjiUI': {
      YanjiHairline: 'YanjiHairline',
      YanjiPaperSectionHeading: 'YanjiPaperSectionHeading',
      YanjiPaperSheetHeading: 'YanjiPaperSheetHeading',
      YanjiPrimaryButton: 'YanjiPrimaryButton',
    },
    './SettingsRows': { YanjiSettingRow: 'YanjiSettingRow' },
    '../state/dataRefresh': {
      refreshSettingsSnapshot: async () => {
        calls.push(['refreshSettingsSnapshot']);
        return refreshSettingsSnapshotImpl();
      },
    },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../theme/tokens': tokens,
  });

  function render() {
    let tree;
    for (let pass = 0; pass < 8; pass++) {
      hookIndex = 0;
      refIndex = 0;
      tree = BackupDataSection();
      const pending = effects.filter(effect => effect && !effect.ran);
      if (pending.length === 0) break;
      for (const effect of pending) {
        effect.ran = true;
        const cleanup = effect.effect();
        effect.cleanup = cleanup;
        if (typeof cleanup === 'function') cleanups.push(cleanup);
      }
    }
    return tree;
  }

  return {
    calls,
    render,
    unmount: () => {
      while (cleanups.length > 0) cleanups.pop()();
    },
    setExportBackup: next => { exportBackupImpl = next; },
    setPrepareBackupImport: next => { prepareBackupImportImpl = next; },
    setRestoreBackupImport: next => { restoreBackupImportImpl = next; },
    setRefreshSettingsSnapshot: next => { refreshSettingsSnapshotImpl = next; },
  };
}

const rowByLabel = (tree, label) => nodes(tree, node =>
  node.type === 'YanjiSettingRow' && node.props.label === label)[0];
const modal = tree => nodes(tree, node => node.type === 'Modal')[0];
const buttonByLabel = (tree, label) => nodes(tree, node =>
  node.type === 'YanjiPrimaryButton' && node.props.label === label)[0];
async function openPreview(harness) {
  rowByLabel(harness.render(), '从备份恢复').props.onPress();
  await flush();
  assert.equal(modal(harness.render()).props.visible, true, 'native staging opens the preview');
}

test('the backup section renders its purpose and two data-management rows', () => {
  const harness = backupHarness();
  const tree = harness.render();

  const heading = nodes(tree, node => node.type === 'YanjiPaperSectionHeading')[0];
  assert.equal(heading.props.title, '数据管理');
  assert.deepEqual(plain(heading.props.titleStyle), plain(theme.paper.typography.titleGroup));
  assert.match(text(tree), /手动保存和迁移学习数据/);

  const rows = nodes(tree, node => node.type === 'YanjiSettingRow');
  assert.deepEqual(rows.map(row => row.props.label), ['导出数据', '从备份恢复']);
  assert.equal(rows[0].props.hint, '将记录保存为本地备份文件');
  assert.equal(rows[1].props.hint, '先预览内容，确认后覆盖本机记录');
});

test('export stays pending until native confirms a saved file and shares one request', async () => {
  const write = deferred();
  const harness = backupHarness({ exportBackup: () => write.promise });
  const row = () => rowByLabel(harness.render(), '导出数据');

  const first = row().props.onPress();
  const second = row().props.onPress();
  await flush();

  assert.equal(harness.calls.filter(call => call[0] === 'exportBackup').length, 1);
  assert.equal(row().props.disabled, true);
  assert.equal(rowByLabel(harness.render(), '从备份恢复').props.disabled, true);
  assert.equal(row().props.busy, true);
  assert.equal(row().props.label, '导出数据');
  assert.match(row().props.hint, /正在保存/);
  assert.equal(rowByLabel(harness.render(), '从备份恢复').props.busy, false);
  assert.doesNotMatch(text(harness.render()), /备份已保存/);

  write.resolve({ fileName: 'manual-backup.json' });
  await flush();

  assert.equal(row().props.disabled, false);
  assert.match(text(harness.render()), /备份已保存：manual-backup\.json/);
});

test('cancelling the native file picker is neutral rather than a success or failure', async () => {
  const harness = backupHarness({ exportBackup: async () => null });
  rowByLabel(harness.render(), '导出数据').props.onPress();
  await flush();

  const rendered = text(harness.render());
  assert.doesNotMatch(rendered, /备份已保存|导出失败/);
});

test('cancelling the import picker keeps the preview closed and reports nothing', async () => {
  const harness = backupHarness({ prepareBackupImport: async () => null });
  rowByLabel(harness.render(), '从备份恢复').props.onPress();
  await flush();

  const rendered = text(harness.render());
  assert.equal(modal(harness.render()).props.visible, false);
  assert.doesNotMatch(rendered, /读取备份失败|备份已恢复/);
  assert.equal(harness.calls.filter(call => call[0] === 'restoreBackupImport').length, 0);
  assert.equal(harness.calls.filter(call => call[0] === 'discardBackupImport').length, 0);
});

test('prepare only opens a preview after staging succeeds and never restores by itself', async () => {
  const prepare = deferred();
  const harness = backupHarness({ prepareBackupImport: () => prepare.promise });
  const importRow = () => rowByLabel(harness.render(), '从备份恢复');

  const first = importRow().props.onPress();
  const second = importRow().props.onPress();
  await flush();
  assert.equal(harness.calls.filter(call => call[0] === 'prepareBackupImport').length, 1);
  assert.equal(modal(harness.render()).props.visible, false);
  assert.equal(rowByLabel(harness.render(), '导出数据').props.busy, false);
  assert.equal(importRow().props.busy, true);
  assert.match(importRow().props.hint, /正在校验/);

  prepare.resolve(backupPreview());
  await flush();

  const opened = harness.render();
  assert.equal(modal(opened).props.visible, true);
  assert.equal(harness.calls.filter(call => call[0] === 'restoreBackupImport').length, 0);
  const rendered = text(opened);
  assert.match(rendered, /yanji-learning-backup\.json/);
  assert.match(rendered, /格式版本 3/);
  assert.match(rendered, /应用版本 2\.0\.0/);
  assert.match(rendered, /2026-10-17 09:05/);
  // Nine categories remain after the achievement system was removed.
  for (const count of ['11', '2', '7', '3', '14', '6', '5', '9', '8']) {
    assert.match(rendered, new RegExp(`\\b${count}\\b`));
  }
  assert.ok(buttonByLabel(opened, '确认覆盖并恢复'));
});

test('the preview states every replacement and conditional preservation exactly', async () => {
  const harness = backupHarness({
    prepareBackupImport: async () => backupPreview({
      replacesSettings: false,
      replacesSubjects: false,
    }),
  });
  await openPreview(harness);
  const rendered = text(harness.render());
  assert.match(rendered, /专注记录、模考记录、随笔、学习任务、打卡、旧版聊天和快捷预设/);
  assert.match(rendered, /备份中为空的类别会清空本机现有数据/);
  assert.match(rendered, /设置不会改变/);
  assert.match(rendered, /学科保持不变/);
  assert.match(rendered, /API 密钥等凭据不会写入备份，也不会覆盖本机凭据/);
  assert.match(rendered, /必须结束当前专注或模考，包括已暂停的会话/);
  assert.match(rendered, /有 2 条旧记录将被清空/);
});

test('the preview says settings and subjects are replaced only when the native flags allow it', async () => {
  const harness = backupHarness({
    prepareBackupImport: async () => backupPreview({
      replacesSettings: true,
      replacesSubjects: true,
    }),
  });
  await openPreview(harness);
  const rendered = text(harness.render());
  assert.match(rendered, /设置将按备份替换/);
  assert.match(rendered, /学科将按备份替换/);
  assert.doesNotMatch(rendered, /设置不会改变|学科保持不变/);
});

test('a failed restore keeps the preview and allows an explicit retry', async () => {
  const restore = deferred();
  const harness = backupHarness({ restoreBackupImport: () => restore.promise });
  await openPreview(harness);

  const confirm = () => buttonByLabel(harness.render(), '确认覆盖并恢复');
  const confirmation = confirm();
  confirmation.props.onPress();
  confirmation.props.onPress();
  await flush();

  assert.equal(harness.calls.filter(call => call[0] === 'restoreBackupImport').length, 1);
  assert.equal(modal(harness.render()).props.visible, true);
  assert.equal(modal(harness.render()).props.onRequestClose(), undefined);
  assert.equal(buttonByLabel(harness.render(), '取消').props.disabled, true);
  assert.equal(buttonByLabel(harness.render(), '恢复中…').props.disabled, true);

  restore.reject(new Error('disk write failed'));
  await flush();

  const failed = harness.render();
  assert.equal(modal(failed).props.visible, true);
  assert.match(text(failed), /恢复失败.*disk write failed/);
  assert.equal(buttonByLabel(failed, '确认覆盖并恢复').props.disabled, false);

  const retry = deferred();
  harness.setRestoreBackupImport(() => retry.promise);
  buttonByLabel(failed, '确认覆盖并恢复').props.onPress();
  await flush();
  retry.resolve({ warnings: [] });
  await flush();

  assert.equal(modal(harness.render()).props.visible, false);
  assert.match(text(harness.render()), /备份已恢复/);
});

test('restore success is reported only after the settings refresh succeeds', async () => {
  const refresh = deferred();
  const harness = backupHarness({
    restoreBackupImport: async () => ({ warnings: [] }),
    refreshSettingsSnapshot: () => refresh.promise,
  });
  await openPreview(harness);

  const attempt = buttonByLabel(harness.render(), '确认覆盖并恢复').props.onPress();
  await flush();
  assert.doesNotMatch(text(harness.render()), /备份已恢复/);

  refresh.resolve();
  await flush();

  assert.equal(harness.calls.filter(call => call[0] === 'refreshSettingsSnapshot').length, 1);
  assert.match(text(harness.render()), /备份已恢复/);
});

test('a committed restore with a failed settings refresh shows a warning without success copy', async () => {
  const harness = backupHarness({
    restoreBackupImport: async () => ({ warnings: ['旧聊天记录已迁移'] }),
    refreshSettingsSnapshot: async () => { throw new Error('settings read failed'); },
  });
  await openPreview(harness);
  buttonByLabel(harness.render(), '确认覆盖并恢复').props.onPress();
  await flush();

  const rendered = text(harness.render());
  assert.match(rendered, /恢复操作已提交，但设置刷新失败/);
  assert.match(rendered, /旧聊天记录已迁移/);
  assert.doesNotMatch(rendered, /备份已恢复/);
});

test('cancelling a preview discards the native staged file without reporting an error', async () => {
  const harness = backupHarness();
  await openPreview(harness);
  buttonByLabel(harness.render(), '取消').props.onPress();
  await flush();

  assert.deepEqual(harness.calls.filter(call => call[0] === 'discardBackupImport'),
    [['discardBackupImport', 'stage-token']]);
  assert.equal(modal(harness.render()).props.visible, false);
  assert.doesNotMatch(text(harness.render()), /失败|已恢复|已保存/);
});

test('an unknown restore outcome closes the consumed preview and never invites a blind retry', async () => {
  const harness = backupHarness({
    restoreBackupImport: async () => {
      throw Object.assign(new Error('未能确认恢复结果，请先检查本机数据，再重新选择备份。'), {
        code: 'E_BACKUP_RESTORE_UNKNOWN',
      });
    },
  });
  await openPreview(harness);
  buttonByLabel(harness.render(), '确认覆盖并恢复').props.onPress();
  await flush();
  assert.equal(modal(harness.render()).props.visible, false);
  assert.match(text(harness.render()), /未能确认恢复结果.*先检查本机数据/);
  assert.doesNotMatch(text(harness.render()), /恢复失败|备份已恢复/);
});

test('a failed cancellation keeps the preview reachable so cleanup can be retried', async () => {
  let attempts = 0;
  const harness = backupHarness({ discardBackupImport: async () => {
    if (++attempts === 1) throw new Error('bridge unavailable');
    return true;
  } });
  await openPreview(harness);
  buttonByLabel(harness.render(), '取消').props.onPress();
  await flush();
  assert.equal(modal(harness.render()).props.visible, true);
  assert.match(text(harness.render()), /取消未完成/);
  buttonByLabel(harness.render(), '取消').props.onPress();
  await flush();
  assert.equal(modal(harness.render()).props.visible, false);
  assert.equal(attempts, 2);
});

test('unmounting a live preview discards its staged token', async () => {
  const harness = backupHarness();
  await openPreview(harness);
  harness.unmount();
  await flush();

  assert.deepEqual(harness.calls.filter(call => call[0] === 'discardBackupImport'),
    [['discardBackupImport', 'stage-token']]);
});

test('unmounting while native is staging still discards the late token', async () => {
  const stage = deferred();
  const harness = backupHarness({ prepareBackupImport: () => stage.promise });
  rowByLabel(harness.render(), '从备份恢复').props.onPress();
  await flush();

  harness.unmount();
  stage.resolve(backupPreview({ token: 'late-stage-token' }));
  await flush();

  assert.deepEqual(harness.calls.filter(call => call[0] === 'discardBackupImport'),
    [['discardBackupImport', 'late-stage-token']]);
});

test('unmounting during a failed restore releases the staged token afterward', async () => {
  const restore = deferred();
  let discardAttempts = 0;
  const harness = backupHarness({
    restoreBackupImport: () => restore.promise,
    discardBackupImport: async () => {
      discardAttempts++;
      if (discardAttempts === 1) throw new Error('native operation is busy');
      return true;
    },
  });
  await openPreview(harness);
  buttonByLabel(harness.render(), '确认覆盖并恢复').props.onPress();
  await flush();

  harness.unmount();
  restore.reject(new Error('disk write failed'));
  await flush();

  assert.deepEqual(harness.calls.filter(call => call[0] === 'discardBackupImport'),
    [
      ['discardBackupImport', 'stage-token'],
      ['discardBackupImport', 'stage-token'],
    ]);
});
