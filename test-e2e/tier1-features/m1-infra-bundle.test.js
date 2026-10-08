/**
 * Tier 1 — Features 1 to 4: Infrastructure, Standalone Bundle, Theme & Host Activity
 */

import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from '../framework/harness.js';
import { assert, assertEqual, assertMatches } from '../framework/assertions.js';

const PROJECT_ROOT = path.resolve(process.cwd());

export function registerM1Tests() {
  describe('Tier 1: Feature 1 — RN Add-to-App Gradle & Maven Setup', () => {
    it('verifies react-android 0.87.1 is declared in gradle/libs.versions.toml', () => {
      const tomlPath = path.join(PROJECT_ROOT, 'gradle', 'libs.versions.toml');
      assert(fs.existsSync(tomlPath), `libs.versions.toml must exist at ${tomlPath}`);
      const content = fs.readFileSync(tomlPath, 'utf8');
      assertMatches(content, /react-android|reactAndroid/, 'react-android dependency must be declared in libs.versions.toml');
      assertMatches(content, /0\.87\.\d+/, 'react-android version must be 0.87.x for Gradle 9.1 / AGP 9.0 compatibility');
    });

    it('verifies app/build.gradle.kts integrates react-android', () => {
      const buildGradlePath = path.join(PROJECT_ROOT, 'app', 'build.gradle.kts');
      assert(fs.existsSync(buildGradlePath), `app/build.gradle.kts must exist at ${buildGradlePath}`);
      const content = fs.readFileSync(buildGradlePath, 'utf8');
      assert(
        content.includes('libs.react.android') || content.includes('react-android'),
        'app/build.gradle.kts must include react-android implementation dependency'
      );
    });

    it('verifies settings.gradle.kts includes mavenCentral() for react-android AAR resolution', () => {
      const settingsPath = path.join(PROJECT_ROOT, 'settings.gradle.kts');
      assert(fs.existsSync(settingsPath), `settings.gradle.kts must exist`);
      const content = fs.readFileSync(settingsPath, 'utf8');
      assert(content.includes('mavenCentral()'), 'Maven Central repository must be configured in settings.gradle.kts');
    });
  });

  describe('Tier 1: Feature 2 — Offline Standalone JS Bundle', () => {
    it('verifies package.json defines offline bundle generation targeting assets/index.android.bundle', () => {
      const pkgPath = path.join(PROJECT_ROOT, 'package.json');
      assert(fs.existsSync(pkgPath), 'package.json must exist at project root');
      const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
      assert(pkg.scripts && pkg.scripts['bundle:android'], 'package.json must declare bundle:android script');
      const script = pkg.scripts['bundle:android'];
      assert(script.includes('index.android.bundle'), 'bundle script must output to index.android.bundle');
      assert(script.includes('--dev false'), 'standalone bundle must be generated with --dev false');
    });

    it('verifies target asset directory structure exists for standalone offline deployment', () => {
      const assetsDir = path.join(PROJECT_ROOT, 'app', 'src', 'main', 'assets');
      assert(fs.existsSync(assetsDir), `Assets directory must exist at ${assetsDir}`);
    });
  });

  describe('Tier 1: Feature 3 — Material3 Theme Compatibility', () => {
    it('verifies Theme.Yanji inherits from Material3 / AppCompat to prevent ReactActivity launch crash', () => {
      const themesPath = path.join(PROJECT_ROOT, 'app', 'src', 'main', 'res', 'values', 'themes.xml');
      assert(fs.existsSync(themesPath), 'themes.xml must exist');
      const content = fs.readFileSync(themesPath, 'utf8');
      const usesMaterial3OrAppCompat =
        content.includes('Theme.Material3') ||
        content.includes('Theme.AppCompat');
      assert(
        usesMaterial3OrAppCompat,
        'Theme.Yanji parent must inherit from Theme.Material3 or Theme.AppCompat for ReactActivity compatibility'
      );
      assert(
        !content.includes('parent="android:Theme.Material.Light.NoActionBar"'),
        'Obsolete android:Theme.Material.Light.NoActionBar parent must be replaced'
      );
    });

    it('verifies values-night/themes.xml also inherits from Material3 / AppCompat (dark mode cold-launch guard)', () => {
      const nightThemesPath = path.join(PROJECT_ROOT, 'app', 'src', 'main', 'res', 'values-night', 'themes.xml');
      assert(fs.existsSync(nightThemesPath), 'values-night/themes.xml must exist');
      const content = fs.readFileSync(nightThemesPath, 'utf8');
      const usesMaterial3OrAppCompat =
        content.includes('Theme.Material3') ||
        content.includes('Theme.AppCompat');
      assert(
        usesMaterial3OrAppCompat,
        'values-night Theme.Yanji parent must inherit from Theme.Material3 or Theme.AppCompat for ReactActivity dark-mode compatibility'
      );
      assert(
        !content.includes('parent="android:Theme.Material.NoActionBar"'),
        'values-night must NOT use android:Theme.Material.NoActionBar — causes AppCompatActivity IllegalStateException'
      );
    });
  });

  describe('Tier 1: Feature 4 — React Native Host Activity & App', () => {
    it('verifies YanjiApplication implements ReactApplication and initializes SoLoader', () => {
      const appPath = path.join(PROJECT_ROOT, 'app', 'src', 'main', 'java', 'com', 'example', 'yanji', 'YanjiApplication.kt');
      assert(fs.existsSync(appPath), 'YanjiApplication.kt must exist');
      const content = fs.readFileSync(appPath, 'utf8');
      assert(content.includes('ReactApplication'), 'YanjiApplication must implement ReactApplication');
      assert(content.includes('SoLoader.init'), 'YanjiApplication must initialize SoLoader for native library loading');
    });

    it('verifies MainActivity extends ReactActivity with main component registration', () => {
      const mainPath = path.join(PROJECT_ROOT, 'app', 'src', 'main', 'java', 'com', 'example', 'yanji', 'MainActivity.kt');
      assert(fs.existsSync(mainPath), 'MainActivity.kt must exist');
      const content = fs.readFileSync(mainPath, 'utf8');
      assert(content.includes('ReactActivity'), 'MainActivity must inherit from ReactActivity');
      assert(content.includes('getMainComponentName') || content.includes('"YanjiApp"'), 'MainActivity must specify main component name YanjiApp');
    });
  });
}
