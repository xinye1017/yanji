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

const logic = loadTypeScript('src/screens/settingsViewLogic.ts');

/** Every element matching `predicate`, descending through rendered components. */
function nodes(tree, predicate) {
  const result = [];
  function walk(node) {
    if (Array.isArray(node)) { node.forEach(walk); return; }
    if (!node || typeof node !== 'object' || !('type' in node)) return;
    if (predicate(node)) result.push(node);
    // A component in the tree is both an element the assertion may match and a
    // renderer for the elements inside it, so it is recorded *and* descended.
    if (typeof node.type === 'function') {
      walk(node.type(node.props));
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

/** Everything the page's own scroll body renders, excluding the two sheets. */
function pageBody(tree) {
  const scroll = nodes(tree, node => node.props?.testID === 'scroll-settings')[0];
  assert.ok(scroll, 'the settings page renders its own scroll body');
  return nodes({ type: 'Body', props: { children: scroll.props.children } }, () => false);
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

test('the month grid is Monday-first, padded, and never shorter than five weeks', () => {
  const december = logic.buildMonthGrid({ year: 2026, month: 12 });
  assert.equal(december[0][1], 1, '2026-12-01 is a Tuesday, so Monday stays blank');
  assert.ok(december.length >= 5 && december.length <= 6);
  assert.ok(december.every(week => week.length === 7));
  const february = logic.buildMonthGrid({ year: 2027, month: 2 });
  assert.equal(february.flat().filter(day => day !== null).length, 28);
  const leapFebruary = logic.buildMonthGrid({ year: 2028, month: 2 });
  assert.equal(leapFebruary.flat().filter(day => day !== null).length, 29);
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
  const names = [
    'YanjiBadge', 'YanjiCard', 'YanjiChronoSpectrum', 'YanjiEditorialHeader', 'YanjiHairline',
    'YanjiIcon', 'YanjiIconButton', 'YanjiPaperSectionHeading', 'YanjiPaperSegmentedControl',
    'YanjiPaperSheetHeading', 'YanjiPrimaryButton', 'YanjiTextAction', 'YanjiTextInput',
  ];
  const ui = Object.fromEntries(names.map(name => [name, name]));
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
    '../components/SettingsRows': loadTypeScript('src/components/SettingsRows.tsx', {
      react: { useState: () => [null, () => {}], useEffect: () => {} },
      'react-native': { View: 'View', Text: 'Text', Pressable: 'Pressable' },
      './YanjiUI': ui,
      './icons': { getYanjiIcon: name => name, YANJI_ICON_STROKE: 1.75 },
      '../theme/ThemeProvider': { useYanjiTheme: () => theme },
      '../theme/tokens': tokens,
    }),
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
      '../screens/settingsViewLogic': logic,
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
    './settingsViewLogic': logic,
  }, {
    Date: class extends Date {
      constructor(...args) { super(...(args.length ? args : ['2026-10-17T12:00:00'])); }
    },
  });

  return {
    calls,
    setState: (slot, value) => { states[slot] = value; },
    render: () => { index = 0; return SettingsScreen(); },
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

test('the advanced block is collapsed until it is opened', () => {
  const harness = settingsHarness({ settings: stored() });
  const collapsed = nodes(harness.render(), byComponent('YanjiSettingDisclosureRow'));
  assert.equal(collapsed.length, 1);
  assert.equal(collapsed[0].props.expanded, false);
  assert.equal(collapsed[0].props.summary, '未配置');

  // Opening it is what reveals the decision-model form. Slot 5 is
  // `advancedOpen`: the page's hooks run in declaration order, and every one
  // before it (theme mode, open sheet, saving, two error slots) is fixed.
  harness.setState(5, true);
  const opened = harness.render();
  const inputs = nodes(opened, node => node.type === 'YanjiTextInput');
  assert.ok(inputs.length >= 3, 'model, endpoint and API key fields appear once expanded');
  assert.equal(nodes(opened, byComponent('YanjiSettingDisclosureRow'))[0].props.expanded, true);
});

test('saving the exam date writes the chosen ISO day and nothing else', async () => {
  const harness = settingsHarness({ settings: stored({ examDate: '' }) });
  const sheets = () => nodes(harness.render(), byComponent('ExamDateSheet'));
  assert.equal(sheets().length, 1);
  assert.equal(sheets()[0].props.visible, false, 'the sheet starts closed');

  // The page opens the sheet from its row, and the sheet's save is the only writer.
  // Slot 1 is `openSheet`, the second hook the page declares.
  harness.setState(1, 'examDate');
  await sheets()[0].props.onSave('2026-12-26');
  assert.deepEqual(plain(harness.calls.filter(call => call[0] === 'updateUserSettings')),
    [['updateUserSettings', { examDate: '2026-12-26' }]]);
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
