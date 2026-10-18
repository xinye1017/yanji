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

test('rhythm clips to each local hour, allocates paused spans faithfully, and aggregates dominant subject', () => {
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
  assert.equal(hour(12).seconds, 1200, 'paused session is faithfully allocated to its active hour');
  assert.equal(hour(13).seconds, 300, 'session with duration mismatch is faithfully allocated');
  assert.equal(hour(23).seconds, 900);
  assert.equal(logic.summarizeSubjects(sessions, theme.paper.colors.secondary).reduce((sum, item) => sum + item.seconds, 0),
    sessions.reduce((sum, item) => sum + item.durationSeconds, 0), 'timeline spans count toward saved investment');
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

function screenHarness(snapshot, bridge = {}) {
  const states = [];
  const calls = [];
  let index = 0;
  const names = ['YanjiBadge', 'YanjiCard', 'YanjiChronoSpectrum', 'YanjiDayRhythmChart', 'YanjiEditorialHeader', 'YanjiEmptyLedgerRow',
    'YanjiHairline', 'YanjiIcon', 'YanjiIconButton', 'YanjiPaperSectionHeading',
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
    '../bridge': { YanjiDataNative: bridge },
    '../components/YanjiUI': ui,
    '../components/RecordMomentModal': { RecordMomentModal: 'RecordMomentModal' },
    '../components/MomentTimelineItem': { MomentTimelineItem: 'MomentTimelineItem' },
    '../components/MomentActionSheet': { MomentActionSheet: 'MomentActionSheet' },
    '../components/TaskEditorModal': { TaskEditorModal: 'TaskEditorModal' },
    '../navigation/NavigationShell': {
      useNavigationActions: () => ({
        selectTab: tab => calls.push(['tab', tab]), requestFocusPreset: preset => calls.push(['preset', preset]),
        openSettings: () => calls.push(['openSettings']), openTaskEditor: () => {}, closeTaskEditor: () => {},
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
  // The expand control lives in the section heading's accessory slot, so the
  // walk has to enter that prop rather than the heading's children.
  const heading = () => nodes(tree, node => node.type === 'YanjiPaperSectionHeading' && node.props.title === '今日学习节奏')[0];
  const toggle = () => nodes(heading().props.right, node => node.type === 'Pressable')[0];
  toggle().props.onPress();
  tree = harness.render();
  assert.equal(nodes(tree, node => node.type === 'YanjiPaperSegmentedControl').length, 1);
  assert.equal(toggle().props.accessibilityState.expanded, true);
});

test('the expand control is named, and the name flips when it is collapsed', () => {
  const harness = screenHarness(snapshot());
  const heading = () => nodes(harness.render(), node => node.type === 'YanjiPaperSectionHeading' && node.props.title === '今日学习节奏')[0];
  const toggle = () => nodes(heading().props.right, node => node.type === 'Pressable')[0];
  assert.equal(toggle().props.accessibilityLabel, '展开今日学习节奏详情');
  toggle().props.onPress();
  assert.equal(toggle().props.accessibilityLabel, '收起今日学习节奏详情');
  toggle().props.onPress();
  assert.equal(toggle().props.accessibilityLabel, '展开今日学习节奏详情',
    'collapsing names the action that is available, not the state just left');
});

test('the rhythm section states its own window so the summary cannot overclaim', () => {
  const harness = screenHarness(snapshot());
  const tree = harness.render();
  // The collapsed section must name what it measures and what the strip means,
  // so the reader does not have to expand it to find out what the dots are.
  const heading = nodes(tree, node => node.type === 'YanjiPaperSectionHeading' && node.props.title === '今日学习节奏')[0];
  assert.ok(heading, 'the section must be a real section heading, not a bare caption row');
  const chart = nodes(tree, node => node.type === 'YanjiDayRhythmChart')[0];
  assert.equal(chart.props.startLabel, '06:00');
  assert.equal(chart.props.endLabel, '24:00');
  assert.equal(chart.props.buckets.length, 18);
  // The axis range is the section's scope. It sits inside a subtree that is
  // hidden from the a11y tree, so the chart must publish it separately or a
  // screen-reader user cannot learn it at all.
  assert.match(chart.props.accessibilityLabel ?? '', /06:00/);
  assert.match(chart.props.accessibilityLabel ?? '', /24:00/);
  assert.match(chart.props.accessibilityLabel, /暂无连续专注记录/);
  assert.doesNotMatch(text(tree), /暂无连续专注记录/);
});

test('the rhythm summary is spoken without displaying text below the chart', () => {
  const sessions = [session('a', '09:00', '09:40'), session('b', '14:00', '14:10')];
  const tree = screenHarness(snapshot({ sessions, totalDurationSeconds: 3000, focusCount: 2 })).render();
  const chart = nodes(tree, node => node.type === 'YanjiDayRhythmChart')[0];
  assert.match(chart.props.accessibilityLabel, /06:00 之后已确认 50m 连续专注/);
  assert.match(chart.props.accessibilityLabel, /最集中的是 09:00–10:00/);
  assert.doesNotMatch(text(tree), /已确认.*连续专注|最集中的是/);
});

test('a pre-dawn session is excluded from the strip and the summary says so', () => {
  // The window starts at 06:00. A 05:00 session is real saved focus, but it is
  // not in the strip, so the sentence must not read as if it covered the day.
  const sessions = [session('dawn', '05:00', '05:40'), session('morning', '09:00', '09:30')];
  const tree = screenHarness(snapshot({ sessions, totalDurationSeconds: 4200, focusCount: 2 })).render();
  const summary = nodes(tree, node => node.type === 'YanjiDayRhythmChart')[0].props.accessibilityLabel;
  assert.match(summary, /06:00 之后/, 'the summary must name its window');
  assert.match(summary, /30m/, 'only the in-window 30m is claimed, not the pre-dawn 40m');
});

test('an empty rhythm strip marks no hour at all', () => {
  // A lone "current hour" tick on a day with no data is the same unexplained
  // mark the section was rebuilt to remove, so nothing may be painted. This is
  // asserted as geometry (`rhythmSlots`) rather than by inspecting the rendered
  // style objects, so no test-only prop has to exist on the component.
  const empty = Array.from({ length: 18 }, (_, i) => ({ hour: i + 6, seconds: 0 }));
  const slots = shared.rhythmSlots(empty, 20, tokens.YanjiPaperLayout.rhythmChartHeight,
    tokens.YanjiPaperLayout.rhythmEmptyHeight);
  assert.equal(slots.length, 18, 'every hour keeps its slot so the axis stays a clock');
  assert.deepEqual(slots.filter(slot => slot.height > 0), [],
    'no hour may draw a mark on an empty day');
  assert.deepEqual(slots.filter(slot => slot.isNow), [],
    'and the current hour is not singled out when there is nothing to relate it to');
});

test('the chart publishes the axis range as a spoken node, not just a prop', () => {
  // The range sits inside a `no-hide-descendants` subtree as painted text, so it
  // only reaches a screen reader if the component itself renders an accessible
  // node carrying it. Asserting the prop TodayScreen passes would not catch that
  // node being removed, so this walks the rendered component.
  const buckets = Array.from({ length: 18 }, (_, i) => ({ hour: i + 6, seconds: 600 }));
  const chart = shared.YanjiDayRhythmChart({
    buckets, currentHour: 20, startLabel: '06:00', endLabel: '24:00',
    accessibilityLabel: '今日学习节奏，时间范围 06:00 至 24:00。已确认 3h。',
  });
  const spoken = nodes(chart, node => node.props?.accessible === true && typeof node.props?.accessibilityLabel === 'string')[0];
  assert.ok(spoken, 'the chart must render an accessible node for its range');
  assert.match(spoken.props.accessibilityLabel, /06:00/);
  assert.match(spoken.props.accessibilityLabel, /24:00/);
  assert.equal(spoken.props.accessibilityRole, 'text');
});

test('the current-hour tick appears once the strip has real data', () => {
  const buckets = Array.from({ length: 18 }, (_, i) => ({ hour: i + 6, seconds: 0 }));
  buckets[4] = { hour: 10, seconds: 1800, color: '#356AE6' };
  const slots = shared.rhythmSlots(buckets, 20, tokens.YanjiPaperLayout.rhythmChartHeight,
    tokens.YanjiPaperLayout.rhythmEmptyHeight);
  assert.deepEqual(slots.filter(slot => slot.height > 0).map(slot => slot.hour), [10, 20],
    'the measured hour and the current hour are the only marks');
  assert.deepEqual(slots.filter(slot => slot.isNow).map(slot => slot.hour), [20]);
  // The measured bar is full height: 1800s is this strip's scale floor.
  assert.equal(slots.find(slot => slot.hour === 10).height, tokens.YanjiPaperLayout.rhythmChartHeight);
});

test('production homepage keeps loading distinct from an empty saved day', () => {
  const tree = screenHarness(null).render();
  assert.match(text(tree), /正在读取今日记录/);
  assert.equal(nodes(tree, node => node.type === 'YanjiPaperSectionHeading')[0].props.countText, '—');
  assert.equal(nodes(tree, node => node.type === 'YanjiChronoSpectrum')[0].props.accessibilityLabel, '正在读取今日专注');
});

test('the goal is edited on the settings page, not in a page-owned sheet', () => {
  // Today used to open its own goal modal and write updateUserSettings itself,
  // so two screens could hold two drafts of one Room column. The route to
  // Settings is now the only path, and the page must own no goal sheet at all.
  const harness = screenHarness(snapshot());
  const tree = harness.render();
  const goalTrigger = nodes(tree, node => node.props.accessibilityLabel === '调整日常学习目标')[0];
  assert.ok(goalTrigger, 'the metric block still offers a way to change the goal');
  goalTrigger.props.onPress();
  assert.deepEqual(harness.calls, [['openSettings']],
    'the goal tap routes to the settings page and does nothing else');
  assert.equal(nodes(tree, node => node.type === 'Modal').length, 0,
    'Today must not render its own goal modal');
  assert.match(text(tree), /目标 06h 00m/, 'the metric still reports the stored goal');
});

test('the journal slip is an unboxed timeline section so the plan keeps the reading order', () => {
  // The journal slip no longer wraps in a lifted white card.
  // Instead, it is an unboxed timeline section with its own quiet action.
  const tree = screenHarness(snapshot()).render();
  const headings = nodes(tree, node => node.type === 'YanjiPaperSectionHeading');
  const journalHeading = headings.find(node => node.props.title === '记录此刻');
  assert.ok(journalHeading, 'the journal section has its own section heading');

  // 写此刻 is a secondary invitation; 新建任务 keeps the section accent. The actions
  // live in the headings' accessory slots, so they are read from that prop.
  const writeNow = nodes(journalHeading.props.right, node => node.type === 'YanjiTextAction')[0];
  assert.equal(writeNow.props.tone, 'quiet', '写此刻 must not compete with the primary action');
  const planHeading = headings.find(node => node.props.title === '今日计划');
  const planAdd = nodes(planHeading.props.right, node => node.type === 'YanjiTextAction')[0];
  assert.equal(planAdd.props.tone, undefined, 'the plan action keeps the section accent');


  // When empty, it renders an unboxed empty state prompt, not a lifted hero card.
  const cards = nodes(tree, node => node.type === 'YanjiCard');
  for (const card of cards) {
    assert.notEqual(card.props.variant, 'hero', 'no page-level card may be lifted above the plan');
  }
  assert.match(text(tree), /留一句想法，让今天有自己的注脚/);
});

test('the journal section renders notes in timeline style with timestamps and actions', () => {
  const note = {
    id: 'note-1',
    timestamp: at('10:30'),
    content: '长难句核心词汇总结完毕',
    category: 'INSPIRATION',
    isFavorite: true,
    isResolved: false,
  };
  const tree = screenHarness(snapshot({ notes: [note] })).render();
  const item = nodes(tree, node => node.type === 'MomentTimelineItem')[0];
  assert.equal(item.props.timeLabel, '10:30');
  assert.equal(item.props.content, note.content);
  assert.equal(item.props.isFavorite, true);
  assert.equal(item.props.categoryLabel, '灵感');
  assert.equal(item.props.isFirst, true);
  assert.equal(item.props.isLast, true);
  assert.equal(typeof item.props.onMore, 'function');
});

test('moment actions retain note identity, original date, persisted favorite and delete confirmation', async () => {
  const calls = [];
  const older = { id: 'older', date, timestamp: at('10:30'), content: '旧记录', category: 'INSPIRATION' };
  const latest = { ...older, id: 'latest', timestamp: at('17:56'), content: '最新记录', isFavorite: true };
  const hidden = { ...older, id: 'hidden', timestamp: at('09:00') };
  const h = screenHarness(snapshot({ notes: [older, hidden, latest] }), {
    toggleFavoriteNote: async id => { calls.push(['favorite', id]); return true; },
    deleteNote: async id => { calls.push(['delete', id]); return true; },
  });
  let tree = h.render();
  let items = nodes(tree, n => n.type === 'MomentTimelineItem');
  assert.deepEqual(items.map(n => n.props.content), ['最新记录', '旧记录']);
  assert.equal(items[0].props.isLast, false);
  assert.equal(items[1].props.isFirst, false);
  assert.equal(items[1].props.isLast, true);
  await items[1].props.onToggleFavorite();
  assert.deepEqual(calls, [['favorite', 'older']]);
  items[1].props.onMore();
  tree = h.render();
  let sheet = nodes(tree, n => n.type === 'MomentActionSheet')[0];
  assert.equal(sheet.props.visible, true);
  sheet.props.onEdit();
  tree = h.render();
  let editor = nodes(tree, n => n.type === 'RecordMomentModal')[0];
  assert.equal(editor.props.editingNote.id, 'older');
  assert.equal(editor.props.date, older.date);
  assert.equal(editor.props.initialConfirmDelete, false);
  editor.props.onClose();
  items = nodes(h.render(), n => n.type === 'MomentTimelineItem');
  items[0].props.onMore();
  sheet = nodes(h.render(), n => n.type === 'MomentActionSheet')[0];
  sheet.props.onDelete();
  tree = h.render();
  editor = nodes(tree, n => n.type === 'RecordMomentModal')[0];
  assert.equal(editor.props.editingNote.id, 'latest');
  assert.equal(editor.props.initialConfirmDelete, true);
  assert.equal(nodes(tree, n => n.type === 'MomentActionSheet')[0].props.visible, false);
  assert.deepEqual(calls, [['favorite', 'older']], 'menu delete only opens confirmation');
});

test('a morning focus session with pauses is properly displayed on the rhythm chart and day parts spectrum', () => {
  // Simulates a 30m session between 10:45 and 11:15 with 4 pauses
  const morningSession = session('morning-paused', '10:45', '11:15', {
    durationSeconds: 1800,
    pauseCount: 4,
    subjectId: 'ds',
    subjectName: '数据结构',
    subjectColor: '#2D7A5E',
  });
  const harness = screenHarness(snapshot({
    sessions: [morningSession],
    totalDurationSeconds: 1800,
    focusCount: 1,
  }));
  const tree = harness.render();

  // Rhythm chart has buckets allocated to hour 10 and hour 11
  const chart = nodes(tree, node => node.type === 'YanjiDayRhythmChart')[0];
  const h10 = chart.props.buckets.find(b => b.hour === 10);
  const h11 = chart.props.buckets.find(b => b.hour === 11);
  assert.ok(h10.seconds > 0, 'hour 10 must have allocated seconds');
  assert.ok(h11.seconds > 0, 'hour 11 must have allocated seconds');
  assert.equal(h10.seconds + h11.seconds, 1800, 'total allocated seconds across hours equals session duration');
  assert.equal(h10.color, '#2D7A5E', 'hour 10 takes the session subject color');
  assert.equal(h11.color, '#2D7A5E', 'hour 11 takes the session subject color');

  // The chart's spoken summary retains the measured focus time.
  assert.match(chart.props.accessibilityLabel, /06:00 之后已确认 30m 连续专注/);

  // Expanding rhythm details reveals the session in the morning spectrum
  const heading = nodes(tree, node => node.type === 'YanjiPaperSectionHeading' && node.props.title === '今日学习节奏')[0];
  const toggle = nodes(heading.props.right, node => node.type === 'Pressable')[0];
  toggle.props.onPress();
  const expandedTree = harness.render();
  const segmented = nodes(expandedTree, node => node.type === 'YanjiPaperSegmentedControl')[0];
  segmented.props.onChange('dayParts');
  const dayPartsTree = harness.render();

  // Morning spectrum has the segment
  assert.match(text(dayPartsTree), /上午/);
  assert.match(text(dayPartsTree), /06:00—12:00/);
  assert.match(text(dayPartsTree), /10:45 起 · 数据结构 · 30分钟/);
  // Must NOT display "未绘连续区段"
  assert.doesNotMatch(text(dayPartsTree), /未绘连续区段/);
});



