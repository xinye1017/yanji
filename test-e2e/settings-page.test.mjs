import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

/**
 * The settings page's own contract: what it prints for a stored value, and what
 * it writes when the user changes one.
 *
 * The logic module runs as shipped TypeScript; the screen is rendered with the
 * same hook harness the other screen tests use, so the assertions are against
 * real rendered props rather than a copy of the source.
 */

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

/**
 * The day every assertion is written against.
 *
 * `todayIso()` lives in the logic module, and the countdown text is derived from
 * it, so the logic module needs the same frozen clock the screen gets. Without
 * this the suite reads the real wall clock and the expected 「距初试 N 天」
 * changes at midnight — it would be green on one day and red the next.
 */
const FIXED_NOW = '2026-10-17T12:00:00';
const FrozenDate = class extends Date {
  constructor(...args) { super(...(args.length ? args : [FIXED_NOW])); }
  static now() { return new Date(FIXED_NOW).getTime(); }
};

const logic = loadTypeScript('src/state/settingsViewLogic.ts', {}, { Date: FrozenDate });

/**
 * Every element matching `predicate`, descending through rendered components.
 *
 * Component functions are invoked **once per distinct element** and the result
 * is cached: the harness's `useState` stub advances a shared slot counter, so
 * re-invoking a component while walking would re-run its hooks against the next
 * slots and read somebody else's state.
 */
function nodes(tree, predicate) {
  const result = [];
  const rendered = new Map();
  function walk(node) {
    if (Array.isArray(node)) { node.forEach(walk); return; }
    if (!node || typeof node !== 'object' || !('type' in node)) return;
    if (predicate(node)) result.push(node);
    if (typeof node.type === 'function') {
      if (!rendered.has(node)) rendered.set(node, node.type(node.props));
      walk(rendered.get(node));
      return;
    }
    walk(node.props.children);
  }
  walk(tree);
  return result;
}
const byComponent = name => node => typeof node.type === 'function' && node.type.name === name;

/**
 * Call arguments and returned values cross the VM boundary the shipped screen
 * runs in, so their objects carry that realm's prototype. Compare the payload,
 * not the identity.
 */
const plain = value => JSON.parse(JSON.stringify(value));

/**
 * Everything the page's own scroll body renders, excluding the two sheets.
 *
 * The walker is given the body's children directly and a predicate that matches
 * every node; returning the materialised list is what lets callers then search
 * it. (An earlier version passed `() => false`, which made the walker collect
 * nothing at all — so the "no full-width return button" assertion could never
 * fail. See the test that now proves this helper can see a button.)
 */
function pageBody(tree) {
  const scroll = nodes(tree, node => node.props?.testID === 'scroll-settings')[0];
  assert.ok(scroll, 'the settings page renders its own scroll body');
  return nodes({ type: 'Body', props: { children: scroll.props.children } }, () => true);
}

// ---------------------------------------------------------------------------
// Pure logic
// ---------------------------------------------------------------------------

test('an ISO date round-trips only when it is a real calendar day', () => {
  assert.deepEqual(plain(logic.parseIsoDate('2026-12-19')), { year: 2026, month: 12, day: 19 });
  assert.equal(logic.parseIsoDate('2026-02-31'), null, 'a rolled-over day is not a date');
  assert.equal(logic.parseIsoDate('2026-13-01'), null);
  assert.equal(logic.parseIsoDate(''), null);
  assert.equal(logic.parseIsoDate('2026/12/19'), null);
  assert.equal(logic.formatIsoDate({ year: 2026, month: 2, day: 9 }), '2026-02-09');
});

test('countdown distinguishes a future exam, the exam day, and a past date', () => {
  assert.equal(logic.examCountdownText('2026-12-19', '2026-10-17'), '距初试 63 天');
  assert.equal(logic.examCountdownText('2026-10-17', '2026-10-17'), '今天就是初试日');
  // The native payload clamps at 0 and so cannot tell these two apart.
  assert.equal(logic.examCountdownText('2026-10-01', '2026-10-17'), '已过设定日期 16 天');
  assert.equal(logic.examCountdownText('', '2026-10-17'), null);
});

test('a stored date prints as a Chinese date and an absent one prints as 未设置', () => {
  assert.equal(logic.formatExamDate('2026-12-19'), '2026年12月19日');
  assert.equal(logic.formatExamDate(''), '未设置');
  assert.equal(logic.formatExamDate('not-a-date'), '未设置');
  assert.equal(logic.weekdayLabelOf('2026-12-19'), '周六');
});

test('the goal reads back at the precision it was stored with', () => {
  assert.equal(logic.formatGoalDuration(6), '6 小时');
  assert.equal(logic.formatGoalDuration(6.5), '6 小时 30 分钟');
  assert.equal(logic.formatGoalDuration(0.5), '30 分钟');
  assert.equal(logic.formatGoalDuration(0), '未设置');
  assert.equal(logic.formatGoalDuration(undefined), '未设置');
  // Minutes are the sheet's unit; hours are the column's unit. Round-tripping
  // must not drift a 30-minute step into 29 or 31 minutes.
  assert.equal(logic.formatGoalDuration(logic.goalHoursFromMinutes(390)), '6 小时 30 分钟');
  assert.equal(logic.goalHoursFromMinutes(-30), 0);
});

test('the month grid is Monday-first and never changes height between months', () => {
  const december = logic.buildMonthGrid({ year: 2026, month: 12 });
  assert.equal(december[0][1], 1, '2026-12-01 is a Tuesday, so Monday stays blank');
  assert.ok(december.every(week => week.length === 7));
  const february = logic.buildMonthGrid({ year: 2027, month: 2 });
  assert.equal(february.flat().filter(day => day !== null).length, 28);
  const leapFebruary = logic.buildMonthGrid({ year: 2028, month: 2 });
  assert.equal(leapFebruary.flat().filter(day => day !== null).length, 29);

  // The sheet must not resize while paging. A 28-day February that starts on a
  // Monday fits in four rows, which is why this asserts the *floor* rather than
  // only the multiple-of-seven padding.
  const rowCounts = new Set();
  for (let year = 2024; year <= 2040; year++) {
    for (let month = 1; month <= 12; month++) {
      rowCounts.add(logic.buildMonthGrid({ year, month }).length);
    }
  }
  assert.deepEqual([...rowCounts].sort(), [5, 6],
    'every month renders five or six rows, so the sheet height is stable');
});

test('month paging crosses year boundaries in both directions', () => {
  assert.deepEqual(plain(logic.shiftMonth({ year: 2026, month: 1 }, -1)), { year: 2025, month: 12 });
  assert.deepEqual(plain(logic.shiftMonth({ year: 2026, month: 12 }, 1)), { year: 2027, month: 1 });
  assert.equal(logic.formatMonthTitle({ year: 2026, month: 12 }), '2026 年 12 月');
});

// ---------------------------------------------------------------------------
// Rendered screen
// ---------------------------------------------------------------------------

function settingsHarness({ settings, calls = [] } = {}) {
  const states = {};
  let index = 0;
  /** The advanced section's own slot counter; see the note where it is loaded. */
  let sectionIndex = 0;
  const names = [
    'YanjiBadge', 'YanjiCard', 'YanjiChronoSpectrum', 'YanjiEditorialHeader', 'YanjiHairline',
    'YanjiIcon', 'YanjiIconButton', 'YanjiPaperSectionHeading', 'YanjiPaperSegmentedControl',
    'YanjiPaperSheetHeading', 'YanjiPrimaryButton', 'YanjiTextAction', 'YanjiTextInput',
  ];
  const ui = Object.fromEntries(names.map(name => [name, name]));
  // The page reserves real IME height so the advanced form stays reachable;
  // the harness has no keyboard, so it reserves nothing.
  ui.useYanjiKeyboardInset = () => 0;
  // Loaded once and shared: the advanced section renders the real disclosure
  // row, so it needs the same component instance the page would get.
  const rows = loadTypeScript('src/components/SettingsRows.tsx', {
    react: { useState: () => [null, () => {}], useEffect: () => {} },
    'react-native': { View: 'View', Text: 'Text', Pressable: 'Pressable' },
    './YanjiUI': ui,
    './icons': { getYanjiIcon: name => name, YANJI_ICON_STROKE: 1.75 },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../theme/tokens': tokens,
  });
  const { SettingsScreen } = loadTypeScript('src/screens/SettingsScreen.tsx', {
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
    },
    'react-native-reanimated': { useReducedMotion: () => true },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom: 0, top: 0 }) },
    '../bridge': {
      YanjiDataNative: {
        updateUserSettings: async patch => { calls.push(['updateUserSettings', patch]); return true; },
        saveDecisionApiKey: async () => true,
        clearDecisionApiKey: async () => true,
        getDecisionApiKey: async () => '',
      },
      YanjiThemeNative: {
        getThemePreference: async () => ({ mode: 'SYSTEM', isDark: false }),
        setThemePreference: async mode => { calls.push(['setThemePreference', mode]); return true; },
      },
      onThemeChanged: () => () => {},
    },
    '../components/YanjiUI': ui,
    '../components/SettingsRows': rows,
    '../components/SettingsSheets': loadTypeScript('src/components/SettingsSheets.tsx', {
      react: {
        useState: initial => {
          const i = 1000 + index++;
          if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
          return [states[i], next => { states[i] = typeof next === 'function' ? next(states[i]) : next; }];
        },
        useEffect: () => {},
      },
      'react-native': {
        View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView', Modal: 'Modal',
      },
      'react-native-reanimated': { useReducedMotion: () => true },
      'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom: 0 }) },
      './YanjiUI': ui,
      '../theme/ThemeProvider': { useYanjiTheme: () => theme },
      '../theme/tokens': tokens,
      '../state/settingsViewLogic': logic,
    }),
    // The advanced block owns its own form state; the page only composes it, so
    // the test loads the real component with the same stubs it needs. Its state
    // slots are keyed by *slot order within one render of that component*, which
    // is why the counter is reset by `render()` and not on every re-render.
    '../components/DecisionModelSection': loadTypeScript('src/components/DecisionModelSection.tsx', {
      react: {
        useState: initial => {
          const i = 2000 + sectionIndex++;
          if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
          return [states[i], next => { states[i] = typeof next === 'function' ? next(states[i]) : next; }];
        },
        useEffect: () => {},
      },
      'react-native': { View: 'View', Text: 'Text', Pressable: 'Pressable' },
      './YanjiUI': ui,
      './SettingsRows': rows,
      '../bridge': {
        YanjiDataNative: {
          updateUserSettings: async patch => { calls.push(['updateUserSettings', patch]); return true; },
          saveDecisionApiKey: async () => true,
          clearDecisionApiKey: async () => true,
          getDecisionApiKey: async () => '',
        },
      },
      '../services/decisionClient': {
        testDecisionConnection: async () => ({ success: true, message: 'ok' }),
        toBaseUrl: (url, provider) => {
          if (!url) return provider === 'typesafe' ? 'https://api.typesafe.ai/v1' : 'https://openrouter.ai/api/v1';
          return url.replace(/\/api\/alpha\/decisions$/, '/api/v1').replace(/\/v1\/systemone$/, '/v1');
        },
      },
      '../theme/ThemeProvider': { useYanjiTheme: () => theme },
      '../theme/tokens': tokens,
    }),
    '../navigation/NavigationShell': {
      useNavigationActions: () => ({ closeSettings: () => calls.push(['closeSettings']) }),
    },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../theme/tokens': tokens,
    '../state/dataRefresh': {
      // `null` models the window before the bridged settings read returns.
      useSettingsSnapshot: () => (settings === null ? null : {
        settings,
        examCountdown: { examDate: settings.examDate, daysRemaining: 0 },
      }),
      refreshSettingsSnapshot: async () => {},
    },
    '../services/decisionClient': { testDecisionConnection: async () => ({ success: true, message: 'ok' }) },
    '../state/settingsViewLogic': logic,
  }, {
    Date: FrozenDate,
  });

  return {
    calls,
    setState: (slot, value) => { states[slot] = value; },
    render: () => {
      index = 0;
      sectionIndex = 0;
      return SettingsScreen();
    },
  };
}

const stored = (changes = {}) => ({
  examDate: '2026-12-19',
  targetSchool: '',
  targetMajor: '',
  themePreference: 'SYSTEM',
  dailyGoalHours: 6.5,
  hasDecisionApiKey: false,
  ...changes,
});

test('settings prints the stored exam date, countdown and daily goal', () => {
  const harness = settingsHarness({ settings: stored() });
  const tree = harness.render();

  const rows = nodes(tree, byComponent('YanjiSettingRow'));
  assert.equal(rows.length, 2, 'the page has exactly two editable value rows');
  assert.equal(rows[0].props.label, '考试日期');
  assert.equal(rows[0].props.value, '2026年12月19日');
  assert.equal(rows[0].props.hint, '距初试 63 天');
  assert.equal(rows[1].props.label, '每日学习时长');
  assert.equal(rows[1].props.value, '6 小时 30 分钟');
});

test('every settings group title uses the group scale', () => {
  const tree = settingsHarness({ settings: stored() }).render();
  const headings = nodes(tree, node => node.type === 'YanjiPaperSectionHeading');
  assert.deepEqual(headings.map(heading => heading.props.title), ['备考', '外观', '高级']);
  for (const heading of headings) {
    assert.deepEqual(
      plain(heading.props.titleStyle),
      plain(theme.paper.typography.titleGroup),
      `${heading.props.title} must use the settings group scale`
    );
  }
});

test('the theme switch shares the 外观 heading row as a compact control', () => {
  const tree = settingsHarness({ settings: stored() }).render();
  const heading = nodes(tree, node => node.type === 'YanjiPaperSectionHeading' && node.props?.title === '外观')[0];
  assert.ok(heading, 'the page keeps an 外观 group');
  assert.equal(heading.props.align, 'center', 'the title centres against the control');
  const control = heading.props.right;
  assert.equal(control.type, 'YanjiPaperSegmentedControl', 'the control is the heading accessory');
  assert.equal(control.props.compact, true, 'a settings group uses the compact segment');
  assert.equal(control.props.value, 'SYSTEM');
});

test('an unset goal and exam date read as 未设置 rather than as a zero', () => {
  const tree = settingsHarness({
    settings: stored({ examDate: '', dailyGoalHours: 0 }),
  }).render();
  const rows = nodes(tree, byComponent('YanjiSettingRow'));
  assert.equal(rows[0].props.value, '未设置');
  assert.equal(rows[1].props.value, '未设置');
  // No countdown line is invented for a date that does not exist.
  assert.doesNotMatch(rows[0].props.hint ?? '', /天/);
});

test('a settings snapshot that has not arrived yet is not reported as 未设置', () => {
  // The first device screenshot of this page showed 「未设置」 for both rows
  // before the bridged read returned, while the stored values were in fact
  // 2026-12-19 and 8 hours. "Not read yet" and "not set" are different facts.
  const tree = settingsHarness({ settings: null }).render();
  const rows = nodes(tree, byComponent('YanjiSettingRow'));
  assert.equal(rows.length, 2);

  for (const row of rows) {
    assert.equal(row.props.pending, true, 'a pending row must say so');
    // Render the row itself: the placeholder is chosen inside it, so the
    // assertion is about what the user actually sees.
    const rendered = nodes(row.type(row.props), node => node.type === 'Text');
    const shown = rendered.map(node => String(node.props.children ?? '')).join('|');
    assert.doesNotMatch(shown, /未设置/, `${row.props.label} must not claim it is unset while loading`);
    assert.match(shown, /—/, `${row.props.label} shows a placeholder instead`);
  }
});

test('the advanced summary does not claim 未配置 before the snapshot arrives', () => {
  // The disclosure is the same kind of claim as the rows above: while the
  // bridged read is still in flight it must show the placeholder, not a
  // verdict about data the page has not seen yet.
  const tree = settingsHarness({ settings: null }).render();
  const disclosure = nodes(tree, byComponent('YanjiSettingDisclosureRow'))[0];
  assert.ok(disclosure, 'the advanced block still publishes its disclosure');
  assert.equal(disclosure.props.summary, '—');
});

test('the advanced block is collapsed until it is opened', () => {
  const harness = settingsHarness({ settings: stored() });

  /**
   * Walk the rendered tree exactly once.
   *
   * `nodes()` re-invokes component functions as it descends, and the harness's
   * `useState` stub advances a slot counter per invocation. Walking the same
   * tree twice would therefore hand the advanced block a different set of slots
   * on the second pass, so every assertion here reads one snapshot.
   */
  const snapshot = tree => {
    const found = { sections: [], disclosures: 0, inputs: 0 };
    const seen = new Set();
    const walk = node => {
      if (Array.isArray(node)) { node.forEach(walk); return; }
      if (!node || typeof node !== 'object' || !('type' in node)) return;
      if (typeof node.type === 'function' && node.type.name === 'YanjiDecisionModelSection') found.sections++;
      if (typeof node.type === 'string' && node.type === 'YanjiTextInput') found.inputs++;
      if (typeof node.type === 'function') {
        if (seen.has(node)) return;
        seen.add(node);
        walk(node.type(node.props));
        return;
      }
      // A disclosure rendered by the block is a real component element; capture
      // it so the test can press it.
      if (typeof node.type === 'function' && node.type.name === 'YanjiSettingDisclosureRow') {
        found.disclosures++;
        found.disclosure = node;
      }
      walk(node.props.children);
    };
    walk(tree);
    return found;
  };

  const collapsed = snapshot(harness.render());
  assert.equal(collapsed.sections, 1, 'the page must render the advanced section');
  assert.equal(collapsed.inputs, 0, 'the decision-model form is not built while collapsed');

  const collapsedTree = harness.render();
  const disclosure = nodes(collapsedTree, byComponent('YanjiSettingDisclosureRow'))[0];
  assert.ok(disclosure, 'the block must publish exactly one disclosure row');
  assert.equal(disclosure.props.expanded, false);
  assert.equal(disclosure.props.summary, '未配置');

  // Drive the real control instead of poking a hook slot: the disclosure's own
  // press handler is what toggles it, so adding or reordering the page's hooks
  // cannot silently point this test at the wrong state.
  disclosure.props.onPress();

  const opened = snapshot(harness.render());
  assert.ok(opened.inputs >= 3, 'model, endpoint and API key fields appear once expanded');
});

test('saving the exam date writes the chosen ISO day and nothing else', async () => {
  const harness = settingsHarness({ settings: stored({ examDate: '' }) });
  const sheets = () => nodes(harness.render(), byComponent('ExamDateSheet'));
  assert.equal(sheets().length, 1);
  assert.equal(sheets()[0].props.visible, false, 'the sheet starts closed');

  // Open the sheet through the row that owns it, so the test exercises the real
  // path instead of depending on where `openSheet` sits in the hook order.
  const rows = nodes(harness.render(), byComponent('YanjiSettingRow'));
  rows[0].props.onPress();
  assert.equal(sheets()[0].props.visible, true, 'the exam-date row opens its sheet');

  await sheets()[0].props.onSave('2026-12-26');
  assert.deepEqual(plain(harness.calls.filter(call => call[0] === 'updateUserSettings')),
    [['updateUserSettings', { examDate: '2026-12-26' }]]);
});

test('the goal stepper and presets are what actually decide the saved value', async () => {
  // Every other save test calls `onSave(minutes)` with a number it chose. Those
  // never exercise the controls that produce the number, so a broken stepper
  // would still look green. This drives the real buttons.
  const harness = settingsHarness({ settings: stored({ dailyGoalHours: 3 }) });
  const sheet = () => nodes(harness.render(), byComponent('DailyGoalSheet'))[0];

  // The value starts at the stored 3h. One "+30 分钟" makes it 3h30m.
  const plus = () => nodes(sheet(), node => node.type === 'YanjiIconButton' && node.props.accessibilityLabel === '增加 30 分钟')[0];
  plus().props.onPress();
  await nodes(sheet(), node => node.type === 'YanjiPrimaryButton')[0].props.onPress();

  const writes = harness.calls.filter(call => call[0] === 'updateUserSettings');
  assert.equal(writes.length, 1);
  assert.equal(writes[0][1].dailyGoalHours, 3.5, 'the stepper value is what gets stored');
});

test('a goal preset sets the stepper value instead of saving on its own', async () => {
  // One source of truth for "what will be saved": the preset is a shortcut into
  // the stepper, not a second writer.
  const harness = settingsHarness({ settings: stored({ dailyGoalHours: 1 }) });
  const sheet = () => nodes(harness.render(), byComponent('DailyGoalSheet'))[0];
  const preset = () => nodes(sheet(), node => node.props?.accessibilityLabel === '设为 8 小时')[0];
  preset().props.onPress();
  assert.deepEqual(harness.calls.filter(call => call[0] === 'updateUserSettings'), [],
    'picking a preset must not write anything by itself');

  await nodes(sheet(), node => node.type === 'YanjiPrimaryButton')[0].props.onPress();
  const writes = harness.calls.filter(call => call[0] === 'updateUserSettings');
  assert.equal(writes[0][1].dailyGoalHours, 8, 'the selected preset is what gets stored');
});

test('the exam-date sheet pages months without changing the selection', () => {
  const harness = settingsHarness({ settings: stored() });
  const sheet = () => nodes(harness.render(), byComponent('ExamDateSheet'))[0];
  const monthTitle = () => nodes(sheet(), node => node.props?.accessibilityRole === 'header')
    .map(node => String(node.props.children ?? '')).find(title => /年 .* 月/.test(title));
  assert.equal(monthTitle(), '2026 年 12 月', 'the sheet opens on the stored date');

  nodes(sheet(), node => node.type === 'YanjiIconButton' && node.props.accessibilityLabel === '下一个月')[0].props.onPress();
  assert.equal(monthTitle(), '2027 年 1 月', 'paging forward crosses the year boundary');

  nodes(sheet(), node => node.type === 'YanjiIconButton' && node.props.accessibilityLabel === '上一个月')[0].props.onPress();
  nodes(sheet(), node => node.type === 'YanjiIconButton' && node.props.accessibilityLabel === '上一个月')[0].props.onPress();
  assert.equal(monthTitle(), '2026 年 11 月', 'paging back crosses below the stored month');
});

test('saving the daily goal converts minutes back into the stored hours column', async () => {
  const harness = settingsHarness({ settings: stored({ dailyGoalHours: 0 }) });
  const sheets = () => nodes(harness.render(), byComponent('DailyGoalSheet'));
  assert.equal(sheets().length, 1);

  await sheets()[0].props.onSave(390);
  const writes = harness.calls.filter(call => call[0] === 'updateUserSettings');
  assert.equal(writes.length, 1);
  assert.equal(writes[0][1].dailyGoalHours, 6.5, '390 minutes is stored as 6.5 hours');
});

test('a goal of zero means "no goal" and the sheet says so before saving it', async () => {
  // Zero is reachable through the stepper, and `dailyGoalHours: 0` is how this
  // codebase stores "unset" (both Today and this page read <= 0 that way). The
  // action is therefore labelled 「清除目标」: writing zero is a removal, and the
  // button must not read as if it were setting a target.
  const harness = settingsHarness({ settings: stored({ dailyGoalHours: 0 }) });
  const sheet = () => nodes(harness.render(), byComponent('DailyGoalSheet'))[0];
  // `YanjiPrimaryButton` is a stub in this harness, so its label is read from
  // the element the sheet renders rather than from a real render pass.
  const save = nodes(sheet(), node => node.type === 'YanjiPrimaryButton')[0];
  assert.ok(save, 'the sheet must publish its save action');
  assert.equal(save.props.label, '清除目标');

  await sheet().props.onSave(0);
  const writes = harness.calls.filter(call => call[0] === 'updateUserSettings');
  assert.equal(writes.length, 1);
  assert.equal(writes[0][1].dailyGoalHours, 0, 'clearing writes zero, the stored "unset"');
});

test('the page body walker can actually see a primary button', () => {
  // Guards the helper the assertion below depends on. The previous version of
  // `pageBody` collected nothing (predicate `() => false`), so the
  // no-return-button test passed no matter what the page rendered. A synthetic
  // tree with a button in it must be found — otherwise the assertion below is
  // vacuous again.
  const withButton = {
    type: 'Body',
    props: {
      children: {
        type: 'View',
        props: {
          children: { type: 'YanjiPrimaryButton', props: { label: '返回今天' } },
        },
      },
    },
  };
  assert.equal(nodes(withButton, node => node.type === 'YanjiPrimaryButton').length, 1,
    'the walker must descend into children and match a leaf component');
});

test('the page no longer ships a full-width return button', () => {
  const tree = settingsHarness({ settings: stored() }).render();
  // Closing is the header's icon button. The removed button was a second,
  // full-width action with the same effect — so the page's own body must carry
  // no primary button at all. The sheets' save buttons live in their own modals
  // and are not part of the page body.
  const primary = nodes(pageBody(tree), node => node.type === 'YanjiPrimaryButton');
  assert.equal(primary.length, 0);

  // The one remaining way out of the page is the header's close control.
  const header = nodes(tree, node => node.type === 'YanjiEditorialHeader')[0];
  assert.equal(header.props.trailing.props.accessibilityLabel, '完成');
  assert.equal(header.props.trailing.type, 'YanjiIconButton');
});
