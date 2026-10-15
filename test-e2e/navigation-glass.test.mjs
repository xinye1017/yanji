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
