import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

const date = '2026-10-16';
const math = { id: 'math', name: '数学', colorHex: '#356AE6', enabled: true, parentId: null, sortOrder: 1 };
const block = (changes = {}) => ({
  id: 'block-a', startMinute: 840, endMinute: 960, subjectIds: ['math'],
  subjectLabel: '数学', title: '积分练习', ...changes,
});
const day = template => ({
  date, template: template && { id: 'tpl', name: '默认', sortOrder: 1, ...template },
  assigned: true, completedBlockIds: [],
});
const settle = () => new Promise(resolve => setImmediate(resolve));

function fixture({ schedule = day({ blocks: [block()] }), subjects = [math], fail = false } = {}) {
  const states = [];
  const refs = [];
  const effects = [];
  let cursor = 0;
  let refCursor = 0;
  let mounted = false;
  let listener;
  let current = schedule;
  const calls = [];

  const module = loadTypeScript('src/components/TodayScheduleCard.tsx', {
    react: {
      useState(initial) {
        const index = cursor++;
        if (!(index in states)) states[index] = typeof initial === 'function' ? initial() : initial;
        return [states[index], next => { states[index] = typeof next === 'function' ? next(states[index]) : next; }];
      },
      useEffect(effect) { effects.push(effect); },
      useCallback: fn => fn,
      useRef(initial) {
        const index = refCursor++;
        if (!(index in refs)) refs[index] = { current: initial };
        return refs[index];
      },
    },
    'react-native': { Pressable: 'Pressable', Text: 'Text', View: 'View' },
    '../bridge': {
      YanjiDataNative: {
        getScheduleDay: async () => { if (fail) throw new Error('offline'); return current; },
        getSubjects: async () => subjects,
      },
      onDataChanged: callback => { listener = callback; return () => {}; },
    },
    './YanjiUI': {
      YanjiIcon: 'YanjiIcon', YanjiPaperSectionHeading: 'YanjiPaperSectionHeading',
      YanjiTextAction: 'YanjiTextAction',
    },
    '../theme/ThemeProvider': {
      useYanjiTheme: () => ({ paper: { colors: {}, typography: {}, layout: {}, radius: {} } }),
    },
    '../theme/tokens': { YanjiSpacing: { xs: 4, sm: 8, md: 12, xl: 24 } },
  }, {
    Date: class extends Date {
      constructor(...args) { super(...(args.length ? args : [`${date}T15:00:00`])); }
    },
    setInterval: () => 1,
    clearInterval: () => {},
  });

  function render() {
    cursor = 0;
    refCursor = 0;
    const tree = module.TodayScheduleCard({
      date,
      visible: true,
      onEdit: () => calls.push(['edit']),
      onStart: preset => calls.push(['start', preset]),
    });
    if (!mounted) {
      mounted = true;
      effects.forEach(effect => effect());
    }
    return tree;
  }

  return { calls, render, emit: type => listener?.({ type }), setSchedule: next => { current = next; } };
}

function nodes(tree, predicate) {
  const result = [];
  (function walk(node) {
    if (Array.isArray(node)) node.forEach(walk);
    else if (node && typeof node === 'object' && 'type' in node) {
      if (predicate(node)) result.push(node);
      walk(node.props?.children);
    }
  })(tree);
  return result;
}
function text(tree) {
  if (Array.isArray(tree)) return tree.map(text).join('');
  if (tree && typeof tree === 'object') {
    if (tree.props?.children === undefined) return tree.props?.title ?? tree.props?.label ?? '';
    return text(tree.props.children);
  }
  return typeof tree === 'string' || typeof tree === 'number' ? String(tree) : '';
}
const cardOf = tree => nodes(tree, node => node.type === 'Pressable' && typeof node.props?.accessibilityLabel === 'string')[0];

test('the current block reads as now, states its remainder, and starts a focus preset', async () => {
  const f = fixture();
  f.render();
  await settle();
  const tree = f.render();
  assert.match(text(tree), /今日时间表/);
  assert.match(text(tree), /现在/);
  assert.match(text(tree), /14:00–16:00/);
  assert.match(text(tree), /数学 · 剩余 60 分钟/);
  const card = cardOf(tree);
  assert.ok(card, 'the block is a pressable row');
  card.props.onPress();
  assert.equal(f.calls.length, 1);
  assert.equal(f.calls[0][0], 'start');
  // The preset is built inside the component's VM context, so compare its
  // fields rather than the object identity.
  assert.deepEqual({ ...f.calls[0][1] }, {
    taskId: null, subjectId: 'math', subjectName: '数学', title: '积分练习', plannedMinutes: 60,
  });
});

test('when nothing is running the next block is offered with its full duration', async () => {
  const f = fixture({ schedule: day({ blocks: [block({ id: 'past', startMinute: 540, endMinute: 600 }), block({ id: 'next', startMinute: 960, endMinute: 1020 })] }) });
  f.render();
  await settle();
  const tree = f.render();
  assert.match(text(tree), /接下来/);
  assert.match(text(tree), /16:00–17:00/);
  assert.match(text(tree), /60 分钟/);
  cardOf(tree).props.onPress();
  assert.equal(f.calls[0][1].plannedMinutes, 60);
});

test('a block with no enabled subject opens the timetable instead of starting a session', async () => {
  const f = fixture({ schedule: day({ blocks: [block({ subjectIds: [] })] }), subjects: [] });
  f.render();
  await settle();
  const tree = f.render();
  const card = cardOf(tree);
  assert.match(card.props.accessibilityLabel, /该时段不启动专注/);
  card.props.onPress();
  assert.deepEqual(f.calls, [['edit']]);
});

test('no template and a finished day each say so honestly', async () => {
  const empty = fixture({ schedule: day(null) });
  empty.render();
  await settle();
  assert.match(text(empty.render()), /还没有时间表/);

  const finished = fixture({ schedule: day({ blocks: [block({ startMinute: 540, endMinute: 600 })] }) });
  finished.render();
  await settle();
  assert.match(text(finished.render()), /今天的时段已全部结束/);
});

test('the heading edit action and a failed read are both reachable', async () => {
  const f = fixture();
  f.render();
  await settle();
  const heading = nodes(f.render(), node => node.type === 'YanjiPaperSectionHeading')[0];
  const action = heading.props.right;
  assert.equal(action.props.label, '编辑');
  action.props.onPress();
  assert.deepEqual(f.calls, [['edit']]);

  const failed = fixture({ fail: true });
  failed.render();
  await settle();
  assert.match(text(failed.render()), /时间表读取失败/);
});

test('a schedule change re-reads the day without a remount', async () => {
  const f = fixture({ schedule: day({ blocks: [] }) });
  f.render();
  await settle();
  assert.match(text(f.render()), /这套模板还没有时间段/);
  f.setSchedule(day({ blocks: [block()] }));
  f.emit('schedule');
  await settle();
  assert.match(text(f.render()), /积分练习/);
});
