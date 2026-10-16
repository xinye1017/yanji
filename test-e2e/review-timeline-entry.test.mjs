/**
 * Tier 1 — the review timeline's entry is one tappable surface.
 *
 * Why this test exists: the requirement is "点击时间线上的任务，即可直接进入编辑页".
 * A still screenshot does not establish which tap target opens the sheet, so
 * this drives the real `ReviewScreen` through its handlers and checks the
 * resulting editor state.
 *
 * What it pins:
 *  1. the session title, the bound-plan chip and the note body all sit inside
 *     one Pressable that opens the note editor, so tapping any of them works;
 *  2. the nested delete handler opens its confirmation, keeps the editor closed
 *     and does not delete a native record before confirmation;
 *  3. the subtitle no longer advertises "编辑随笔" (the entry itself is the
 *     trigger), while an entry with no note still says so.
 *
 * This drives production source through `loadTypeScript`, the same harness
 * `today-home.test.mjs` uses.
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

const snapshot = changes => ({
  date,
  formattedDate: '2026年10月6日',
  totalDurationSeconds: 0,
  focusCount: 0,
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
 * `ReviewScreen` delegates the timeline to `Timeline` / `TimelineItem` /
 * `SessionEntry`, which React would normally invoke. Nothing invokes them here —
 * there is no renderer — so a naive walk stops at `jsx(Timeline, {...})` and
 * never sees the entry the test is about. Calling each function component with
 * its own props is what makes the assertion reach the real markup.
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

/** True when `needle` renders somewhere inside `tree`. */
function renders(tree, needle) {
  return text(tree).includes(needle);
}

const UI_NAMES = [
  'YanjiCard', 'YanjiChronoSpectrum', 'YanjiEditorialHeader', 'YanjiIcon',
  'YanjiIconButton', 'YanjiLineChart', 'YanjiPaperSectionHeading',
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
        toggleFavoriteNote: async () => true,
        deleteSessionRecord: async (...args) => { calls.push(args); return true; },
      },
    },
    '../components/YanjiUI': ui,
    '../components/RecordMomentModal': { RecordMomentModal: 'RecordMomentModal' },
    '../components/SessionNoteModal': { SessionNoteModal: 'SessionNoteModal' },
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
    /** The `SessionNoteModal` element, whose `visible`/`sessionId` say what opened. */
    noteModal: tree => nodes(tree, node => node.type === 'SessionNoteModal')[0],
  };
}

test('tapping the title, the bound-plan chip and the note body all open the note editor', () => {
  const harness = screenHarness(snapshot({
    sessions: [session({ note: '手画了一遍指针断链过程', taskId: 'task-os-ch4' })],
    tasks: [{
      id: 'task-os-ch4', date, subjectId: 'os', subjectName: '数据结构', subjectColor: '#2D7A5E',
      title: '王道操作系统第四章', plannedMinutes: 90, actualMinutes: 0, completed: false, createdAt: at('08:00'),
    }],
  }));
  const tree = harness.render();

  // The modal starts closed: nothing opened it yet.
  assert.equal(harness.noteModal(tree).props.visible, false);

  // One Pressable carries the whole entry, so every line of it is a trigger.
  const entry = nodes(tree, node => node.type === 'Pressable'
    && node.props.accessibilityLabel?.startsWith('数据结构 · 专注')).at(0);
  assert.ok(entry, 'the session entry is one Pressable');

  const inside = [];
  (function collect(node) {
    if (Array.isArray(node)) node.forEach(collect);
    else if (node && typeof node === 'object' && 'type' in node) { inside.push(node); collect(node.props.children); }
  })(entry.props.children);

  for (const [name, content] of [
    ['title', '数据结构 · 专注'],
    ['bound-plan chip', '达成关联任务：王道操作系统第四章'],
    ['note body', '手画了一遍指针断链过程'],
  ]) {
    const node = inside.find(child => child.type === 'Text' && text(child) === content);
    assert.ok(node, `the ${name} renders inside the entry's Pressable`);
    assert.equal(nodes(entry, child => child.type === 'Pressable' && renders(child, content)).length, 1,
      `the ${name} uses the entry's handler without a nested press target`);
  }

  // The trigger opens the editor for *this* session, not merely some sheet.
  entry.props.onPress();
  const reopened = harness.noteModal(harness.render());
  assert.equal(reopened.props.visible, true, 'tapping the entry opens the note editor');
  assert.equal(reopened.props.sessionId, 'focus-a', 'it opens the session that was tapped');
});

test('the delete control requests confirmation without opening the editor or deleting early', async () => {
  const harness = screenHarness(snapshot({ sessions: [session()] }));
  const tree = harness.render();

  const entry = nodes(tree, node => node.type === 'Pressable'
    && node.props.accessibilityLabel?.startsWith('数据结构 · 专注')).at(0);
  const remove = nodes(entry, node => node.type === 'YanjiIconButton'
    && node.props.icon === 'delete')[0];
  assert.ok(remove, 'the entry carries its own delete control');

  const confirmation = root => nodes(root, node => node.type === 'Modal')[0];
  assert.equal(confirmation(tree).props.visible, false);
  remove.props.onPress();
  await Promise.resolve();

  // This covers the delete handler's state changes; native responder arbitration
  // between the nested button and entry still needs a device interaction check.
  const after = harness.render();
  assert.equal(harness.noteModal(after).props.visible, false);
  assert.equal(confirmation(after).props.visible, true, 'the delete handler actually opens its confirmation');
  assert.deepEqual(harness.calls, [], 'opening the confirmation must not delete a native record');
});

test('the subtitle drops 编辑随笔 but still speaks up when there is no note', () => {
  const withNote = screenHarness(snapshot({ sessions: [session({ note: '已写好的随笔' })] })).render();
  assert.ok(!renders(withNote, '编辑随笔 ›'), 'a session with a note does not advertise the gesture');

  const withoutNote = screenHarness(snapshot({ sessions: [session({ note: '' })] })).render();
  assert.ok(renders(withoutNote, '添一段专注随笔'), 'a session with no note still says so');

  const exam = screenHarness(snapshot({
    sessions: [session({ isExam: true, subjectName: '英语一' })],
  })).render();
  assert.ok(renders(exam, '模考随笔暂不支持编辑'), 'an exam states that it has no editable note');
});
