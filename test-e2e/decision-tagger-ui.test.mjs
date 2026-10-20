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
      useWindowDimensions: () => ({ width: 390, height: 844, fontScale: 1, scale: 2 }),
    },
    'react-native-reanimated': { useReducedMotion: () => true },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ top: 24, bottom: 0 }) },
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
    '../components/TimelineCompactRow': { TimelineCompactRow: 'TimelineCompactRow' },
    '../components/TimelineActionPopover': { TimelineActionPopover: 'TimelineActionPopover' },
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
    focusParticipationCount: 1,
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
    focusParticipationCount: 1,
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
  // A doubt is a *state of an entry*, not a second list. It used to render twice
  // — once inside 「今日疑点」 and once in the journal — so the section is gone and
  // the entry itself carries the state instead.
  const doubtHeadings = nodes(tree, node => node.type === 'YanjiPaperSectionHeading' && node.props?.title === '今日疑点');
  assert.equal(doubtHeadings.length, 0, '疑点 is a tag on an entry, so the standalone section must not exist');

  const row = nodes(tree, node => node.type === 'TimelineCompactRow')[0];
  assert.ok(row, 'the open doubt still appears, as a journal row');
  assert.match(row.props.tagLabel, /疑点 · 待解决/);
  assert.equal(row.props.tagTone, 'doubt', 'a waiting state uses the doubt family, not the action accent');
  assert.equal(row.props.content, '为什么快速排序的最坏时间复杂度是O(n^2)？');
});

test('ReviewScreen tags doubts/inspirations and resolves them through the anchored menu', async () => {
  const harness = reviewHarness({
    date,
    totalDurationSeconds: 1200,
    focusCount: 1,
    focusParticipationCount: 1,
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
  const rows = nodes(tree, node => node.type === 'TimelineCompactRow');
  const tags = rows.map(row => row.props.tagLabel);
  assert.ok(tags.includes('疑点 · 待解决'), 'the doubt row carries its state tag');
  assert.ok(tags.includes('灵感'), 'the inspiration row carries its category tag');
  // 疑点 is a waiting state (doubt family); 灵感 is a category, so it takes the
  // neutral family. The action accent is never used for a state tag.
  assert.deepEqual(rows.map(row => row.props.tagTone).sort(), ['doubt', 'neutral']);

  // The row itself no longer toggles resolution: the anchored menu does.
  const doubtRow = rows.find(row => row.props.tagLabel === '疑点 · 待解决');
  doubtRow.props.onMore({ x: 10, y: 100, width: 44, height: 44 });
  const menu = nodes(harness.render(), node => node.type === 'TimelineActionPopover')[0];
  const resolve = menu.props.actions.find(action => action.label === '标记为已解决');
  assert.ok(resolve, 'the menu offers the resolution action');
  menu.props.onClose();
  await resolve.onPress();
  assert.deepEqual(harness.calls, [['toggleNoteResolved', 'doubt-rev-1']]);
});
