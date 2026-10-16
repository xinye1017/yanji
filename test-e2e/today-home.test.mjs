import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

const tokens = loadTypeScript('src/theme/tokens.ts');
const theme = {
  paper: {
    colors: tokens.YanjiPaperColors.light,
    typography: tokens.YanjiPaperTypography,
    layout: tokens.YanjiPaperLayout,
    radius: tokens.YanjiPaperRadius,
  },
};
const logic = loadTypeScript('src/screens/todayViewLogic.ts');
const date = '2026-10-16';
const at = time => new Date(`${date}T${time}:00`).getTime();
const task = (changes = {}) => ({
  id: 'task-a', date, subjectId: 'math', subjectName: '数学一', subjectColor: '#356AE6',
  title: '积分练习', plannedMinutes: 90, actualMinutes: 0, completed: false, createdAt: at('08:00'),
  ...changes,
});
const session = (id, from, to, changes = {}) => ({
  id, title: '专注', subjectId: 'math', subjectName: '数学一', subjectColor: '#356AE6',
  startTime: at(from), endTime: at(to), durationSeconds: (at(to) - at(from)) / 1000,
  pauseCount: 0, isExam: false, mode: 'STOPWATCH', note: '', ...changes,
});

function nodes(tree, predicate) {
  const result = [];
  function walk(node) {
    if (Array.isArray(node)) node.forEach(walk);
    else if (node && typeof node === 'object' && 'type' in node) {
      if (predicate(node)) result.push(node);
      walk(node.props.children);
    }
  }
  walk(tree);
  return result;
}
function text(tree) {
  if (Array.isArray(tree)) return tree.map(text).join('');
  if (tree && typeof tree === 'object') return text(tree.props?.children);
  return typeof tree === 'string' || typeof tree === 'number' ? String(tree) : '';
}
let spectrumWidth = 276;
const shared = loadTypeScript('src/components/YanjiUI.tsx', {
  react: { useState: () => [spectrumWidth, value => { spectrumWidth = value; }] },
  'react-native': { View: 'View', Text: 'Text', Pressable: 'Pressable' },
  'react-native-reanimated': {},
  'react-native-svg': {},
  '../theme/ThemeProvider': { useYanjiTheme: () => theme },
  './icons': { getYanjiIcon: name => name, YANJI_ICON_STROKE: 1.75 },
});

test('Chinese dates and durations retain truthful zero and subminute values', () => {
  assert.equal(logic.displayDate(date), '十月十六日 · 周五');
  assert.match(logic.displayDate('2026-12-31'), /^十二月三十一日/);
  assert.equal(logic.goalClock(21600), '06h 00m');
  assert.equal(logic.compactDuration(3599), '59m');
  assert.equal(logic.compactDuration(10), '<1m');
  assert.equal(logic.taskMeta(task({ completed: true })), '实际专注 0m');
  assert.equal(logic.taskMeta(task({ actualMinutes: 45 })), '已投入 45m / 预计 1h30m');
});

test('subject legend includes genuine planned subjects at zero and keeps their color', () => {
  const saved = [session('a', '09:00', '09:30')];
  const summary = logic.summarizeSubjects(saved, theme.paper.colors.secondary);
  const legend = logic.spectrumLegend(summary, [task(), task({ subjectId: 'politics', subjectName: '政治', subjectColor: '#DC2626' })]);
  assert.equal(legend.length, 2);
  assert.equal(legend[0].seconds, 1800);
  assert.equal(legend[1].seconds, 0);
  assert.equal(legend[1].color, '#DC2626');
  assert.equal(saved[0].durationSeconds, 1800);
  const reordered = logic.summarizeSubjects([
    session('b', '10:00', '10:50', { subjectId: 'english' }), ...saved,
  ], theme.paper.colors.secondary);
  assert.equal(reordered[1].id, 'math', 'series order must not depend on duration rank');
});

test('rhythm clips to each local hour, rejects paused spans, and aggregates dominant subject', () => {
  const sessions = [
    session('before', '05:45', '06:15'),
    session('cross', '09:45', '10:15'),
    session('one', '11:00', '11:10'),
    session('two', '11:10', '11:20'),
    session('other', '11:20', '11:35', { subjectId: 'english', subjectColor: '#16A34A' }),
    session('paused', '12:00', '12:40', { durationSeconds: 1200, pauseCount: 1 }),
    session('clock-mismatch', '13:00', '13:20', { durationSeconds: 300 }),
    session('last', '23:45', '24:00'),
  ];
  const buckets = logic.buildRhythmBuckets(date, sessions, theme.paper.colors.secondary);
  const hour = h => buckets.find(bucket => bucket.hour === h);
  assert.equal(buckets.length, 18);
  assert.equal(hour(6).seconds, 900);
  assert.equal(hour(9).seconds, 900);
  assert.equal(hour(10).seconds, 900);
  assert.equal(hour(11).seconds, 2100);
  assert.equal(hour(11).color, '#356AE6', 'two short math sessions together outweigh the English session');
  assert.equal(hour(12).seconds, 0);
  assert.equal(hour(13).seconds, 0);
  assert.equal(hour(23).seconds, 900);
  assert.equal(logic.summarizeSubjects(sessions, theme.paper.colors.secondary).reduce((sum, item) => sum + item.seconds, 0),
    sessions.reduce((sum, item) => sum + item.durationSeconds, 0), 'excluded timeline spans still count toward saved investment');
});

test('spectrum shows goal remainder and fills distribution when no goal or over goal', () => {
  const segments = [
    { id: 'math', label: '数学', color: '#356AE6', seconds: 60 },
    { id: 'english', label: '英语', color: '#16A34A', seconds: 16 },
  ];
  const render = goalSeconds => shared.YanjiChronoSpectrum({ segments, totalSeconds: 76, goalSeconds });
  assert.equal(render(100).props.children.props.style.width, '76%');
  assert.equal(render(100).props.accessibilityValue.now, 76);
  assert.equal(render(null).props.children.props.style.width, '100%');
  assert.equal(render(50).props.children.props.style.width, '100%');
  assert.equal(render(50).props.accessibilityValue.now, 100);
  assert.match(render(50).props.accessibilityLabel, /已超出目标/);
  assert.equal(render(100).props.children.props.style.gap, tokens.YanjiPaperLayout.spectrumGap);
  const empty = shared.YanjiChronoSpectrum({ segments: [], totalSeconds: 0, goalSeconds: 100 });
  assert.equal(empty.props.children.props.style.width, '0%');
});

test('tiny spectrum progress reserves colored space and loading never announces fabricated zeros', () => {
  const segments = [
    { id: 'a', label: '数学', color: '#356AE6', seconds: 60 },
    { id: 'b', label: '英语', color: '#16A34A', seconds: 60 },
  ];
  const render = () => shared.YanjiChronoSpectrum({ segments, totalSeconds: 120, goalSeconds: 21600 });
  const tinyWidth = 276 * 120 / 21600;
  render().props.children.props.onLayout({ nativeEvent: { layout: { width: tinyWidth } } });
  const gap = render().props.children.props.style.gap;
  assert.ok(gap < tinyWidth / 2, 'gaps cannot consume all the progress width');
  const loading = shared.YanjiChronoSpectrum({ segments: [], totalSeconds: 0, goalSeconds: null, accessibilityLabel: '正在读取今日专注' });
  assert.equal(loading.props.accessibilityLabel, '正在读取今日专注');
  assert.equal(loading.props.accessibilityValue, undefined);
  spectrumWidth = 276;
});

test('task controls keep completion, editing, and focus separate; completed rows have no focus capsule', () => {
  const calls = [];
  const props = {
    title: '积分练习', subjectName: '数学一', accentColor: '#356AE6', meta: '实际专注 0m',
    onToggle: () => calls.push('toggle'), onEdit: () => calls.push('edit'), onStartFocus: () => calls.push('focus'),
  };
  const row = shared.YanjiTaskLedgerRow({ ...props, completed: false });
  const controls = nodes(row, node => node.type === 'Pressable');
  assert.equal(controls.length, 3);
  for (const control of controls) {
    assert.ok(control.props.style({ pressed: false }).minHeight >= 48);
    control.props.onPress();
  }
  assert.deepEqual(calls, ['toggle', 'edit', 'focus']);
  const completed = shared.YanjiTaskLedgerRow({ ...props, completed: true });
  assert.equal(nodes(completed, node => node.type === 'Pressable').length, 2);
  assert.equal(nodes(completed, node => node.props.accessibilityRole === 'checkbox')[0].props.accessibilityState.checked, true);
});

test('task subject labels use readable theme text rather than the decorative subject colour', () => {
  const original = theme.paper.colors;
  try {
    for (const colors of Object.values(tokens.YanjiPaperColors)) {
      theme.paper.colors = colors;
      for (const accentColor of ['#16A34A', '#356AE6', '#DC2626']) {
        const row = shared.YanjiTaskLedgerRow({
          title: '积分练习', subjectName: '数学一', meta: '已投入 0m', accentColor,
          completed: false, onToggle() {}, onEdit() {}, onStartFocus() {},
        });
        const label = nodes(row, n => n.type === 'Text' && n.props.children === '数学一')[0];
        assert.equal(label.props.style.color, colors.secondary, 'subject colour belongs to the dot and tint, not small text');
      }
    }
  } finally {
    theme.paper.colors = original;
  }
});

function screenHarness(snapshot) {
  const states = [];
  const calls = [];
  let index = 0;
  const names = ['YanjiBadge', 'YanjiCard', 'YanjiChronoSpectrum', 'YanjiEditorialHeader', 'YanjiEmptyLedgerRow',
    'YanjiHairline', 'YanjiIcon', 'YanjiIconButton', 'YanjiMiniRhythmSparkline', 'YanjiPaperSectionHeading',
    'YanjiPaperSegmentedControl', 'YanjiPaperSheetHeading', 'YanjiPrimaryButton', 'YanjiSubjectProportionRow',
    'YanjiTaskLedgerRow', 'YanjiTextAction'];
  const ui = Object.fromEntries(names.map(name => [name, name]));
  let active = null;
  let tickReads = 0;
  const { TodayScreen } = loadTypeScript('src/screens/TodayScreen.tsx', {
    react: {
      useState: initial => {
        const i = index++;
        if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
        return [states[i], next => { states[i] = typeof next === 'function' ? next(states[i]) : next; }];
      },
      useMemo: fn => fn(), useCallback: fn => fn, useRef: value => ({ current: value }), useEffect: () => {},
    },
    'react-native': { View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView', Modal: 'Modal', RefreshControl: 'RefreshControl' },
    'react-native-reanimated': { useReducedMotion: () => true },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom: 0 }) },
    '../bridge': { YanjiDataNative: {} },
    '../components/YanjiUI': ui,
    '../components/RecordMomentModal': { RecordMomentModal: 'RecordMomentModal' },
    '../components/TaskEditorModal': { TaskEditorModal: 'TaskEditorModal' },
    '../navigation/NavigationShell': {
      useNavigationActions: () => ({
        selectTab: tab => calls.push(['tab', tab]), requestFocusPreset: preset => calls.push(['preset', preset]),
        openSettings: () => {}, openTaskEditor: () => {}, closeTaskEditor: () => {},
      }),
      useNavigationState: () => ({ taskEditorOpen: false }),
    },
    '../navigation/useBottomTabLayout': { useBottomTabLayout: () => ({ contentPadding: 96 }) },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../state/dataRefresh': {
      useDailySnapshot: () => snapshot,
      useSettingsSnapshot: () => ({ settings: { dailyGoalHours: 6 }, examCountdown: { examDate: '2027-12-25', daysRemaining: 435 } }),
    },
    '../state/timerStore': { useActiveSession: () => active, useTimerTick: () => { tickReads++; } },
  }, { Date: class extends Date { constructor(...args) { super(...(args.length ? args : [`${date}T15:00:00`])); } } });
  return {
    calls, setActive: value => { active = value; }, get tickReads() { return tickReads; },
    render: () => { index = 0; return TodayScreen({ visible: true, foregroundRevision: 0 }); },
  };
}

const snapshot = changes => ({ date, totalDurationSeconds: 0, focusCount: 0, examCount: 0, sessions: [], notes: [], tasks: [], ...changes });

test('production homepage routes free focus and running focus without a page-level tick read', () => {
  const harness = screenHarness(snapshot());
  let tree = harness.render();
  nodes(tree, node => node.type === 'YanjiPrimaryButton' && node.props.label === '开始一轮专注')[0].props.onPress();
  assert.deepEqual(harness.calls, [['tab', 'focus']]);
  harness.setActive({ sessionId: 'active', subjectName: '数学', isPaused: true });
  tree = harness.render();
  assert.equal(nodes(tree, node => node.type === 'YanjiPrimaryButton' && node.props.label === '开始一轮专注').length, 0);
  nodes(tree, node => node.type === 'YanjiPrimaryButton' && node.props.label === '返回专注工作台')[0].props.onPress();
  assert.equal(harness.tickReads, 0);
});

test('production homepage shows truthful task metadata and expands rhythm only on request', () => {
  const completed = task({ id: 'done', completed: true });
  const pending = task();
  const harness = screenHarness(snapshot({ tasks: [completed, pending] }));
  let tree = harness.render();
  const rows = nodes(tree, node => node.type === 'YanjiTaskLedgerRow');
  assert.equal(rows[0].props.completed, false);
  assert.equal(rows[1].props.completed, true);
  assert.equal(rows[1].props.meta, '实际专注 0m');
  assert.equal(rows[0].props.accentColor, pending.subjectColor);
  rows[0].props.onStartFocus();
  assert.equal(harness.calls[0][1].taskId, pending.id);
  assert.equal(nodes(tree, node => node.type === 'YanjiPaperSegmentedControl').length, 0);
  const toggle = nodes(tree, node => node.props.accessibilityLabel === '今日学习节奏 (06:00-24:00)')[0];
  toggle.props.onPress();
  tree = harness.render();
  assert.equal(nodes(tree, node => node.type === 'YanjiPaperSegmentedControl').length, 1);
  assert.equal(nodes(tree, node => node.props.accessibilityLabel === '今日学习节奏 (06:00-24:00)')[0].props.accessibilityState.expanded, true);
});

test('production homepage keeps loading distinct from an empty saved day', () => {
  const tree = screenHarness(null).render();
  assert.match(text(tree), /正在读取今日记录/);
  assert.equal(nodes(tree, node => node.type === 'YanjiPaperSectionHeading')[0].props.countText, '—');
  assert.equal(nodes(tree, node => node.type === 'YanjiChronoSpectrum')[0].props.accessibilityLabel, '正在读取今日专注');
});
