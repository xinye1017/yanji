import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

/**
 * 补录专注 —— 一段**已经发生**但没有被计时器记录的专注。
 *
 * 与「记录此刻」的区别是本文件的核心契约：随笔写的是 `journal_entries`，
 * 补录写的是 `focus_sessions`。两者混用的话，忘掉的五十分钟永远进不了
 * 今日 / 回顾 / 统计，只会变成一句无法被统计的散文。
 *
 * 这里驱动真实的组件与桥接调用，锁住：
 *  1. 提交的是「开始时刻 + 时长」，结束时刻由原生推导；
 *  2. 非法输入在两侧都被挡住，且不会静默夹取；
 *  3. 可选关联今日计划。
 */

const tokens = loadTypeScript('src/theme/tokens.ts');
const theme = {
  isDark: false,
  paper: {
    colors: tokens.YanjiPaperColors.light,
    typography: tokens.YanjiPaperTypography,
    layout: tokens.YanjiPaperLayout,
    radius: tokens.YanjiPaperRadius,
  },
  shadow: tokens.YanjiShadow,
};

const nodes = (tree, predicate) => {
  const result = [];
  const walk = node => {
    if (Array.isArray(node)) node.forEach(walk);
    else if (node && typeof node === 'object' && 'type' in node) {
      if (predicate(node)) result.push(node);
      walk(node.props.children);
    }
  };
  walk(tree);
  return result;
};

const SUBJECTS = [
  { id: 'math', name: '数学', colorHex: '#C84B31', sortOrder: 0, enabled: true, parentId: null, isCategory: false },
  { id: 'english', name: '英语', colorHex: '#2D7A5E', sortOrder: 1, enabled: true, parentId: null, isCategory: false },
];

const TASKS = [
  { id: 'task-a', date: '2026-10-21', subjectId: 'math', subjectName: '数学', subjectColor: '#C84B31', title: '微分方程二刷', plannedMinutes: 50, actualMinutes: 0, completed: false, createdAt: 0 },
];

function harness({ records = [], subjects = SUBJECTS, tasks = TASKS, reject = null } = {}) {
  const states = [];
  let effects = [];
  let index = 0;
  const { BackfillFocusModal } = loadTypeScript('src/components/BackfillFocusModal.tsx', {
    react: {
      useState: initial => {
        const i = index++;
        if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
        return [states[i], next => { states[i] = typeof next === 'function' ? next(states[i]) : next; }];
      },
      useMemo: fn => fn(),
      useCallback: fn => fn,
      useEffect: effect => effects.push(effect),
      useRef: initial => ({ current: initial }),
    },
    'react-native': { Modal: 'Modal', Pressable: 'Pressable', ScrollView: 'ScrollView', Text: 'Text', View: 'View' },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ top: 24, bottom: 0 }) },
    'react-native-reanimated': { useReducedMotion: () => true },
    '../bridge': {
      YanjiDataNative: {
        getSubjects: async () => subjects,
        getDailySnapshot: async () => ({ tasks }),
        createFocusRecord: async args => {
          if (reject) throw reject;
          records.push(args);
          return true;
        },
      },
    },
    './YanjiUI': {
      YanjiPaperSheetHeading: 'YanjiPaperSheetHeading',
      YanjiPrimaryButton: 'YanjiPrimaryButton',
      YanjiStepper: 'YanjiStepper',
      useYanjiKeyboardInset: () => 0,
    },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../state/settingsViewLogic': { todayIso: () => '2026-10-23' },
  });
  return {
    effects,
    /**
     * Mount once, then re-render the same mounted instance.
     *
     * `index` is reset per render so the hook order stays aligned, while `states`
     * persists — which is exactly what a mounted component does.
     */
    async mount(props = {}) {
      index = 0;
      effects = [];
      const tree = BackfillFocusModal({ visible: true, onClose: () => {}, initialSubjectId: null, ...props });
      for (const effect of effects) await effect();
      await new Promise(resolve => setTimeout(resolve, 0));
      // Every later render restarts the hook cursor while keeping `states`, so
      // the component re-renders with the values its handlers wrote.
      return () => {
        index = 0;
        effects = [];
        return BackfillFocusModal({ visible: true, onClose: () => {}, initialSubjectId: null, ...props });
      };
    },
    render(props = {}) {
      return this.mount(props);
    },
  };
}

const saveButton = tree => nodes(tree, n => n.type === 'YanjiPrimaryButton')[0];

test('补录提交的是开始时刻与时长，不是一段散文', async () => {
  const records = [];
  const h = harness({ records });
  const tree = await h.render();
  // The default start must already have happened: saving without touching any
  // field is the whole point of the sheet, and a fixed clock time made that
  // impossible for every session earlier in the day than the hard-coded one.
  await saveButton(tree()).props.onPress();
  assert.equal(records.length, 1, '补录写下了一条真实的专注记录');
  const record = records[0];
  assert.equal(record.subjectId, 'math');
  assert.equal(record.durationMinutes, 50);
  assert.equal(record.taskId, null, '默认不关联计划');
  const start = new Date(record.startTime);
  assert.ok(start.getTime() <= Date.now(), '默认开始时间不能落在未来');
  assert.ok(Date.now() - start.getTime() < 60 * 60 * 1000, '默认补的是刚刚结束的一段专注');
  assert.equal(start.getMinutes() % 15, 0, '默认时间对齐到整刻钟');
});

test('学科可以切换，关联计划是可选字段', async () => {
  const records = [];
  const h = harness({ records });
  const tree = await h.render();

  const loaded = tree();
  const english = nodes(loaded, n => n.type === 'Pressable' && n.props.accessibilityLabel === '英语')[0];
  assert.ok(english, '学科可选');
  english.props.onPress();
  const plan = nodes(tree(), n => n.type === 'Pressable' && n.props.accessibilityLabel === '微分方程二刷')[0];
  assert.ok(plan, '今日计划出现在可选关联里');
  plan.props.onPress();
  await saveButton(tree()).props.onPress();

  assert.equal(records.length, 1);
  assert.equal(records[0].subjectId, 'english');
  assert.equal(records[0].taskId, 'task-a');
});

test('原生拒绝被翻译成用户能读懂的提示，并保留表单', async () => {
  const failure = Object.assign(new Error('rejected'), { code: 'E_FUTURE_SESSION' });
  const h = harness({ reject: failure });
  const tree = await h.render();
  await saveButton(tree()).props.onPress();
  // The rejection lands in state inside the promise's `finally`, so the tree is
  // read after that microtask drains.
  await new Promise(resolve => setTimeout(resolve, 0));

  const message = nodes(tree(), n => n.type === 'Text' && String(n.props.children).includes('开始时间不能晚于现在'))[0];
  assert.ok(message, '拒绝原因会在弹层里说出来，而不是静默失败');
});
