import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

const { getYanjiIcon } = loadTypeScript('src/components/icons.ts', {
  react: {
    default: {
      createElement: (type, props, ...children) => ({
        type,
        props: props ?? {},
        children,
      }),
    },
  },
  'react-native-svg': { default: 'Svg', Path: 'Path' },
});

function renderSolid(name) {
  const Glyph = getYanjiIcon(name);
  const tree = Glyph({ fill: '#000', color: '#000' });
  return tree.children.flat(Infinity).filter(node => node?.type === 'Path');
}

test('solid focus keeps Heroicons evenodd cut-outs', () => {
  const [clock] = renderSolid('focus');
  assert.equal(clock.props.fillRule, 'evenodd');
  assert.equal(clock.props.clipRule, 'evenodd');
});

test('Heroicons fill rules stay on the exact source paths', () => {
  for (const name of [
    'settings',
    'check',
    'close',
    'more',
    'add',
    'remove',
    'back',
    'forward',
    'chevronUp',
    'chevronDown',
    'play',
    'pause',
    'delete',
    'chartMinimal',
    'star',
    'reset',
    'doubt',
    'doubtResolved',
    'doubtUnresolved',
    'sparkles',
    'key',
    'clock3',
  ]) {
    const paths = renderSolid(name);
    assert.equal(paths.length, 1, `${name} has one solid path`);
    assert.equal(paths[0].props.fillRule, 'evenodd', `${name} keeps fillRule`);
    assert.equal(paths[0].props.clipRule, 'evenodd', `${name} keeps clipRule`);
  }
});

test('multi-path icons only mark the paths that declare evenodd', () => {
  const calendar = renderSolid('calendar');
  assert.equal(calendar.length, 2);
  assert.equal(calendar[0].props.fillRule, undefined);
  assert.equal(calendar[0].props.clipRule, undefined);
  assert.equal(calendar[1].props.fillRule, 'evenodd');
  assert.equal(calendar[1].props.clipRule, 'evenodd');

  const tasks = renderSolid('tasks');
  assert.equal(tasks.length, 2);
  for (const path of tasks) {
    assert.equal(path.props.fillRule, 'evenodd');
    assert.equal(path.props.clipRule, 'evenodd');
  }

  const inspiration = renderSolid('inspiration');
  assert.equal(inspiration.length, 2);
  assert.equal(inspiration[0].props.fillRule, undefined);
  assert.equal(inspiration[0].props.clipRule, undefined);
  assert.equal(inspiration[1].props.fillRule, 'evenodd');
  assert.equal(inspiration[1].props.clipRule, 'evenodd');
});

test('icons without evenodd are not globally polluted by the renderer', () => {
  for (const name of ['today', 'review', 'sprout', 'compose', 'target']) {
    const paths = renderSolid(name);
    assert.ok(paths.length > 0);
    for (const path of paths) {
      assert.equal(path.props.fillRule, undefined, `${name} keeps its source fillRule`);
      assert.equal(path.props.clipRule, undefined, `${name} keeps its source clipRule`);
    }
  }
});

test('force-outline icons always render their outline paths', () => {
  const Glyph = getYanjiIcon('starOutline');
  const tree = Glyph({ fill: '#000', color: '#000' });
  const [path] = tree.children.flat(Infinity).filter(node => node?.type === 'Path');
  assert.equal(path.props.fill, 'none');
  assert.equal(path.props.stroke, '#000');
  assert.equal(path.props.fillRule, undefined);
  assert.equal(path.props.clipRule, undefined);
});
