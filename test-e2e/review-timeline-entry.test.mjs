/**
 * Tier 1 — the review timeline's row is one compact line with an anchored menu.
 *
 * What it pins:
 *  1. a session renders as clock · subject tag · one-line body · duration ·
 *     「···」, and its body opens the existing session-note editor;
 *  2. the body falls back to the bound plan name, then to the kind of block, so
 *     an empty note never leaves the row blank;
 *  3. every destructive/editing action goes through the anchored menu and the
 *     existing confirmation — the menu never deletes a native record directly;
 *  4. note rows keep favorite/resolve/edit/delete in that same menu.
 */

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

const date = '2026-10-06';
const at = time => new Date(`${date}T${time}:00`).getTime();

const session = (changes = {}) => ({
  id: 'focus-a',
  subjectId: 'os',
  subjectName: '数据结构',
  subjectColor: '#2D7A5E',
  startTime: at('15:11'),
  endTime: at('17:17'),
  durationSeconds: 7560,
  pauseCount: 0,
  isExam: false,
  mode: 'STOPWATCH',
  note: '',
  taskId: null,
  ...changes,
});

const note = (changes = {}) => ({
  id: 'note-a',
  date,
  timestamp: at('16:10'),
  content: '手画了一遍指针断链过程',
  isFavorite: false,
  category: 'INSPIRATION',
  isResolved: false,
  resolvedAt: null,
  ...changes,
});

const snapshot = changes => ({
  date,
  formattedDate: '2026年10月6日',
  totalDurationSeconds: 0,
  focusCount: 0,
  focusParticipationCount: 0,
  examCount: 0,
  sessions: [],
  notes: [],
  tasks: [],
  taskActualSeconds: {},
  ...changes,
});

/**
 * Walk a rendered element tree, expanding function components as it goes.
 *
 * `ReviewScreen` delegates the timeline to `Timeline` / `TimelineItem`, which
 * React would normally invoke. Nothing invokes them here — there is no renderer
 * — so a naive walk stops at `jsx(Timeline, {...})` and never sees the row.
 */
function nodes(tree, predicate) {
  const result = [];
  function walk(node) {
    if (Array.isArray(node)) { node.forEach(walk); return; }
    if (!node || typeof node !== 'object' || !('type' in node)) return;
    if (typeof node.type === 'function') {
      walk(node.type(node.props));
      return;
    }
    if (predicate(node)) result.push(node);
    walk(node.props.children);
  }
  walk(tree);
  return result;
}

function text(tree) {
  if (Array.isArray(tree)) return tree.map(text).join('');
  if (tree && typeof tree === 'object' && 'type' in tree) {
    if (typeof tree.type === 'function') return text(tree.type(tree.props));
    return text(tree.props?.children);
  }
  if (tree && typeof tree === 'object') return text(tree.props?.children);
  return typeof tree === 'string' || typeof tree === 'number' ? String(tree) : '';
}

const UI_NAMES = [
  'YanjiCard', 'YanjiChronoSpectrum', 'YanjiEditorialHeader', 'YanjiIconButton',
  'YanjiLineChart', 'YanjiPaperSectionHeading',
  'YanjiPaperSegmentedControl', 'YanjiPaperSheetHeading', 'YanjiPrimaryButton',
  'YanjiStackedBarChart', 'YanjiSubjectProportionRow',
];

function screenHarness(daySnapshot) {
  const ui = Object.fromEntries(UI_NAMES.map(name => [name, name]));
  const calls = [];
  const states = [];
  let index = 0;
  const { ReviewScreen } = loadTypeScript('src/screens/ReviewScreen.tsx', {
    react: {
      useState: initial => {
        const i = index++;
        if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
        return [states[i], next => { states[i] = typeof next === 'function' ? next(states[i]) : next; }];
      },
      useMemo: fn => fn(),
      useCallback: fn => fn,
      useEffect: () => {},
    },
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView', Modal: 'Modal',
      useWindowDimensions: () => ({ fontScale: 1, width: 390, height: 844 }),
    },
    'react-native-reanimated': { useReducedMotion: () => true },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom: 0, top: 0 }) },
    '../bridge': {
      YanjiDataNative: {
        toggleFavoriteNote: async id => { calls.push(['favorite', id]); return true; },
        toggleNoteResolved: async id => { calls.push(['resolve', id]); return true; },
        deleteSessionRecord: async (...args) => { calls.push(['delete', ...args]); return true; },
      },
    },
    '../components/YanjiUI': ui,
    '../components/RecordMomentModal': { RecordMomentModal: 'RecordMomentModal' },
    '../components/SessionNoteModal': { SessionNoteModal: 'SessionNoteModal' },
    '../components/TimelineCompactRow': { TimelineCompactRow: 'TimelineCompactRow' },
    '../components/TimelineActionPopover': { TimelineActionPopover: 'TimelineActionPopover' },
    '../navigation/useBottomTabLayout': { useBottomTabLayout: () => ({ contentPadding: 96 }) },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../state/dataRefresh': {
      useDailySnapshot: () => daySnapshot,
      useSettingsSnapshot: () => ({ settings: { dailyGoalHours: 6 } }),
      useReviewOverview: () => null,
      useReviewDataVisibility: () => {},
      dataRefreshCoordinator: { refreshReviewOverview: async () => {} },
      refreshDailySnapshot: async () => {},
    },
  }, { Date: class extends Date { constructor(...args) { super(...(args.length ? args : [`${date}T18:00:00`])); } } });
  return {
    calls,
    render: () => { index = 0; return ReviewScreen({ visible: true }); },
    rows: tree => nodes(tree, node => node.type === 'TimelineCompactRow'),
    menu: tree => nodes(tree, node => node.type === 'TimelineActionPopover')[0],
    noteModal: tree => nodes(tree, node => node.type === 'SessionNoteModal')[0],
    recordModal: tree => nodes(tree, node => node.type === 'RecordMomentModal')[0],
    confirmation: tree => nodes(tree, node => node.type === 'Modal')[0],
  };
}

/** The popover closes first, then the chosen command runs — mimic that order. */
const pressMenuAction = (menu, label) => {
  const action = menu.props.actions.find(item => item.label === label);
  assert.ok(action, `the menu offers 「${label}」`);
  menu.props.onClose();
  action.onPress();
};

/** Call arguments cross the VM boundary, so compare payloads, not realm objects. */
const plain = value => JSON.parse(JSON.stringify(value));

test('a session is one compact line whose body opens its note editor', () => {
  const harness = screenHarness(snapshot({
    sessions: [session({ note: '手画了一遍指针断链过程', taskId: 'task-os-ch4' })],
    tasks: [{
      id: 'task-os-ch4', date, subjectId: 'os', subjectName: '数据结构', subjectColor: '#2D7A5E',
      title: '王道操作系统第四章', plannedMinutes: 90, actualMinutes: 0, completed: false, createdAt: at('08:00'),
    }],
  }));
  const tree = harness.render();
  const rows = harness.rows(tree);
  assert.equal(rows.length, 1);
  const row = rows[0];

  assert.equal(row.props.tagLabel, '数据结构');
  assert.equal(row.props.tagIcon, 'clock3', 'every focus subject shares the clock icon');
  assert.equal(row.props.tagTone, 'neutral');
  assert.equal(row.props.dotColor, '#2D7A5E', 'the node keeps the subject colour');
  assert.equal(row.props.content, '手画了一遍指针断链过程', 'the note is the body');
  assert.equal(row.props.metaText, '2 小时 6 分', 'duration rides on the same line');
  assert.equal(row.props.isFirst, true);
  assert.equal(row.props.isLast, true);

  assert.equal(harness.noteModal(tree).props.visible, false);
  row.props.onPressContent();
  const reopened = harness.noteModal(harness.render());
  assert.equal(reopened.props.visible, true, 'tapping the body opens the note editor');
  assert.equal(reopened.props.sessionId, 'focus-a', 'it opens the session that was tapped');
});

test('an empty session note falls back to the bound plan, then to the kind of block', () => {
  const bound = screenHarness(snapshot({
    sessions: [session({ taskId: 'task-1' })],
    tasks: [{ id: 'task-1', title: '王道操作系统第四章' }],
  })).render();
  assert.equal(screenHarness(snapshot({
    sessions: [session({ taskId: 'task-1' })],
    tasks: [{ id: 'task-1', title: '王道操作系统第四章' }],
  })).rows(bound)[0].props.content, '达成「王道操作系统第四章」');

  const plain = screenHarness(snapshot({ sessions: [session()] })).render();
  assert.equal(screenHarness(snapshot({ sessions: [session()] })).rows(plain)[0].props.content, '专注');

  const exam = screenHarness(snapshot({ sessions: [session({ isExam: true, subjectName: '英语一' })] })).render();
  assert.equal(screenHarness(snapshot({
    sessions: [session({ isExam: true, subjectName: '英语一' })],
  })).rows(exam)[0].props.content, '模考');
});

test('the session menu keeps edit/delete behind the existing confirmation', async () => {
  const harness = screenHarness(snapshot({ sessions: [session({ note: '已写好的随笔' })] }));
  let tree = harness.render();
  harness.rows(tree)[0].props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  tree = harness.render();
  const menu = harness.menu(tree);
  assert.ok(menu, 'the row opens the anchored menu');
  assert.deepEqual(plain(menu.props.actions.map(action => action.label)), ['编辑随笔', '删除记录']);

  pressMenuAction(menu, '删除记录');
  const after = harness.render();
  assert.equal(harness.confirmation(after).props.visible, true, 'the existing confirmation opens');
  assert.deepEqual(plain(harness.calls), [], 'opening the confirmation must not delete a native record');

  const exam = screenHarness(snapshot({ sessions: [session({ isExam: true })] }));
  exam.rows(exam.render())[0].props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  assert.deepEqual(plain(exam.menu(exam.render()).props.actions.map(action => action.label)), ['删除记录'],
    'an exam has no editable note');
});

test('note rows keep favorite, resolution and editing in the anchored menu', async () => {
  const harness = screenHarness(snapshot({
    notes: [
      note({ id: 'doubt-1', content: '二叉树前序遍历与线索化区别', category: 'DOUBT' }),
      note({ id: 'insp-1', content: '今天背词效率很高', timestamp: at('16:20') }),
    ],
  }));
  const rows = harness.rows(harness.render());
  assert.deepEqual(plain(rows.map(row => row.props.tagLabel).sort()), ['疑点 · 待解决', '灵感'].sort());
  assert.deepEqual(plain(rows.map(row => row.props.tagIcon).sort()), ['doubt', 'inspiration']);
  assert.deepEqual(plain(rows.map(row => row.props.tagTone).sort()), ['brand', 'positive']);

  const doubtRow = rows.find(row => row.props.tagLabel === '疑点 · 待解决');
  doubtRow.props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  let menu = harness.menu(harness.render());
  assert.deepEqual(plain(menu.props.actions.map(action => action.label)),
    ['收藏记录', '标记为已解决', '编辑记录', '删除记录']);

  pressMenuAction(menu, '标记为已解决');
  await Promise.resolve();
  assert.deepEqual(plain(harness.calls), [['resolve', 'doubt-1']]);

  const inspRow = harness.rows(harness.render()).find(row => row.props.tagLabel === '灵感');
  inspRow.props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  menu = harness.menu(harness.render());
  pressMenuAction(menu, '编辑记录');
  const editor = harness.recordModal(harness.render());
  assert.equal(editor.props.editingNote.id, 'insp-1');
  assert.equal(editor.props.editingNote.content, '今天背词效率很高');
  assert.equal(editor.props.initialConfirmDelete, false);

  harness.rows(harness.render()).find(row => row.props.tagLabel === '灵感')
    .props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  pressMenuAction(harness.menu(harness.render()), '删除记录');
  assert.equal(harness.recordModal(harness.render()).props.initialConfirmDelete, true,
    'delete only opens the existing confirmation');
});

/** The three big numbers in the summary strip, in column order. */
const summaryNumbers = tree => plain(nodes(tree, node => node.type === 'Text' &&
  node.props.style?.[0]?.fontSize === theme.paper.typography.metricHero.fontSize)
  .map(node => text(node)));

test('the day summary is exactly two rows per metric: label, then one big number', () => {
  const tree = screenHarness(snapshot({
    totalDurationSeconds: 1800,
    focusCount: 1,
    focusParticipationCount: 3,
    tasks: [
      { id: 'done', date, title: 'done', completed: true },
      { id: 'open', date, title: 'open', completed: false },
    ],
  })).render();

  for (const label of ['专注时长', '完成任务', '专注次数']) {
    assert.ok(text(tree).includes(label), `the summary names ${label}`);
  }
  assert.deepEqual(summaryNumbers(tree), ['30', '1/2', '3']);

  const units = plain(nodes(tree, node => node.type === 'Text' &&
    node.props.style?.[0]?.fontSize === theme.paper.typography.bodyPrimary.fontSize &&
    ['分钟', '次'].includes(text(node))).map(node => text(node)));
  assert.deepEqual(units, ['分钟', '次'], 'units are smaller than the numbers but share the line');

  // Baseline alignment: each column's number and unit sit in one baseline row.
  assert.equal(nodes(tree, node => node.type === 'View' && node.props.style?.alignItems === 'baseline').length >= 3, true);

  // The old third line is gone.
  assert.doesNotMatch(text(tree), /当日专注|最长单次|目标 6|含 0 场模考/);
});

test('zero data and a loading snapshot stay truthful in the summary', () => {
  const empty = screenHarness(snapshot({ totalDurationSeconds: 0, focusParticipationCount: 0 })).render();
  assert.deepEqual(summaryNumbers(empty), ['0', '0/0', '0']);

  const loading = screenHarness(null).render();
  assert.deepEqual(summaryNumbers(loading), ['—', '—', '—']);
  assert.doesNotMatch(text(loading), /分钟/, 'no unit is invented before the read returns');
});

test('the summary renders whichever day\'s snapshot is selected', () => {
  const first = screenHarness(snapshot({
    totalDurationSeconds: 600, focusParticipationCount: 1,
  })).render();
  assert.deepEqual(summaryNumbers(first), ['10', '0/0', '1']);

  const second = screenHarness(snapshot({
    totalDurationSeconds: 5400, focusParticipationCount: 4,
  })).render();
  assert.deepEqual(summaryNumbers(second), ['90', '0/0', '4']);
});
