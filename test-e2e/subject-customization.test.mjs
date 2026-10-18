import test from 'node:test';
import assert from 'node:assert/strict';
import { MockYanjiBridge } from './contracts/mock-bridge.js';
import { loadTypeScript } from './framework/load-typescript.js';

const tokens = loadTypeScript('src/theme/tokens.ts');

test('MockYanjiBridge renameSubject updates subject and synchronizes historical sessions and tasks', async () => {
  const bridge = new MockYanjiBridge({
    subjects: [
      { id: 'math', name: '数学一', colorHex: '#356AE6', sortOrder: 1, enabled: true, parentId: null, isCategory: true },
    ],
    tasks: [
      { id: 'task-1', date: '2026-10-18', subjectId: 'math', subjectName: '数学一', title: '完成线代第一章', plannedMinutes: 60, completed: false },
    ],
    sessionRecords: [
      { id: 'sess-1', date: '2026-10-18', subjectId: 'math', subjectName: '数学一', durationSeconds: 2700, isExam: false },
    ],
  });

  const emitted = [];
  bridge.onDataChanged(event => emitted.push(event.type));

  const success = await bridge.renameSubject('math', '高等数学（综合）');
  assert.equal(success, true);

  const subjects = await bridge.getSubjects();
  assert.equal(subjects.find(s => s.id === 'math')?.name, '高等数学（综合）');

  // Verify historical task synchronized
  assert.equal(bridge.tasks[0].subjectName, '高等数学（综合）');

  // Verify historical session record synchronized
  assert.equal(bridge.sessionRecords[0].subjectName, '高等数学（综合）');

  // Verify event notifications emitted
  assert.ok(emitted.includes('subjects'));
  assert.ok(emitted.includes('sessions'));
  assert.ok(emitted.includes('tasks'));
});

test('MockYanjiBridge subject management: add category, add sub-subject, delete, restore', async () => {
  const bridge = new MockYanjiBridge();
  const initialCount = bridge.subjects.length;

  // Add category
  const newCat = await bridge.addSubjectCategory('考研专业课二');
  assert.ok(newCat);
  assert.equal(newCat.name, '考研专业课二');
  assert.equal(newCat.isCategory, true);
  assert.equal(bridge.subjects.length, initialCount + 1);

  // Add sub-subject
  const newSub = await bridge.addSubSubject(newCat.id, '信号与系统');
  assert.ok(newSub);
  assert.equal(newSub.name, '信号与系统');
  assert.equal(newSub.parentId, newCat.id);

  // Delete sub-subject
  const deleted = await bridge.deleteSubject(newSub.id);
  assert.equal(deleted, true);
  assert.equal(bridge.subjects.some(s => s.id === newSub.id), false);

  // Add another sub-subject to verify category deletion cascade
  const sub2 = await bridge.addSubSubject(newCat.id, '通信原理');
  assert.ok(sub2);
  assert.equal(bridge.subjects.some(s => s.id === sub2.id), true);

  // Delete category (along with children)
  const catDeleted = await bridge.deleteSubject(newCat.id);
  assert.equal(catDeleted, true);
  assert.equal(bridge.subjects.some(s => s.id === newCat.id), false);
  assert.equal(bridge.subjects.some(s => s.parentId === newCat.id), false);

  // Restore defaults
  await bridge.restoreDefaultSubjects();
  assert.equal(bridge.subjects.length, initialCount);
});

test('FocusScreen category cards do not show 今日 xx duration text', () => {
  const paper = {
    colors: tokens.YanjiPaperColors.light,
    radius: tokens.YanjiPaperRadius,
    layout: tokens.YanjiPaperLayout,
    typography: tokens.YanjiPaperTypography,
  };

  const sampleSubjects = [
    { id: 'math', name: '数学一', colorHex: '#356AE6', sortOrder: 1, enabled: true, parentId: null, isCategory: true },
    { id: 'math_adv', name: '高等数学', colorHex: '#7C3AED', sortOrder: 11, enabled: true, parentId: 'math', isCategory: false },
    { id: 'politics', name: '政治', colorHex: '#DC2626', sortOrder: 4, enabled: true, parentId: null, isCategory: false },
  ];

  let stateIdx = 0;
  const states = [];
  const react = {
    useState: initial => {
      const i = stateIdx++;
      if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
      return [states[i], next => { states[i] = typeof next === 'function' ? next(states[i]) : next; }];
    },
    useMemo: fn => fn(),
    useCallback: fn => fn,
    useEffect: () => {},
    useRef: val => ({ current: val }),
  };

  const focusModule = loadTypeScript('src/screens/FocusScreen.tsx', {
    react,
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView', Modal: 'Modal',
      TextInput: 'TextInput', AppState: { currentState: 'active', addEventListener: () => ({ remove: () => {} }) },
      useWindowDimensions: () => ({ width: 375, height: 812 }),
    },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) },
    'react-native-svg': { default: 'Svg', Defs: 'Defs', LinearGradient: 'LinearGradient', Rect: 'Rect', Stop: 'Stop' },
    '../bridge': {
      YanjiDataNative: {
        getSubjects: async () => sampleSubjects,
      },
      YanjiTimerNative: {},
      onDataChanged: () => () => {},
    },
    '../components/YanjiUI': {
      YanjiIcon: 'YanjiIcon',
      YanjiPaperSegmentedControl: 'YanjiPaperSegmentedControl',
    },
    '../components/FocusVernierDial': { FocusVernierDial: 'FocusVernierDial' },
    '../components/RollingTimeDisplay': { RollingTimeDisplay: 'RollingTimeDisplay' },
    '../components/RecordMomentModal': { RecordMomentModal: 'RecordMomentModal' },
    '../navigation/NavigationShell': {
      useNavigationActions: () => ({ clearFocusPreset: () => {} }),
      useNavigationState: () => ({ focusPreset: null }),
    },
    '../navigation/useBottomTabLayout': { useBottomTabLayout: () => ({ totalBottomOffset: 60 }) },
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper }) },
    '../theme/tokens': tokens,
    '../state/dataRefresh': {
      useDailySnapshot: () => ({ date: '2026-10-18', sessions: [], tasks: [] }),
    },
    '../state/timerStore': {
      syncActiveSession: () => {},
      useActiveSession: () => null,
      useTimerTick: () => ({ elapsedSeconds: 0, remainingSeconds: 0 }),
    },
  });

  // Render FocusScreen
  const element = focusModule.FocusScreen({ visible: true });

  // Collect all text nodes
  const textStrings = [];
  function collectText(node) {
    if (!node) return;
    if (Array.isArray(node)) {
      node.forEach(collectText);
      return;
    }
    if (typeof node === 'object' && 'props' in node) {
      if (node.type === 'Text' && typeof node.props?.children === 'string') {
        textStrings.push(node.props.children);
      }
      collectText(node.props?.children);
    }
  }
  collectText(element);

  // Assert that no string mentions "今日 " or "今日已专注" for subjects
  for (const str of textStrings) {
    assert.doesNotMatch(str, /今日\s*\d+[hm]/, `Found subject duration text: ${str}`);
    assert.doesNotMatch(str, /今日已专注/, `Found subject duration text: ${str}`);
  }
});

test('SubjectsSheet renders edit and delete options for categories with compact icon sizes', () => {
  const paper = {
    colors: tokens.YanjiPaperColors.light,
    radius: tokens.YanjiPaperRadius,
    layout: tokens.YanjiPaperLayout,
    typography: tokens.YanjiPaperTypography,
  };

  const sampleSubjects = [
    { id: 'math', name: '数学一', colorHex: '#356AE6', sortOrder: 1, enabled: true, parentId: null, isCategory: true },
    { id: 'math_adv', name: '高等数学', colorHex: '#7C3AED', sortOrder: 11, enabled: true, parentId: 'math', isCategory: false },
  ];

  let stateIdx = 0;
  const states = [sampleSubjects, false, null, null, '', null, null, '', false, false, '', false];
  const react = {
    useState: initial => {
      const i = stateIdx++;
      if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
      return [states[i], next => { states[i] = typeof next === 'function' ? next(states[i]) : next; }];
    },
    useMemo: fn => fn(),
    useCallback: fn => fn,
    useEffect: () => {},
    useRef: val => ({ current: val }),
  };

  const YanjiIconButton = props => ({ type: 'YanjiIconButton', props });
  const subjectsSheetModule = loadTypeScript('src/components/SubjectsSheet.tsx', {
    react,
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView', Modal: 'Modal',
      TextInput: 'TextInput', Alert: { alert: () => {} },
    },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) },
    'react-native-reanimated': { useReducedMotion: () => false },
    './YanjiUI': {
      YanjiIcon: 'YanjiIcon',
      YanjiPaperSheetHeading: 'YanjiPaperSheetHeading',
      YanjiIconButton,
    },
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper }) },
    '../theme/tokens': tokens,
    '../bridge': {
      YanjiDataNative: {
        getSubjects: async () => sampleSubjects,
        renameSubject: async () => true,
        deleteSubject: async () => true,
      },
    },
  });

  const element = subjectsSheetModule.SubjectsSheet({ visible: true, onClose: () => {} });
  assert.ok(element);

  function findNodes(node, predicate, acc = []) {
    if (!node) return acc;
    if (Array.isArray(node)) {
      node.forEach(n => findNodes(n, predicate, acc));
      return acc;
    }
    if (predicate(node)) acc.push(node);
    if (node.props?.children) findNodes(node.props.children, predicate, acc);
    return acc;
  }
  const buttonNodes = findNodes(element, n => n.type === YanjiIconButton);
  const iconButtons = buttonNodes.map(b => b.props);

  // Verify category has both edit and delete buttons
  const editCatBtn = iconButtons.find(b => b.accessibilityLabel === '修改数学一名称');
  assert.ok(editCatBtn, 'Category edit button must exist');
  assert.equal(editCatBtn.size, 24, 'Category edit button size should be 24');
  assert.equal(editCatBtn.iconSize, 14, 'Category edit button iconSize should be 14');

  const deleteCatBtn = iconButtons.find(b => b.accessibilityLabel === '删除数学一');
  assert.ok(deleteCatBtn, 'Category delete button must exist');
  assert.equal(deleteCatBtn.size, 24, 'Category delete button size should be 24');
  assert.equal(deleteCatBtn.iconSize, 14, 'Category delete button iconSize should be 14');

  // Verify child subject has edit and delete buttons
  const deleteChildBtn = iconButtons.find(b => b.accessibilityLabel === '删除高等数学');
  assert.ok(deleteChildBtn, 'Child delete button must exist');
  assert.equal(deleteChildBtn.size, 22, 'Child delete button size should be 22');
  assert.equal(deleteChildBtn.iconSize, 13, 'Child delete button iconSize should be 13');
});

test('FocusScreen reloads subjects when onDataChanged receives event.type subjects', async () => {
  const paper = {
    colors: tokens.YanjiPaperColors.light,
    radius: tokens.YanjiPaperRadius,
    layout: tokens.YanjiPaperLayout,
    typography: tokens.YanjiPaperTypography,
  };

  const sampleSubjects = [
    { id: 'math', name: '数学一', colorHex: '#356AE6', sortOrder: 1, enabled: true, parentId: null, isCategory: true },
  ];
  let getSubjectsCallCount = 0;
  let dataChangedCallback = null;

  const effects = [];
  const react = {
    useState: initial => [typeof initial === 'function' ? initial() : initial, () => {}],
    useMemo: fn => fn(),
    useCallback: fn => fn,
    useEffect: (fn, deps) => { effects.push({ fn, deps }); },
    useRef: val => ({ current: val }),
  };

  const focusModule = loadTypeScript('src/screens/FocusScreen.tsx', {
    react,
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView', Modal: 'Modal',
      TextInput: 'TextInput', AppState: { currentState: 'active', addEventListener: () => ({ remove: () => {} }) },
      useWindowDimensions: () => ({ width: 375, height: 812 }),
    },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) },
    'react-native-svg': { default: 'Svg', Defs: 'Defs', LinearGradient: 'LinearGradient', Rect: 'Rect', Stop: 'Stop' },
    '../bridge': {
      YanjiDataNative: {
        getSubjects: async () => {
          getSubjectsCallCount++;
          return sampleSubjects;
        },
      },
      YanjiTimerNative: {},
      onDataChanged: cb => {
        dataChangedCallback = cb;
        return () => {};
      },
    },
    '../components/YanjiUI': {
      YanjiIcon: 'YanjiIcon',
      YanjiPaperSegmentedControl: 'YanjiPaperSegmentedControl',
    },
    '../components/FocusVernierDial': { FocusVernierDial: 'FocusVernierDial' },
    '../components/RollingTimeDisplay': { RollingTimeDisplay: 'RollingTimeDisplay' },
    '../components/RecordMomentModal': { RecordMomentModal: 'RecordMomentModal' },
    '../navigation/NavigationShell': {
      useNavigationActions: () => ({ clearFocusPreset: () => {} }),
      useNavigationState: () => ({ focusPreset: null }),
    },
    '../navigation/useBottomTabLayout': { useBottomTabLayout: () => ({ totalBottomOffset: 60 }) },
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper }) },
    '../theme/tokens': tokens,
    '../state/dataRefresh': {
      useDailySnapshot: () => ({ date: '2026-10-18', sessions: [], tasks: [] }),
    },
    '../state/timerStore': {
      syncActiveSession: () => {},
      useActiveSession: () => null,
      useTimerTick: () => ({ elapsedSeconds: 0, remainingSeconds: 0 }),
    },
  });

  // Render FocusScreen with visible = true
  focusModule.FocusScreen({ visible: true });

  // Run initial effects
  for (const eff of effects) {
    eff.fn();
  }

  assert.ok(getSubjectsCallCount > 0, 'Should have loaded subjects on mount / visible');
  const countBefore = getSubjectsCallCount;

  // Simulate onDataChanged event for subjects
  assert.ok(dataChangedCallback, 'onDataChanged callback should be registered');
  dataChangedCallback({ type: 'subjects' });

  assert.equal(getSubjectsCallCount, countBefore + 1, 'Should reload subjects when subjects changed event arrives');
});
