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
import { createFlashListDouble } from './framework/flash-list-double.js';

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
      // A function component is not a host node: expand it and keep walking the
      // element it returns. Returning early here dropped everything a mocked
      // list component produced (its header and its rows).
      walk(node.type(node.props));
      return;
    }
    if (predicate(node)) result.push(node);
    walk(node.props.children);
  }
  walk(tree);  return result;
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
  'YanjiCard', 'YanjiChronoSpectrum', 'YanjiEditorialHeader',
  'YanjiLineChart', 'YanjiPaperSectionHeading',
  'YanjiPaperSegmentedControl', 'YanjiPaperSheetHeading', 'YanjiPrimaryButton',
  'YanjiStackedBarChart', 'YanjiStaleDataNotice', 'YanjiSubjectProportionRow',
];

function screenHarness(daySnapshot, {
  deleteSessionRecord = async () => true,
  dailyReadFailed = false,
} = {}) {
  const ui = Object.fromEntries(UI_NAMES.map(name => [name, name]));
  const calls = [];
  const states = [];
  const refs = {};
  const flashList = createFlashListDouble();
  let index = 0;
  let refIndex = 0;
  // The retry spies record the exact window the banner re-reads.
  const refreshCalls = [];
  const { ReviewScreen } = loadTypeScript('src/screens/ReviewScreen.tsx', {
    react: {
      useState: initial => {
        const i = index++;
        if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
        return [states[i], next => { states[i] = typeof next === 'function' ? next(states[i]) : next; }];
      },
      useRef: initial => {
        const i = refIndex++;
        if (!(i in refs)) refs[i] = { current: initial };
        return refs[i];
      },
      useMemo: fn => fn(),
      useCallback: fn => fn,
      useEffect: () => {},
      // The strip positions itself in a layout effect, before paint. The double
      // runs it inline so a test sees the same offset the device would paint.
      useLayoutEffect: fn => fn(),
    },
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView', Modal: 'Modal',
      useWindowDimensions: () => ({ fontScale: 1, width: 390, height: 844 }),
      PanResponder: { create: config => ({ panHandlers: config }) },
    },
    // The strip pages with a shared value; the double keeps the animation
    // out of the way and hands back the raw target so tests read a real number.
    'react-native-reanimated': {
      default: { View: 'Animated.View' },
      useSharedValue: initial => ({ value: initial }),
      useAnimatedStyle: style => style(),
      withTiming: to => to,
      runOnUI: fn => (...args) => fn(...args),
      useReducedMotion: () => true,
    },
    // The timeline view is a FlashList now. The shared double windows its cells,
    // so a test can tell "the rows go through the virtualised list" apart from
    // "the page mounted every row and called it virtualization".
    '@shopify/flash-list': flashList,
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom: 0, top: 0 }) },
    '../bridge': {
      YanjiDataNative: {
        toggleFavoriteNote: async id => { calls.push(['favorite', id]); return true; },
        toggleNoteResolved: async id => { calls.push(['resolve', id]); return true; },
        setNoteCategory: async (id, category) => { calls.push(['category', id, category]); return true; },
        deleteSessionRecord: async (...args) => {
          calls.push(['delete', ...args]);
          return deleteSessionRecord(...args);
        },
      },
    },
    '../components/YanjiUI': ui,
    '../components/RecordMomentModal': { RecordMomentModal: 'RecordMomentModal' },
    '../components/RoamingNotesModal': { RoamingNotesModal: 'RoamingNotesModal' },
    './review/JournalLibraryView': { JournalLibraryView: 'JournalLibraryView' },
    '../components/SessionNoteModal': { SessionNoteModal: 'SessionNoteModal' },
    '../components/TimelineCompactRow': { TimelineCompactRow: 'TimelineCompactRow' },
    '../components/TimelineActionPopover': { TimelineActionPopover: 'TimelineActionPopover' },
    '../navigation/useBottomTabLayout': { useBottomTabLayout: () => ({ contentPadding: 96 }) },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../state/dataRefresh': {
      useDailySnapshot: () => daySnapshot,
      useDailyReadFailed: () => dailyReadFailed,
      useSettingsSnapshot: () => ({ settings: { dailyGoalHours: 6 } }),
      useReviewOverview: () => null,
      useReviewReadFailed: () => false,
      useReviewDataVisibility: () => {},
      refreshDailySnapshot: async day => { refreshCalls.push(['daily', day]); },
      refreshReviewOverview: async request => { refreshCalls.push(['review', request.scope, request.anchorDate]); },
    },
  }, { Date: class extends Date { constructor(...args) { super(...(args.length ? args : [`${date}T18:00:00`])); } } });
  const render = () => {
    index = 0;
    refIndex = 0;
    return ReviewScreen({ visible: true });
  };
  return {
    calls,
    refreshCalls,
    flashList,
    render,
    rows: tree => nodes(tree, node => node.type === 'TimelineCompactRow'),
    menu: tree => nodes(tree, node => node.type === 'TimelineActionPopover')[0],
    noteModal: tree => nodes(tree, node => node.type === 'SessionNoteModal')[0],
    recordModal: tree => nodes(tree, node => node.type === 'RecordMomentModal')[0],
    confirmation: tree => nodes(tree, node => node.type === 'Modal')[0],
    /**
     * The timeline list as it was actually given.
     *
     * `FlashList` is a function component, so it only runs while the tree is
     * walked; this walks the current render first so callers need not know that.
     */
    listRender: () => {
      nodes(render(), () => false);
      return flashList.lastRender('scroll-review');
    },
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

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

const flush = () => new Promise(resolve => setImmediate(resolve));

test('a session renders as a single-line tag capsule with subject colour and duration, decoupled from note editing', () => {
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
  assert.equal(row.props.tagColor, '#2D7A5E', 'tag carries the subject colour');
  assert.equal(row.props.dotColor, '#2D7A5E', 'the node keeps the subject colour');
  assert.equal(row.props.content, undefined, 'no body text / "专注" line on sessions');
  assert.equal(row.props.metaText, '2 小时 6 分', 'duration rides beside the capsule');
  assert.equal(row.props.isFirst, true);
  assert.equal(row.props.isLast, true);
});

test('sessions omit body text regardless of bound plans or notes, staying single-line', () => {
  const bound = screenHarness(snapshot({
    sessions: [session({ taskId: 'task-1' })],
    tasks: [{ id: 'task-1', title: '王道操作系统第四章' }],
  })).render();
  assert.equal(screenHarness(snapshot({
    sessions: [session({ taskId: 'task-1' })],
    tasks: [{ id: 'task-1', title: '王道操作系统第四章' }],
  })).rows(bound)[0].props.content, undefined);

  const plain = screenHarness(snapshot({ sessions: [session()] })).render();
  assert.equal(screenHarness(snapshot({ sessions: [session()] })).rows(plain)[0].props.content, undefined);

  const exam = screenHarness(snapshot({ sessions: [session({ isExam: true, subjectName: '英语一' })] })).render();
  assert.equal(screenHarness(snapshot({
    sessions: [session({ isExam: true, subjectName: '英语一' })],
  })).rows(exam)[0].props.content, undefined);
});

test('the session menu only offers delete, decoupling focus records from note editing', async () => {
  const harness = screenHarness(snapshot({ sessions: [session({ note: '已写好的随笔' })] }));
  let tree = harness.render();
  harness.rows(tree)[0].props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  tree = harness.render();
  const menu = harness.menu(tree);
  assert.ok(menu, 'the row opens the anchored menu');
  assert.deepEqual(plain(menu.props.actions.map(action => action.label)), ['删除记录'],
    'editing note is decoupled from focus session menu');

  pressMenuAction(menu, '删除记录');
  const after = harness.render();
  assert.equal(harness.confirmation(after).props.visible, true, 'the existing confirmation opens');
  assert.deepEqual(plain(harness.calls), [], 'opening the confirmation must not delete a native record');

  const exam = screenHarness(snapshot({ sessions: [session({ isExam: true })] }));
  exam.rows(exam.render())[0].props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  assert.deepEqual(plain(exam.menu(exam.render()).props.actions.map(action => action.label)), ['删除记录'],
    'an exam also only has delete');
});

function openSessionDelete(harness) {
  harness.rows(harness.render())[0].props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  pressMenuAction(harness.menu(harness.render()), '删除记录');
  return {
    confirmation: () => harness.confirmation(harness.render()),
    confirm: () => nodes(harness.confirmation(harness.render()), node =>
      node.type === 'YanjiPrimaryButton' &&
      ['确认删除', '删除中…'].includes(node.props.label))[0],
    cancel: () => nodes(harness.confirmation(harness.render()), node =>
      node.type === 'Pressable' && node.props.accessibilityLabel === '取消删除记录')[0],
  };
}

test('session delete keeps confirmation and context pending with one same-tick request', async () => {
  const write = deferred();
  const harness = screenHarness(
    snapshot({ sessions: [session({ note: '待删除记录' })] }),
    { deleteSessionRecord: () => write.promise },
  );
  const confirmation = openSessionDelete(harness);

  const first = confirmation.confirm().props.onPress();
  const second = confirmation.confirm().props.onPress();
  await flush();

  assert.equal(confirmation.confirmation().props.visible, true,
    'pending must not close the confirmation');
  assert.equal(harness.calls.filter(call => call[0] === 'delete').length, 1,
    'same-tick confirmation taps produce one bridge request');
  const pendingButton = confirmation.confirm();
  assert.equal(pendingButton.props.label, '删除中…');
  assert.equal(pendingButton.props.disabled, true);
  assert.equal(confirmation.cancel().props.disabled, true,
    'the deletion target cannot be abandoned while its write is pending');

  write.resolve(true);
  await Promise.all([first, second]);
  await flush();
  assert.equal(confirmation.confirmation().props.visible, false,
    'only a resolved true removes the confirmation');
});

test('session delete false or rejection preserves the record and a retry succeeds', async () => {
  for (const failure of [false, new Error('room delete failed')]) {
    const write = deferred();
    let attemptNumber = 0;
    const harness = screenHarness(
      snapshot({ sessions: [session({ note: '失败后仍保留' })] }),
      {
        deleteSessionRecord: () => {
          attemptNumber++;
          return attemptNumber === 1 ? write.promise : Promise.resolve(true);
        },
      },
    );
    const confirmation = openSessionDelete(harness);

    const attempt = confirmation.confirm().props.onPress();
    await flush();
    if (failure instanceof Error) write.reject(failure);
    else write.resolve(failure);
    await attempt;
    await flush();

    assert.equal(confirmation.confirmation().props.visible, true,
      'failure keeps the record and its confirmation');
    assert.equal(harness.rows(harness.render()).length, 1,
      'the failed record remains in the timeline');
    assert.match(text(harness.render()), /删除失败，记录已保留/);
    assert.equal(confirmation.confirm().props.disabled, false, 'retry is available');
    assert.equal(confirmation.cancel().props.disabled, false);

    await confirmation.confirm().props.onPress();
    await flush();
    assert.equal(confirmation.confirmation().props.visible, false,
      'the retried true removes the confirmation');
  }
});

test('note rows keep favorite, resolution, category correction and editing in the anchored menu', async () => {
  const harness = screenHarness(snapshot({
    notes: [
      note({ id: 'doubt-1', content: '二叉树前序遍历与线索化区别', category: 'DOUBT' }),
      note({ id: 'insp-1', content: '今天背词效率很高', timestamp: at('16:20') }),
    ],
  }));
  const rows = harness.rows(harness.render());
  assert.deepEqual(plain(rows.map(row => row.props.tagLabel).sort()), ['疑点 待解决', '灵感'].sort());
  assert.deepEqual(plain(rows.map(row => row.props.tagIcon).sort()), ['doubt', 'inspiration']);
  assert.deepEqual(plain(rows.map(row => row.props.tagTone).sort()), ['brand', 'positive']);

  const doubtRow = rows.find(row => row.props.tagLabel === '疑点 待解决');
  doubtRow.props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  let menu = harness.menu(harness.render());
  assert.deepEqual(plain(menu.props.actions.map(action => action.label)),
    ['标记为已解决', '编辑记录', '删除记录']);

  pressMenuAction(menu, '标记为已解决');
  await flush();
  assert.deepEqual(plain(harness.calls), [['resolve', 'doubt-1']]);

  const inspRow = harness.rows(harness.render()).find(row => row.props.tagLabel === '灵感');
  inspRow.props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  menu = harness.menu(harness.render());
  assert.deepEqual(plain(menu.props.actions.map(action => action.label)),
    ['编辑记录', '删除记录']);

  harness.rows(harness.render()).find(row => row.props.tagLabel === '灵感')
    .props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  menu = harness.menu(harness.render());
  pressMenuAction(menu, '编辑记录');
  const editor = harness.recordModal(harness.render());
  assert.equal(editor.props.editingNote.id, 'insp-1');
  assert.equal(editor.props.editingNote.content, '今天背词效率很高');
  assert.equal(editor.props.editingNote.category, 'INSPIRATION',
    '编辑必须带上已存分类，否则保存时会把疑点悄悄改回灵感');
  assert.equal(editor.props.initialConfirmDelete, false);

  harness.rows(harness.render()).find(row => row.props.tagLabel === '灵感')
    .props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  pressMenuAction(harness.menu(harness.render()), '删除记录');
  assert.equal(harness.recordModal(harness.render()).props.initialConfirmDelete, true,
    'delete only opens the existing confirmation');
});

/**
 * The three summary numbers, in column order.
 *
 * Read by the summary metric size rather than the hero size: these three sit in
 * one row and are deliberately a step smaller than a single hero number.
 */
const summaryNumbers = tree => plain(nodes(tree, node => node.type === 'Text' &&
  node.props.style?.[0]?.fontSize === theme.paper.typography.metricSummary.fontSize)
  .map(node => text(node)));

/** The quiet denominators that share the row with their number. */
const summaryTails = tree => plain(nodes(tree, node => node.type === 'Text' &&
  node.props.style?.[0]?.fontSize === theme.paper.typography.captionMeta.fontSize)
  .map(node => text(node)).filter(value => value.startsWith('/')));

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
  // The denominator is no longer set at the number's size: 「1」 is the answer,
  // 「/2」 the context it is measured against.
  assert.deepEqual(summaryNumbers(tree), ['30', '1', '1']);
  assert.deepEqual(summaryTails(tree), ['/2'], 'the denominator rides the row at caption size');

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
  assert.deepEqual(summaryNumbers(empty), ['0', '0', '0']);
  assert.deepEqual(summaryTails(empty), ['/0']);

  const loading = screenHarness(null).render();
  assert.deepEqual(summaryNumbers(loading), ['—', '—', '—']);
  assert.doesNotMatch(text(loading), /分钟/, 'no unit is invented before the read returns');
});

test('the summary renders whichever day\'s snapshot is selected', () => {
  const first = screenHarness(snapshot({
    totalDurationSeconds: 600, focusCount: 1,
  })).render();
  assert.deepEqual(summaryNumbers(first), ['10', '0', '1']);

  const second = screenHarness(snapshot({
    totalDurationSeconds: 5400, focusCount: 4,
  })).render();
  assert.deepEqual(summaryNumbers(second), ['90', '0', '4']);
});


test('the timeline warns when the day it is showing may be behind Room', () => {
  // Same gap as Today: the review page kept its last good snapshot on a failed
  // read and gave no way to tell that the numbers were unconfirmed. It has a
  // snapshot, so the banner is the honest signal — and the rows stay readable.
  const h = screenHarness(snapshot({ totalDurationSeconds: 1800, focusCount: 1 }), { dailyReadFailed: true });
  const tree = h.render();
  const notice = nodes(tree, node => node.type === 'YanjiStaleDataNotice')[0];
  assert.ok(notice, 'a stale-but-present day must offer a retry');
  assert.match(notice.props.message, /这一天的记录可能不是最新/);
  assert.equal(notice.props.actionLabel, '重新读取');
  assert.equal(notice.props.busy, false);
  assert.match(text(tree), /专注/, 'the timeline content itself is not blanked');
});

test('a healthy review read shows no staleness banner', () => {
  const h = screenHarness(snapshot({ totalDurationSeconds: 1800, focusCount: 1 }));
  assert.equal(nodes(h.render(), node => node.type === 'YanjiStaleDataNotice').length, 0);
});

test('the timeline view renders through the virtualised list, not an inline map', () => {
  // A day can hold many sessions and notes; the rows used to be mapped straight
  // into the page's ScrollView, which mounted every offscreen row. The list is
  // now the scroll container, so the header rides `ListHeaderComponent` and the
  // rows are items — which is what lets the renderer recycle them.
  //
  // The double windows its cells, so this asserts both halves of the claim: the
  // list receives every entry (no truncation), and the first paint mounts only a
  // window of them (no eager mount of the whole day).
  const many = Array.from({ length: 30 }, (_, index) => note({
    id: `bulk-note-${index}`,
    content: `记录 ${index}`,
    timestamp: at('08:00') + index * 60_000,
  }));
  const h = screenHarness(snapshot({ sessions: [session({ id: 's1' })], notes: many }));
  const tree = h.render();
  const list = nodes(tree, node => node.type === 'FlashList')[0];
  assert.ok(list, 'the timeline view is a FlashList');
  assert.ok(list.props.ListHeaderComponent, 'the filmstrip and summary ride the header');
  assert.equal(typeof list.props.renderItem, 'function');
  assert.equal(list.props.testID, 'scroll-review',
    'the list is the page scroll container, not nested inside another one');

  const windowed = h.listRender();
  assert.equal(windowed.rowCount, 31, 'one session and thirty notes all reach the list');
  assert.ok(windowed.mounted < windowed.rowCount,
    `the first paint mounts a window, not the whole day (mounted ${windowed.mounted} of ${windowed.rowCount})`);
  assert.ok(windowed.mounted <= 8,
    `the initial mount is bounded by the viewport, not by entry count (was ${windowed.mounted})`);

  // Scrolling extends the window until every entry has been reached.
  let mounted = windowed.mounted;
  for (let pass = 0; pass < 12 && mounted < windowed.rowCount; pass += 1) {
    mounted = h.flashList.scroll(6, 'scroll-review');
  }
  assert.equal(mounted, windowed.rowCount, 'scrolling reaches the last entry — nothing is unreachable');

  // Switching to the trend view must leave the virtualised list behind: that
  // branch is a fixed set of cards, so it keeps the plain scroll view.
  const segmented = nodes(tree, node => node.type === 'YanjiPaperSegmentedControl')[0];
  segmented.props.onChange('trend');
  const trendTree = h.render();
  assert.equal(nodes(trendTree, node => node.type === 'FlashList').length, 0,
    'the trend view is not a virtualised list');
  assert.ok(nodes(trendTree, node => node.type === 'ScrollView').length > 0,
    'the trend view keeps its plain scroll view');
});

test('a review still loading shows no staleness banner', () => {
  // No snapshot yet means nothing is stale — the page is simply reading, which
  // `TimelineState` already reports. The banner must not double up on that.
  const h = screenHarness(null, { dailyReadFailed: true });
  assert.equal(nodes(h.render(), node => node.type === 'YanjiStaleDataNotice').length, 0);
});

test('the review retry re-reads the selected day and its overview window', () => {
  const h = screenHarness(snapshot({ totalDurationSeconds: 1800 }), { dailyReadFailed: true });
  const notice = nodes(h.render(), node => node.type === 'YanjiStaleDataNotice')[0];
  notice.props.onAction();
  // The banner repairs exactly what it warns about: the day's records and the
  // window being summarised, both re-read through the page's existing path.
  assert.deepEqual(h.refreshCalls, [['daily', date], ['review', 'TODAY', date]]);
});

/**
 * The filmstrip's drag handler, found by the container that owns it.
 *
 * The strip measures itself before it can page — a drag with no width has
 * nothing to translate against — so the double fires the layout pass the
 * renderer would have delivered first.
 */
function filmstripPan(tree) {
  const strip = nodes(tree, node => typeof node.props.onPanResponderRelease === 'function')[0];
  assert.ok(strip, 'the filmstrip owns a horizontal drag');
  strip.props.onLayout({ nativeEvent: { layout: { width: 350, height: 72 } } });
  return strip.props;
}

/**
 * The week in frame, as `{ start, cells }`.
 *
 * The strip lays three weeks side by side; only the middle page is on screen,
 * so a test must read that page rather than all 21 cells.
 */
function inFrameWeek(tree) {
  const pages = nodes(tree, node => typeof node.props.testID === 'string'
    && node.props.testID.startsWith('filmstrip-page-'));
  assert.ok(pages.length === 2 || pages.length === 3,
    `the strip lays out the weeks it has, not more (${pages.length})`);
  const framed = pages.find(page => page.props.accessibilityElementsHidden !== true);
  assert.ok(framed, 'one page is in frame');
  const cells = nodes(framed, node => node.type === 'Pressable' && node.props.accessibilityLabel?.startsWith('2026-'))
    .map(node => ({
      label: node.props.accessibilityLabel,
      selected: node.props.accessibilityState.selected,
      disabled: node.props.accessibilityState.disabled,
      onPress: node.props.onPress,
    }));
  return { start: framed.props.testID.slice('filmstrip-page-'.length), cells };
}

test('swiping the filmstrip pages the week without changing the selected day', () => {
  // 2026-10-06 is a Tuesday; its week is Mon 10-05 → Sun 10-11.
  const h = screenHarness(snapshot({ totalDurationSeconds: 1800, focusCount: 1 }));
  const before = h.render();
  const weekBefore = inFrameWeek(before);
  assert.equal(weekBefore.start, '2026-10-05', 'the strip opens on the selected week');
  assert.equal(weekBefore.cells.filter(cell => cell.selected).length, 1,
    'the selected day is marked in its own week');

  // A leftward drag would reveal the following week, but the page is already on
  // the current week: that direction is refused and the frame must not move.
  const pan = filmstripPan(before);
  pan.onPanResponderRelease({}, { dx: -80, dy: 4, vx: -0.9 });
  assert.equal(inFrameWeek(h.render()).start, '2026-10-05',
    'the current week is the forward ceiling');

  // A rightward drag goes back a week. The timeline keeps 10-06 — nothing is
  // selected in the week being shown, because paging is not a selection.
  pan.onPanResponderRelease({}, { dx: 80, dy: 4, vx: 0.9 });
  const after = h.render();
  const weekAfter = inFrameWeek(after);
  assert.equal(weekAfter.start, '2026-09-28', 'the strip rolled back one week');
  assert.equal(weekAfter.cells.filter(cell => cell.selected).length, 0,
    'a paged-back week holds no selected cell');
  assert.equal(nodes(after, node => node.type === 'TimelineCompactRow').length,
    nodes(before, node => node.type === 'TimelineCompactRow').length,
    'the timeline still lists the selected day, unchanged by the swipe');
});

test('tapping a day in a paged-back week selects it and brings the strip home', () => {
  const h = screenHarness(snapshot({ totalDurationSeconds: 1800, focusCount: 1 }));
  filmstripPan(h.render()).onPanResponderRelease({}, { dx: 80, dy: 4, vx: 0.9 });
  const paged = inFrameWeek(h.render());
  assert.equal(paged.start, '2026-09-28', 'the drag landed on the previous week');
  const cell = paged.cells.find(item => item.label.startsWith('2026-10-01'));
  assert.ok(cell, 'the paged-back week offers its own days');
  cell.onPress();

  const home = inFrameWeek(h.render());
  assert.equal(home.start, '2026-09-28', 'the strip stays on the week of the newly selected day');
  const chosen = home.cells.find(item => item.label.startsWith('2026-10-01'));
  assert.equal(chosen.selected, true, 'the tapped day is now the selection');
});

test('VIEWS expands to 3 options including journal library view', () => {
  const h = screenHarness(snapshot({ totalDurationSeconds: 1800, focusCount: 1 }));
  const tree = h.render();
  const segment = nodes(tree, node => node.type === 'YanjiPaperSegmentedControl' && node.props.accessibilityLabel === '回顾视图')[0];
  assert.ok(segment, 'segmented control for review view must be rendered');
  assert.equal(segment.props.options.length, 3);
  assert.equal(segment.props.options[0].value, 'timeline');
  assert.equal(segment.props.options[1].value, 'trend');
  assert.equal(segment.props.options[2].value, 'journal');
});
