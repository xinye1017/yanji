# DISPATCH: worker_m1_1

You are worker_m1_1 (teamwork_preview_worker).
Your working directory is: d:\AI项目\yanji\.agents\teamwork\worker_m1_1
Project root: d:\AI项目\yanji
Original request: d:\AI项目\yanji\.agents\teamwork\ORIGINAL_REQUEST.md
Project specification: d:\AI项目\yanji\PROJECT.md
Survey findings: d:\AI项目\yanji\.agents\teamwork\explorer_survey_rn_env_1\handoff.md

## Scope: Milestone 1 — React Native Add-to-App Infrastructure & Build System

Exclusive write ownership for this milestone:
- `package.json`, `package-lock.json`
- `tsconfig.json`
- `babel.config.js`
- `metro.config.js`
- `tailwind.config.js`
- `gradle/libs.versions.toml` (only adding `react-android` version and library declaration)
- `app/build.gradle.kts` (adding `implementation(libs.react.android)` and assets sourceSets if needed)
- `app/src/main/res/values/themes.xml` (updating `Theme.Yanji` parent to `Theme.Material3.DayNight.NoActionBar`)
- `app/src/main/java/com/example/yanji/YanjiApplication.kt` (implementing `ReactApplication` + `SoLoader.init`)
- `app/src/main/java/com/example/yanji/MainActivity.kt` (extending `ReactActivity`)
- `app/src/main/assets/index.android.bundle` (initial generated standalone offline bundle)
- `index.js`, `App.tsx` (entry points)

DO NOT touch:
- `app/src/main/java/com/example/yanji/data/db/**` (No Room changes)
- `app/src/test/**` (All 47 tests must remain passing)
- `.gitignore` (Protect in-flight modifications)

## Tasks
1. Initialize `package.json` with React Native 0.87.1, React 19.2.3, TypeScript 5.8, NativeWind v4 (`nativewind@^4.0.1`), Tailwind CSS (`tailwindcss@^3.4.17`), `@react-native/babel-preset`, `@react-native/metro-config`.
   Run `npm install` (or npm install --legacy-peer-deps / --force if needed for React 19 peer deps).
2. Configure `tsconfig.json` (strict type-checking, JSX react-jsx).
   Ensure `npm run typecheck` (`tsc --noEmit`) passes cleanly with 0 errors.
3. Configure `babel.config.js`, `metro.config.js`, `tailwind.config.js`.
4. Update `gradle/libs.versions.toml` with `reactAndroid = "0.87.1"` and `react-android = { module = "com.facebook.react:react-android", version.ref = "reactAndroid" }`.
5. Update `app/build.gradle.kts` to include `implementation(libs.react.android)`.
6. Update `themes.xml` so `Theme.Yanji` parent is `Theme.Material3.DayNight.NoActionBar`.
7. Update `YanjiApplication.kt` to implement `ReactApplication`, configure `ReactNativeHost`, and call `SoLoader.init(this, false)`.
8. Update `MainActivity.kt` to extend `ReactActivity` with `getMainComponentName() = "YanjiApp"`.
9. Create `index.js` and `App.tsx` registering `YanjiApp`.
10. Generate initial offline standalone bundle via `npx react-native bundle --platform android --dev false --entry-file index.js --bundle-output app/src/main/assets/index.android.bundle --assets-dest app/src/main/res`.
11. Run verification commands:
    - `npm run typecheck`
    - `.\gradlew.bat assembleDebug`
    - `.\gradlew.bat :app:testDebugUnitTest`
12. Report verification results and write `handoff.md` in your working directory.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.


## 2026-10-08T13:23:05Z
Read d:\AI项目\yanji\.agents\teamwork\ORIGINAL_REQUEST.md, d:\AI项目\yanji\PROJECT.md, d:\AI项目\yanji\.agents\teamwork\explorer_survey_rn_env_1\handoff.md, and d:\AI项目\yanji\.agents\teamwork\worker_m1_1\DISPATCH.md.

Implement Milestone 1: React Native Add-to-App Infrastructure & Build System:
1. package.json, TypeScript, NativeWind v4, Metro, Babel setup. Run npm install.
2. Update gradle/libs.versions.toml and app/build.gradle.kts for react-android 0.87.1.
3. Update themes.xml to Material3.DayNight.NoActionBar.
4. Update YanjiApplication.kt (ReactApplication, SoLoader) and MainActivity.kt (ReactActivity).
5. Generate standalone offline bundle in app/src/main/assets/index.android.bundle.
6. Verify npm run typecheck, .\gradlew.bat assembleDebug, and .\gradlew.bat :app:testDebugUnitTest.
7. Write handoff.md in d:\AI项目\yanji\.agents\teamwork\worker_m1_1\handoff.md and report back via send_message.

DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.
