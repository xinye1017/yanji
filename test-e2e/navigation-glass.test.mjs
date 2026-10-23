import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { bottomTabLayout, YanjiLiquidGlass } from '../src/theme/liquidGlass.ts';

test('the final scroll action clears the dock with gesture and three-button insets', () => {
  for (const inset of [0, 24, 48]) {
    for (const fontScale of [0.85, 1, 1.5, 2, 3]) {
      const layout = bottomTabLayout(fontScale, inset, 16);
      assert.ok(layout.bottom >= inset);
      assert.ok(layout.contentPadding - layout.height - layout.bottom >= 16);
      const itemHeight = layout.height - 2 * YanjiLiquidGlass.padding;
      const contentHeight = YanjiLiquidGlass.iconSize + YanjiLiquidGlass.labelGap
        + YanjiLiquidGlass.labelLineHeight * fontScale + 2 * YanjiLiquidGlass.itemPadding;
      assert.ok(itemHeight >= contentHeight);
      assert.ok(itemHeight >= 48);
    }
  }
});

test('the backdrop excludes the dock and secondary settings layer', () => {
  const app = readFileSync(new URL('../App.tsx', import.meta.url), 'utf8');
  const targetEnd = app.indexOf('</BlurTarget>');
  assert.ok(app.includes('<BlurTarget id={YanjiLiquidGlass.targetId}'));
  for (const screen of ['Today', 'Focus', 'Review']) {
    const position = app.indexOf(`<${screen}Screen`);
    assert.ok(position >= 0 && targetEnd > position);
  }
  // The dock now receives a visibility prop, but remains one mounted sibling
  // of BlurTarget and below the secondary settings overlay in source order.
  const dockNodes = [...app.matchAll(/<BottomTabBar\b/g)];
  assert.equal(dockNodes.length, 1, 'the shell must render exactly one glass dock');
  const dockPosition = dockNodes[0].index;
  assert.ok(targetEnd < dockPosition, 'glass dock must not be inside its sampled BlurTarget');
  assert.ok(dockPosition < app.indexOf('<SettingsScreen />'), 'settings overlay must cover the dock');
  for (const screen of ['Today', 'Focus', 'Review']) {
    const source = readFileSync(new URL(`../src/screens/${screen}Screen.tsx`, import.meta.url), 'utf8');
    assert.match(source, /paddingBottom: contentPadding/);
  }
});

test('the dock resolves its surface from the glass material with no second outline', () => {
  for (const variant of ['light', 'dark']) {
    const material = YanjiLiquidGlass[variant];
    for (const role of ['tint', 'backdrop']) {
      assert.equal(typeof material[role], 'string', `YanjiLiquidGlass.${variant}.${role} is a colour`);
      assert.ok(material[role].length > 0, `YanjiLiquidGlass.${variant}.${role} is not empty`);
    }
    assert.notEqual(material.backdrop, material.tint,
      `${variant} keeps the unblurred backdrop distinct from the sampled tint`);
  }

  const dock = readFileSync(new URL('../src/navigation/BottomTabBar.tsx', import.meta.url), 'utf8');
  for (const role of ['tint', 'backdrop']) {
    assert.match(dock, new RegExp(`material\\.${role}\\b`), `the dock paints its ${role} from the material`);
  }
  assert.ok(!/rgba\(/.test(dock),
    'the dock hardcodes no colour of its own; every one comes from the material');
  assert.doesNotMatch(dock, /borderColor:|borderWidth:/,
    'only the native directional reflection defines the capsule edge');
});

test('the selected dock tab is a solid icon with no selection surface', () => {
  const dock = readFileSync(new URL('../src/navigation/BottomTabBar.tsx', import.meta.url), 'utf8');
  const material = readFileSync(new URL('../src/theme/liquidGlass.ts', import.meta.url), 'utf8');
  assert.match(dock, /filled=\{isActive\}/, 'the active glyph must switch to its solid variant');
  assert.doesNotMatch(dock, /material\.selection|Animated\.View|calculatePillDeformation/,
    'the dock must not paint or animate a selection pill');
  assert.doesNotMatch(material, /\bselection\s*:/, 'the glass material has no selection colour');
});

test('both native glass components are registered through the manual Fabric pipeline', () => {
  const registration = readFileSync(new URL('../app/src/main/jni/OnLoad.cpp', import.meta.url), 'utf8');
  for (const component of ['SajjadBlurOverlay', 'SajjadBlurTarget']) {
    assert.ok(registration.includes(`concreteComponentDescriptorProvider<${component}ComponentDescriptor>()`));
  }
  const gradle = readFileSync(new URL('../app/build.gradle.kts', import.meta.url), 'utf8');
  assert.ok(gradle.includes(':react-native-blur-overlay:generateCodegenArtifactsFromSchema'));
  const cmake = readFileSync(new URL('../app/src/main/jni/CMakeLists.txt', import.meta.url), 'utf8');
  assert.ok(cmake.includes('react-native-blur-overlay:RNBlurOverlaySpec'));
});

test('the borderless dock row uses its full height and centers its content', () => {
  const dock = readFileSync(new URL('../src/navigation/BottomTabBar.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(dock, /hairlineWidth|contentHeight/);
  assert.match(dock, /width:\s*'100%',\s*height,/);
  assert.match(dock, /justifyContent:\s*'center'/);
});

test('timeline entries share the compact spine and spacing contract', () => {
  const review = readFileSync(new URL('../src/screens/ReviewScreen.tsx', import.meta.url), 'utf8');
  const row = readFileSync(new URL('../src/components/TimelineCompactRow.tsx', import.meta.url), 'utf8');
  assert.match(review, /<TimelineCompactRow/);
  assert.match(review, /isFirst=\{isFirst\}\s+isLast=\{isLast\}/);
  assert.match(review, /onMore=\{anchor => onOpenMenu\(entry, anchor\)\}/);
  // The row owns the clearance around the node and the inter-row gap, so Today
  // and Review cannot drift into two different spines.
  assert.match(row, /clearance|center - nodeRadius - YanjiSpacing\.xs/);
  assert.match(row, /bottom: -layout\.momentItemGap/);
});

