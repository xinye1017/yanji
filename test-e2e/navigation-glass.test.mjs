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

test('the dock resolves its whole surface from the glass material', () => {
  // The backdrop and hairline used to be literals in the component while the
  // tint and selection came from `YanjiLiquidGlass`, so the same surface had
  // two sources and a palette change could update one and miss the other. The
  // material now owns every colour the dock paints.
  for (const variant of ['light', 'dark']) {
    const material = YanjiLiquidGlass[variant];
    for (const role of ['tint', 'backdrop', 'border', 'selection']) {
      assert.equal(typeof material[role], 'string', `YanjiLiquidGlass.${variant}.${role} is a colour`);
      assert.ok(material[role].length > 0, `YanjiLiquidGlass.${variant}.${role} is not empty`);
    }
    assert.notEqual(material.backdrop, material.tint,
      `${variant} keeps the unblurred backdrop distinct from the sampled tint`);
  }

  const dock = readFileSync(new URL('../src/navigation/BottomTabBar.tsx', import.meta.url), 'utf8');
  for (const role of ['tint', 'backdrop', 'border', 'selection']) {
    assert.match(dock, new RegExp(`material\\.${role}\\b`), `the dock paints its ${role} from the material`);
  }
  assert.ok(!/rgba\(/.test(dock),
    'the dock hardcodes no colour of its own; every one comes from the material');
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

test('the dock pill is centered vertically with hairlineWidth compensation', () => {
  const dock = readFileSync(new URL('../src/navigation/BottomTabBar.tsx', import.meta.url), 'utf8');
  assert.match(dock, /contentHeight\s*=\s*height\s*-\s*2\s*\*\s*paper\.layout\.hairlineWidth/);
  assert.match(dock, /height:\s*contentHeight/);
  assert.match(dock, /justifyContent:\s*'center'/);
});

test('timeline entries connect with a continuous spine and note cards align with the dot', () => {
  const review = readFileSync(new URL('../src/screens/ReviewScreen.tsx', import.meta.url), 'utf8');
  assert.match(review, /isFirst=\{index === 0\}/);
  assert.match(review, /isLast=\{index === entries\.length - 1\}/);
  assert.match(review, /dotCenterY\s*=\s*isSession\s*\?\s*headBand\s*\/\s*2\s*:\s*\(layout\.timelineNodeSize\s*\/\s*2\s*\+\s*2\)/);
  assert.match(review, /!isFirst\s*\?\s*\(\s*<View[\s\S]*?top:\s*0[\s\S]*?height:\s*dotCenterY/);
  assert.match(review, /!isLast\s*\?\s*\(\s*<View[\s\S]*?top:\s*dotCenterY[\s\S]*?bottom:\s*0/);
});

