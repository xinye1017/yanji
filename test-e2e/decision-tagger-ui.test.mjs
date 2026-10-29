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
    motion: tokens.YanjiPaperMotion,
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
  const flashList = createFlashListDouble();
  const names = [
    'YanjiBadge', 'YanjiCaptureCard', 'YanjiCard', 'YanjiChronoSpectrum', 'YanjiEditorialHeader', 'YanjiEmptyLedgerRow',
    'YanjiHairline', 'YanjiIcon', 'YanjiIconButton', 'YanjiDayRhythmChart', 'YanjiPaperSectionHeading',
    'YanjiPaperSegmentedControl', 'YanjiPaperSheetHeading', 'YanjiPrimaryButton', 'YanjiSectionToggle',
    'YanjiSubjectProportionRow', 'YanjiStaleDataNotice', 'YanjiTaskLedgerRow', 'YanjiTextAction',
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
      // The filmstrip positions itself before paint; run it inline here.
      useLayoutEffect: fn => fn(),
    },
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView', Modal: 'Modal',
      useWindowDimensions: () => ({ width: 390, height: 844, fontScale: 1, scale: 2 }),
    },
    // Both pages now scroll through FlashList. The shared double mounts only a
    // window of cells, so these tests keep reading the rendered rows without
    // pretending that every row is mounted at once.
    '@shopify/flash-list': flashList,
    'react-native-reanimated': {
      Easing: { out: value => value, in: value => value, cubic: 'cubic' },
      runOnJS: callback => callback,
      useAnimatedStyle: style => style(),
      useReducedMotion: () => true,
      useSharedValue: initial => ({ value: initial }),
      withTiming: value => value,
      default: { View: 'AnimatedView' },
    },
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
    '../components/SettingsSheets': { DailyGoalSheet: 'DailyGoalSheet' },
    '../components/TodayScheduleCard': { TodayScheduleCard: 'TodayScheduleCard' },
    '../components/RecordMomentModal': { RecordMomentModal: 'RecordMomentModal' },
    '../components/LiveSessionReadout': { LiveSessionReadout: 'LiveSessionReadout', LiveSessionCapsule: 'LiveSessionCapsule' },
    '../components/RoamingNotesModal': { RoamingNotesModal: 'RoamingNotesModal' },
    './review/JournalLibraryView': { JournalLibraryView: 'JournalLibraryView' },
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
      useDailyReadFailed: () => false,
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
  const flashList = createFlashListDouble();
  const names = [
    'YanjiBadge', 'YanjiCaptureCard', 'YanjiCard', 'YanjiChronoSpectrum', 'YanjiEditorialHeader',
    'YanjiIcon', 'YanjiIconButton', 'YanjiLineChart', 'YanjiPaperSectionHeading',
    'YanjiPaperSegmentedControl', 'YanjiPaperSheetHeading', 'YanjiPrimaryButton',
    'YanjiStackedBarChart', 'YanjiStaleDataNotice', 'YanjiSubjectProportionRow',
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
      useRef: value => ({ current: value }),
      useEffect: () => {},
      // The filmstrip positions itself before paint; run it inline here.
      useLayoutEffect: fn => fn(),
    },
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView', Modal: 'Modal',
      useWindowDimensions: () => ({ fontScale: 1, width: 390, height: 844 }),
      PanResponder: { create: config => ({ panHandlers: config }) },
    },
    // Same windowed double as the Today harness.
    '@shopify/flash-list': flashList,
    'react-native-reanimated': {
      default: { View: 'Animated.View' },
      useSharedValue: initial => ({ value: initial }),
      useAnimatedStyle: style => style(),
      withTiming: to => to,
      runOnUI: fn => (...args) => fn(...args),
      useReducedMotion: () => true,
    },
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
    '../components/TodayScheduleCard': { TodayScheduleCard: 'TodayScheduleCard' },
    '../components/RecordMomentModal': { RecordMomentModal: 'RecordMomentModal' },
    '../components/LiveSessionReadout': { LiveSessionCapsule: 'LiveSessionCapsule', LiveSessionReadout: 'LiveSessionReadout' },
    '../components/RoamingNotesModal': { RoamingNotesModal: 'RoamingNotesModal' },
    './review/JournalLibraryView': { JournalLibraryView: 'JournalLibraryView' },
    '../components/SessionNoteModal': { SessionNoteModal: 'SessionNoteModal' },
    '../components/TimelineCompactRow': { TimelineCompactRow: 'TimelineCompactRow' },
    '../components/TimelineActionPopover': { TimelineActionPopover: 'TimelineActionPopover' },
    '../navigation/NavigationShell': {
      useNavigationActions: () => ({ openSettings: () => {}, selectTab: () => {} }),
    },
    '../navigation/useBottomTabLayout': { useBottomTabLayout: () => ({ contentPadding: 96 }) },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../state/dataRefresh': {
      useDailySnapshot: () => daySnapshot,
      useDailyReadFailed: () => false,
      useSettingsSnapshot: () => ({ settings: { dailyGoalHours: 6 } }),
      useReviewOverview: () => null,
      useReviewReadFailed: () => false,
      useReviewDataVisibility: () => {},
      refreshDailySnapshot: async () => {},
      refreshReviewOverview: async () => {},
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

test('TodayScreen renders doubts inside 今日随笔 without redundant 今日疑点 section', async () => {
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
  const headings = nodes(tree, node => node.type === 'YanjiPaperSectionHeading' && node.props?.title === '今日疑点');
  assert.equal(headings.length, 0, '今日疑点 section heading must be absent; doubts are displayed in 今日随笔');

  const rows = nodes(tree, node => node.type === 'TimelineCompactRow');
  assert.equal(rows.length, 1);
  assert.equal(rows[0].props.tagLabel, '疑点 待解决');
  assert.equal(rows[0].props.tagTone, 'brand');
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
  assert.ok(tags.includes('疑点 待解决'), 'the doubt row carries its state tag');
  assert.ok(tags.includes('灵感'), 'the inspiration row carries its category tag');
  assert.deepEqual(rows.map(row => row.props.tagTone).sort(), ['brand', 'positive']);

  // The row itself no longer toggles resolution: the anchored menu does.
  const doubtRow = rows.find(row => row.props.tagLabel === '疑点 待解决');
  doubtRow.props.action.onOpen({ x: 10, y: 100, width: 44, height: 44 });
  const menu = nodes(harness.render(), node => node.type === 'TimelineActionPopover')[0];
  const resolve = menu.props.actions.find(action => action.label === '标记为已解决');
  assert.ok(resolve, 'the menu offers the resolution action');
  menu.props.onClose();
  await resolve.onPress();
  assert.deepEqual(harness.calls, [['toggleNoteResolved', 'doubt-rev-1']]);
});
