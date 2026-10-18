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

const date = '2026-10-16';

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
  if (tree && typeof tree === 'object') return text(tree.props?.children);
  return typeof tree === 'string' || typeof tree === 'number' ? String(tree) : '';
}

function todayHarness(snapshot, nativeOverrides = {}) {
  const states = {};
  let index = 0;
  const calls = [];
  const names = [
    'YanjiBadge', 'YanjiCard', 'YanjiChronoSpectrum', 'YanjiEditorialHeader', 'YanjiEmptyLedgerRow',
    'YanjiHairline', 'YanjiIcon', 'YanjiIconButton', 'YanjiDayRhythmChart', 'YanjiPaperSectionHeading',
    'YanjiPaperSegmentedControl', 'YanjiPaperSheetHeading', 'YanjiPrimaryButton', 'YanjiSubjectProportionRow',
    'YanjiTaskLedgerRow', 'YanjiTextAction',
  ];
  const ui = Object.fromEntries(names.map(name => [name, name]));
  const { TodayScreen } = loadTypeScript('src/screens/TodayScreen.tsx', {
    react: {
      useState: initial => {
        const i = index++;
        if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
        return [states[i], next => { states[i] = typeof next === 'function' ? next(states[i]) : next; }];
      },
      useMemo: fn => fn(),
      useCallback: fn => fn,
      useRef: value => ({ current: value }),
      useEffect: () => {},
    },
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView', Modal: 'Modal',
    },
    'react-native-reanimated': { useReducedMotion: () => true },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom: 0 }) },
    '../bridge': {
      YanjiDataNative: {
        toggleNoteResolved: async (id) => {
          calls.push(['toggleNoteResolved', id]);
          return true;
        },
        ...nativeOverrides,
      },
    },
    '../components/YanjiUI': ui,
    '../components/RecordMomentModal': { RecordMomentModal: 'RecordMomentModal' },
    '../components/MomentTimelineItem': { MomentTimelineItem: 'MomentTimelineItem' },
    '../components/MomentActionSheet': { MomentActionSheet: 'MomentActionSheet' },
    '../components/TaskEditorModal': { TaskEditorModal: 'TaskEditorModal' },
    '../navigation/NavigationShell': {
      useNavigationActions: () => ({
        selectTab: tab => calls.push(['tab', tab]),
        requestFocusPreset: preset => calls.push(['preset', preset]),
        openSettings: () => {},
        openTaskEditor: () => {},
        closeTaskEditor: () => {},
      }),
      useNavigationState: () => ({ taskEditorOpen: false }),
    },
    '../navigation/useBottomTabLayout': { useBottomTabLayout: () => ({ contentPadding: 96 }) },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../state/dataRefresh': {
      useDailySnapshot: () => snapshot,
      useSettingsSnapshot: () => ({
        settings: { dailyGoalHours: 6 },
        examCountdown: { examDate: '2027-12-25', daysRemaining: 435 },
      }),
      refreshDailySnapshot: async () => {},
      refreshSettingsSnapshot: async () => {},
    },
    '../state/timerStore': {
      useActiveSession: () => null,
      useTimerTick: () => null,
      syncActiveSession: async () => {},
    },
  }, {
    Date: class extends Date {
      constructor(...args) {
        super(...(args.length ? args : [`${date}T15:00:00`]));
      }
    },
  });

  return {
    calls,
    render: () => {
      index = 0;
      return TodayScreen({ visible: true, foregroundRevision: 0 });
    },
  };
}

function reviewHarness(daySnapshot, nativeOverrides = {}) {
  const states = {};
  let index = 0;
  const calls = [];
  const names = [
    'YanjiBadge', 'YanjiCard', 'YanjiChronoSpectrum', 'YanjiEditorialHeader',
    'YanjiIcon', 'YanjiIconButton', 'YanjiLineChart', 'YanjiPaperSectionHeading',
    'YanjiPaperSegmentedControl', 'YanjiPaperSheetHeading', 'YanjiPrimaryButton',
    'YanjiStackedBarChart', 'YanjiSubjectProportionRow',
  ];
  const ui = Object.fromEntries(names.map(name => [name, name]));
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
        deleteSessionRecord: async () => true,
        toggleNoteResolved: async (id) => {
          calls.push(['toggleNoteResolved', id]);
          return true;
        },
        ...nativeOverrides,
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
  }, {
    Date: class extends Date {
      constructor(...args) {
        super(...(args.length ? args : [`${date}T18:00:00`]));
      }
    },
  });

  return {
    calls,
    render: () => {
      index = 0;
      return ReviewScreen({ visible: true });
    },
  };
}

test('TodayScreen hides 今日疑点 card when no doubts exist', () => {
  const harness = todayHarness({
    date,
    totalDurationSeconds: 1200,
    focusCount: 1,
    examCount: 0,
    sessions: [],
    notes: [
      {
        id: 'note-insp',
        content: '今天背了20个单词，感觉状态很好',
        date,
        timestamp: Date.now(),
        isFavorite: false,
        category: 'INSPIRATION',
        isResolved: false,
        resolvedAt: null,
      },
    ],
    tasks: [],
  });

  const tree = harness.render();
  const headings = nodes(tree, node => node.type === 'YanjiPaperSectionHeading' && node.props?.title === '今日疑点');
  assert.equal(headings.length, 0, 'When no doubts exist, 今日疑点 section heading must be absent');
});

test('TodayScreen renders 今日疑点 and allows toggling resolution when doubts exist', async () => {
  const harness = todayHarness({
    date,
    totalDurationSeconds: 1200,
    focusCount: 1,
    examCount: 0,
    sessions: [],
    notes: [
      {
        id: 'doubt-today-1',
        content: '为什么快速排序的最坏时间复杂度是O(n^2)？',
        date,
        timestamp: Date.now(),
        isFavorite: false,
        category: 'DOUBT',
        isResolved: false,
        resolvedAt: null,
      },
    ],
    tasks: [],
  });

  const tree = harness.render();
  const headings = nodes(tree, node => node.type === 'YanjiPaperSectionHeading' && node.props?.title === '今日疑点');
  assert.equal(headings.length, 1, 'When doubts exist, 今日疑点 section heading must appear');
  assert.equal(headings[0].props.countText, '0/1', 'Count should display resolved/total');

  const checkboxes = nodes(tree, node => node.type === 'Pressable' && node.props?.accessibilityRole === 'checkbox');
  assert.ok(checkboxes.length > 0, 'Checkbox for doubt resolution must exist');
  await checkboxes[0].props.onPress();
  assert.deepEqual(harness.calls, [['toggleNoteResolved', 'doubt-today-1']]);
});

test('ReviewScreen renders category badge and handles doubt resolution toggle', async () => {
  const harness = reviewHarness({
    date,
    totalDurationSeconds: 1200,
    focusCount: 1,
    examCount: 0,
    sessions: [],
    notes: [
      {
        id: 'doubt-rev-1',
        content: '二叉树前序遍历与线索化区别',
        date,
        timestamp: Date.now(),
        isFavorite: false,
        category: 'DOUBT',
        isResolved: false,
        resolvedAt: null,
      },
      {
        id: 'insp-rev-1',
        content: '今天背词效率很高，明天继续用艾宾浩斯',
        date,
        timestamp: Date.now() - 1000,
        isFavorite: false,
        category: 'INSPIRATION',
        isResolved: false,
        resolvedAt: null,
      },
    ],
    tasks: [],
  });

  const tree = harness.render();
  const badges = nodes(tree, node => node.type === 'YanjiBadge');
  const labels = badges.map(b => b.props?.label);
  assert.ok(labels.includes('待解决'), 'Review timeline must display 待解决 badge');
  assert.ok(labels.includes('灵感'), 'Review timeline must display 灵感 badge');

  const toggleButtons = nodes(tree, node => node.type === 'Pressable' && node.props?.accessibilityLabel?.includes('疑点待解决'));
  assert.ok(toggleButtons.length > 0, 'Toggle button for doubt resolution must exist');
  await toggleButtons[0].props.onPress();
  assert.deepEqual(harness.calls, [['toggleNoteResolved', 'doubt-rev-1']]);
});
