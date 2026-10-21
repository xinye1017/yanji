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
    colors: { surface: '#ffffff', hairline: 'rgba(28,30,33,0.08)', scrim: 'rgba(28,30,33,0.32)', danger: '#be3232', secondary: '#5a5f66' },
    radius: { sheet: 26, pill: 9999 },
    layout: { pageGutter: 22, rowMinHeight: 48 },
    typography: { captionMeta: { fontSize: 12 } },
  },
};

function modalHarness({ save, activeSession = null, classify = async () => 'INSPIRATION', updateNote = async () => true, setNoteCategory = async () => true } = {}) {
  const calls = { saveQuickNote: [], updateNote: [], deleteNote: [], setNoteCategory: [], activeSessionReads: 0, classifyInputs: [] };
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
        return updateNote(...args);
      },
      deleteNote: (...args) => {
        calls.deleteNote.push(args);
        return Promise.resolve(true);
      },
      setNoteCategory: (...args) => {
        calls.setNoteCategory.push(args);
        return setNoteCategory(...args);
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
      YanjiPaperSegmentedControl: 'YanjiPaperSegmentedControl',
      YanjiPaperSheetHeading: 'YanjiPaperSheetHeading',
      YanjiPrimaryButton: 'YanjiPrimaryButton',
      YanjiTextInput: 'YanjiTextInput',
      useYanjiKeyboardInset: () => 0,
    },
    // The model is a *suggestion* source, so the harness has to be able to make
    // it disagree with the user and to fail — that disagreement is the behaviour
    // under test, not an implementation detail of `decisionClient`.
    '../services/decisionClient': {
      classifyMoment: content => {
        calls.classifyInputs.push(content);
        return classify(content);
      },
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
      draftCategory: refs[1],
      input: walk(tree, node => node.type === 'YanjiTextInput')[0],
      categoryControl: walk(tree, node => node.type === 'YanjiPaperSegmentedControl')[0],
      save: walk(tree, node => node.type === 'YanjiPrimaryButton')[0],
      heading: walk(tree, node => node.type === 'YanjiPaperSheetHeading')[0],
      error: walk(tree, node => node.type === 'Text' && carriesDanger(node))[0],
      text: value => walk(tree, node => node.type === 'Text' &&
        (node.props.children === value || (Array.isArray(node.props.children) && node.props.children.includes(value))))[0],
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

  /** Pick a category in the segmented control, then re-render. */
  function chooseCategory(ui, value) {
    ui.categoryControl.props.onChange(value);
    return render(lastProps);
  }

  return { render, typeInto, chooseCategory, calls, refs };
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

// ---------------------------------------------------------------------------
// 分类：AI 只给建议，用户定夺（且可随时纠正）
// ---------------------------------------------------------------------------

test('a new capture saves the model suggestion only while the user has not chosen', async () => {
  const modal = modalHarness({ classify: async () => 'DOUBT' });

  const opened = modal.render({ visible: true });
  assert.equal(opened.categoryControl.props.value, 'INSPIRATION', 'the control starts at 灵感');
  const typed = modal.typeInto(opened, '为什么这道题要用换元');
  await typed.save.props.onPress();

  assert.deepEqual(modal.calls.classifyInputs, ['为什么这道题要用换元'], 'the suggestion is computed from the typed text');
  assert.equal(modal.calls.saveQuickNote[0][3], 'DOUBT', 'an untouched control accepts the model suggestion');
});

test("the user's category choice overrides a disagreeing model suggestion", async () => {
  const modal = modalHarness({ classify: async () => 'DOUBT' });

  const opened = modal.render({ visible: true });
  modal.chooseCategory(opened, 'INSPIRATION');
  const typed = modal.typeInto(modal.render({ visible: true }), '模型觉得是疑点，但这是我的一句心得');
  await typed.save.props.onPress();

  assert.equal(modal.calls.classifyInputs.length, 0, 'once the user decides, the model is not even consulted');
  assert.equal(modal.calls.saveQuickNote[0][3], 'INSPIRATION', "the user's choice wins over the suggestion");
});

test('a failed classification still saves, as an inspiration the user can correct', async () => {
  const modal = modalHarness({ classify: async () => { throw new Error('network down'); } });

  const typed = modal.typeInto(modal.render({ visible: true }), '这条其实是疑点');
  await typed.save.props.onPress();

  assert.equal(modal.calls.saveQuickNote.length, 1, 'a failing model must never block the capture');
  assert.equal(modal.calls.saveQuickNote[0][3], 'INSPIRATION', 'it fails closed to 灵感');
});

test('the pending category choice travels with the unsaved capture draft', () => {
  const modal = modalHarness();

  const opened = modal.render({ visible: true });
  modal.typeInto(opened, '未保存的想法');
  modal.chooseCategory(modal.render({ visible: true }), 'DOUBT');

  modal.render({ visible: false });
  const restored = modal.render({ visible: true });
  assert.equal(restored.input.props.value, '未保存的想法', 'the text is still buffered');
  assert.equal(restored.categoryControl.props.value, 'DOUBT', 'and so is the category the user picked');
  assert.equal(modal.refs[1].current.chosen, true, 'reopening must not hand the decision back to the model');
});

test('a successful new save resets the category choice for the next capture', async () => {
  const modal = modalHarness();

  modal.chooseCategory(modal.render({ visible: true }), 'DOUBT');
  const typed = modal.typeInto(modal.render({ visible: true }), '一条疑点');
  await typed.save.props.onPress();
  assert.equal(modal.calls.saveQuickNote[0][3], 'DOUBT');

  const reopened = modal.render({ visible: true });
  assert.equal(reopened.categoryControl.props.value, 'INSPIRATION', 'the next capture starts fresh');
  assert.equal(modal.refs[1].current.chosen, false, 'and the model may suggest again');
});

test('editing shows the stored category and leaves it untouched when unchanged', async () => {
  const modal = modalHarness();

  // A parent holds the note being edited in its own state, so the reference is
  // stable across re-renders while typing. Reusing one object here mirrors that;
  // passing a fresh literal every render would re-run the seeding effect and
  // reset the control, which never happens in the app.
  const doubtNote = { id: 'note-doubt', content: '之前存下来的疑点', category: 'DOUBT' };
  const editor = modal.render({ visible: true, editingNote: doubtNote });
  assert.equal(editor.categoryControl.props.value, 'DOUBT', 'the sheet reflects what is already stored');

  const typed = modal.typeInto(editor, '只改了正文');
  await typed.save.props.onPress();
  assert.deepEqual(modal.calls.updateNote, [['note-doubt', '只改了正文']]);
  assert.deepEqual(modal.calls.setNoteCategory, [], 'an unchanged category is not rewritten');
  assert.equal(modal.calls.classifyInputs.length, 0, 'editing never re-runs the model');
});

test('editing commits a category correction through setNoteCategory', async () => {
  const modal = modalHarness();

  const doubtNote = { id: 'note-doubt', content: '之前存下来的疑点', category: 'DOUBT' };
  const editor = modal.render({ visible: true, editingNote: doubtNote });
  // Correct 疑点 → 灵感 and save the body alongside it.
  modal.chooseCategory(editor, 'INSPIRATION');
  const typed = modal.typeInto(modal.render({ visible: true, editingNote: doubtNote }), '正文也顺手改了');
  await typed.save.props.onPress();

  assert.deepEqual(modal.calls.updateNote, [['note-doubt', '正文也顺手改了']]);
  assert.deepEqual(modal.calls.setNoteCategory, [['note-doubt', 'INSPIRATION']],
    'the correction is written with an explicit target value');
  assert.equal(modal.calls.classifyInputs.length, 0, 'editing never re-runs the model');
});

test('a failed category write on edit reports the failure instead of a false save', async () => {
  const modal = modalHarness({ setNoteCategory: async () => { throw new Error('room write failed'); } });

  const note = { id: 'note-x', content: '正文', category: 'INSPIRATION' };
  const editor = modal.render({ visible: true, editingNote: note });
  modal.chooseCategory(editor, 'DOUBT');
  const typed = modal.typeInto(editor, '改了正文也改了分类');
  await typed.save.props.onPress();

  const failed = modal.render({ visible: true, editingNote: note });
  assert.ok(failed.text('分类未保存，请重试'), 'the failed category write stays visible for a retry');
  assert.deepEqual(modal.calls.updateNote, [['note-x', '改了正文也改了分类']], 'content already committed');
  assert.deepEqual(modal.calls.setNoteCategory, [['note-x', 'DOUBT']], 'the category write was attempted');
});

