import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

/**
 * F09/T5 — production `RecordMomentModal` draft behaviour.
 *
 * The RN change under test is a single unsaved-capture buffer owned by the
 * modal instance: `src/components/RecordMomentModal.tsx` keeps `captureDraft`
 * in a `useRef`, seeds the field from it for a new capture, mirrors keystrokes
 * into it only while NOT editing, and clears it only after a successful new
 * save.
 *
 * These tests invoke the production component through React hook/native
 * adapters, so the assertions exercise the shipped code path rather than a
 * copy of it. `useEffect` honours its real dependency array and a state write
 * re-renders until it settles, which is what makes "close then reopen restores
 * the draft", "editing never pollutes the draft" and "a failed save keeps the
 * text" meaningful.
 */

const theme = {
  isDark: false,
  shadow: { floating: () => ({}) },
  paper: {
    colors: { surface: '#ffffff', hairline: 'rgba(28,30,33,0.08)', scrim: 'rgba(28,30,33,0.32)', danger: '#be3232' },
    radius: { sheet: 26, pill: 9999 },
    layout: { pageGutter: 22 },
    typography: { captionMeta: { fontSize: 12 } },
  },
};

function modalHarness({ save, activeSession = null } = {}) {
  const calls = { saveQuickNote: [], updateNote: [], deleteNote: [], activeSessionReads: 0 };
  const states = [];
  const refs = [];
  const effectSlots = [];
  const pendingEffects = [];
  let hookIndex = 0;
  let refIndex = 0;
  let lastProps = { date: '2026-10-15', onClose: () => {}, editingNote: null };

  const react = {
    useState: initial => {
      const i = hookIndex++;
      if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
      return [states[i], next => {
        states[i] = typeof next === 'function' ? next(states[i]) : next;
      }];
    },
    useRef: initial => {
      const i = refIndex++;
      if (!refs[i]) refs[i] = { current: initial };
      return refs[i];
    },
    useEffect: (fn, deps) => {
      const i = hookIndex++;
      const previous = effectSlots[i];
      const unchanged = previous && deps && previous.deps &&
        deps.length === previous.deps.length &&
        deps.every((value, index) => Object.is(value, previous.deps[index]));
      if (!unchanged) {
        effectSlots[i] = { deps, fn };
        pendingEffects.push(fn);
      }
    },
    useCallback: fn => fn,
  };

  const bridge = {
    YanjiDataNative: {
      saveQuickNote: (...args) => {
        calls.saveQuickNote.push(args);
        return save ? save(...args) : Promise.resolve({ id: 'note-1' });
      },
      updateNote: (...args) => {
        calls.updateNote.push(args);
        return Promise.resolve(true);
      },
      deleteNote: (...args) => {
        calls.deleteNote.push(args);
        return Promise.resolve(true);
      },
      getUserSettings: () => Promise.resolve({}),
      getDecisionApiKey: () => Promise.resolve(''),
    },
    YanjiTimerNative: {
      getActiveSession: () => {
        calls.activeSessionReads++;
        return Promise.resolve(activeSession);
      },
    },
  };

  const component = loadTypeScript('src/components/RecordMomentModal.tsx', {
    react,
    'react-native': { Modal: 'Modal', Pressable: 'Pressable', Text: 'Text', View: 'View' },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom: 0 }) },
    'react-native-reanimated': { useReducedMotion: () => false },
    '../bridge': bridge,
    './YanjiUI': {
      YanjiHairline: 'YanjiHairline',
      YanjiPaperSheetHeading: 'YanjiPaperSheetHeading',
      YanjiPrimaryButton: 'YanjiPrimaryButton',
      YanjiTextInput: 'YanjiTextInput',
      useYanjiKeyboardInset: () => 0,
    },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../theme/tokens': { YanjiSpacing: { xs: 4, sm: 8, md: 12, lg: 16 } },
  }).RecordMomentModal;

  function collect(tree) {
    function walk(node, predicate, found = []) {
      if (Array.isArray(node)) {
        for (const child of node) walk(child, predicate, found);
      } else if (node && typeof node === 'object' && 'type' in node) {
        if (predicate(node)) found.push(node);
        walk(node.props?.children, predicate, found);
      }
      return found;
    }
    const carriesDanger = node => {
      const style = node.props?.style;
      const layers = Array.isArray(style) ? style : [style];
      return layers.some(layer => layer && layer.color === theme.paper.colors.danger);
    };
    return {
      tree,
      props: lastProps,
      draft: refs[0],
      input: walk(tree, node => node.type === 'YanjiTextInput')[0],
      save: walk(tree, node => node.type === 'YanjiPrimaryButton')[0],
      heading: walk(tree, node => node.type === 'YanjiPaperSheetHeading')[0],
      error: walk(tree, node => node.type === 'Text' && carriesDanger(node))[0],
      pressables: walk(tree, node => node.type === 'Pressable'),
    };
  }

  // A parent re-render keeps every prop it did not change, so the harness
  // merges instead of resetting — otherwise `date` would silently fall back.
  function render(props) {
    lastProps = { ...lastProps, ...props };
    let ui;
    // An effect that writes state schedules another pass, exactly like React.
    for (let pass = 0; pass < 6; pass++) {
      hookIndex = 0;
      refIndex = 0;
      pendingEffects.length = 0;
      const before = [...states];
      const tree = component(lastProps);
      for (const effect of pendingEffects) effect();
      ui = collect(tree);
      if (states.every((value, index) => Object.is(value, before[index]))) break;
    }
    return ui;
  }

  /** Type into the field, then let the resulting state update re-render. */
  function typeInto(ui, value) {
    ui.input.props.onChangeText(value);
    return render(lastProps);
  }

  return { render, typeInto, calls, refs };
}

test('a new capture draft survives closing and reopening the sheet', () => {
  const modal = modalHarness();

  const opened = modal.render({ visible: true });
  modal.typeInto(opened, '寸阴尺第三格的想法');
  const hidden = modal.render({ visible: false });
  assert.equal(hidden.input.props.value, '寸阴尺第三格的想法', 'closing keeps the unsaved text');

  const restored = modal.render({ visible: true });
  assert.equal(restored.input.props.value, '寸阴尺第三格的想法');
});

test('editing a saved note never reads from or writes to the new-capture draft', () => {
  const modal = modalHarness();
  const draft = '未保存的随手记录';

  modal.typeInto(modal.render({ visible: true }), draft);

  const editor = modal.render({ visible: true, editingNote: { id: 'note-9', content: '已保存的旧记录' } });
  assert.equal(editor.input.props.value, '已保存的旧记录', 'the editor must seed from the saved note');
  modal.typeInto(editor, '编辑后的正文');
  assert.equal(modal.refs[0].current, draft, 'editing must not overwrite the pending capture draft');

  const reopened = modal.render({ visible: true, editingNote: null });
  assert.equal(reopened.input.props.value, draft);
});

test('a failed save preserves the typed text and the draft buffer', async () => {
  const modal = modalHarness({ save: () => Promise.reject(new Error('room write failed')) });

  const opened = modal.render({ visible: true });
  const typed = modal.typeInto(opened, '保存失败也要留住');
  await typed.save.props.onPress();

  const failed = modal.render({ visible: true });
  assert.equal(failed.input.props.value, '保存失败也要留住');
  assert.equal(failed.error.props.children, '保存失败，内容已保留');

  const restored = modal.render({ visible: true });
  assert.equal(restored.input.props.value, '保存失败也要留住');
  assert.equal(modal.refs[0].current, '保存失败也要留住');
});

test('a successful new save clears the draft and writes the selected target date', async () => {
  const modal = modalHarness({ activeSession: { sessionId: 'session-7' } });

  const opened = modal.render({ visible: true, date: '2026-10-09' });
  const typed = modal.typeInto(opened, '  目标日期必须来自可见的 prop  ');
  await typed.save.props.onPress();

  assert.deepEqual(modal.calls.saveQuickNote, [['目标日期必须来自可见的 prop', '2026-10-09', 'session-7', 'INSPIRATION']]);
  assert.equal(modal.calls.activeSessionReads, 1, 'the active session is resolved once for the binding');
  assert.equal(modal.refs[0].current, '', 'a successful new save clears the retained draft');

  const reopened = modal.render({ visible: true });
  assert.equal(reopened.input.props.value, '');
  assert.equal(reopened.heading.props.subtitle, '10月9日 · 周五', 'the visible subtitle stays the selected date');
});

test('whitespace-only input is rejected without touching the draft buffer', async () => {
  const modal = modalHarness();

  const opened = modal.render({ visible: true });
  const typed = modal.typeInto(opened, '   \n  ');
  await typed.save.props.onPress();

  assert.equal(modal.calls.saveQuickNote.length, 0);
  assert.equal(modal.refs[0].current, '   \n  ', 'a rejected save must not discard what the user typed');
  assert.equal(modal.render({ visible: true }).error.props.children, '写点什么再保存吧');
});

test('the draft belongs to the mounted instance, so hiding the pane keeps it', () => {
  const modal = modalHarness();
  modal.typeInto(modal.render({ visible: true }), '切到回顾再回来');

  const hidden = modal.render({ visible: false });
  assert.equal(hidden.input.props.value, '切到回顾再回来', 'hiding the pane must not drop the buffer');
  assert.equal(modal.render({ visible: true }).input.props.value, '切到回顾再回来');
  assert.equal(modal.refs[0], modal.render({ visible: true }).draft, 'no remount: one stable ref');
});

test('editing an existing note routes through updateNote and leaves the capture draft intact', async () => {
  const modal = modalHarness();
  modal.typeInto(modal.render({ visible: true }), 'pending capture');

  const editor = modal.render({ visible: true, editingNote: { id: 'note-3', content: 'old' } });
  const typed = modal.typeInto(editor, 'updated body');
  await typed.save.props.onPress();

  assert.deepEqual(modal.calls.updateNote, [['note-3', 'updated body']]);
  assert.equal(modal.calls.saveQuickNote.length, 0, 'editing must not create a second note');
  assert.equal(modal.refs[0].current, 'pending capture', 'editing must leave the capture draft intact');
});

test('deleting an existing note routes through deleteNote, clears input, and calls onSaved and onClose', async () => {
  const modal = modalHarness();
  modal.typeInto(modal.render({ visible: true }), 'draft before delete');

  let savedCalled = false;
  let closedCalled = false;
  const editor = modal.render({
    visible: true,
    editingNote: { id: 'note-del-1', content: 'to delete' },
    initialConfirmDelete: true,
    onSaved: () => { savedCalled = true; },
    onClose: () => { closedCalled = true; },
  });

  const confirmDeleteBtn = editor.pressables.find(p => p.props.accessibilityLabel === '确认删除这条记录');
  assert.ok(confirmDeleteBtn, 'initialConfirmDelete renders the confirmation button');

  await confirmDeleteBtn.props.onPress();

  assert.deepEqual(modal.calls.deleteNote, [['note-del-1']]);
  assert.equal(savedCalled, true, 'onSaved must be called upon deletion');
  assert.equal(closedCalled, true, 'onClose must be called upon deletion');
  assert.equal(modal.refs[0].current, 'draft before delete', 'deleting an existing note must leave capture draft intact');
});

test('inline delete toggles between initial delete prompt and confirmation buttons', async () => {
  const modal = modalHarness();
  const editor = modal.render({
    visible: true,
    editingNote: { id: 'note-del-2', content: 'prompt test' },
    initialConfirmDelete: false,
  });

  const deleteBtn = editor.pressables.find(p => p.props.accessibilityLabel === '删除这条记录');
  assert.ok(deleteBtn, 'regular editor renders initial delete button');

  // Trigger delete prompt
  deleteBtn.props.onPress();
  const confirming = modal.render({ visible: true });

  const cancelBtn = confirming.pressables.find(p => p.props.accessibilityLabel === '取消删除');
  const confirmBtn = confirming.pressables.find(p => p.props.accessibilityLabel === '确认删除这条记录');
  assert.ok(cancelBtn, 'reveals cancel button');
  assert.ok(confirmBtn, 'reveals confirm delete button');

  // Cancel returns to prompt
  cancelBtn.props.onPress();
  const cancelled = modal.render({ visible: true });
  assert.ok(cancelled.pressables.find(p => p.props.accessibilityLabel === '删除这条记录'));
});

