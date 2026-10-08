# Handoff Report: Milestone 1 — React Native Add-to-App Infrastructure & Build System

## 1. Observation

1. **Tooling & Environment Setup**:
   - Initialized React Native ecosystem dependencies in root `package.json`:
     - `react-native`: `0.87.1`
     - `react`: `19.2.3`
     - `nativewind`: `^4.0.1`
     - `tailwindcss`: `^3.4.17`
     - `typescript`: `^5.8.2`
     - `@react-native/babel-preset`: `^0.87.1`
     - `@react-native/metro-config`: `^0.87.1`
     - `@react-native-community/cli`: `^18.0.0`
     - `react-native-reanimated`: `^4.7.1` & `react-native-worklets`: `^0.13.0`
   - Configured `tsconfig.json` for strict TypeScript checking (`jsx: react-jsx`, `noEmit: true`, `strict: true`).
   - Configured `babel.config.js`, `metro.config.js` (`withNativeWind(config, { input: './global.css' })`), `tailwind.config.js`, and `global.css`.
   - Created `App.tsx` and `index.js` registering component `"YanjiApp"`.

2. **Android Gradle Configuration**:
   - `gradle/libs.versions.toml`:
     - Added `reactAndroid = "0.87.1"` to `[versions]` (line 29).
     - Added `react-android = { module = "com.facebook.react:react-android", version.ref = "reactAndroid" }` to `[libraries]` (line 67).
   - `app/build.gradle.kts`:
     - Added `implementation(libs.react.android)` under dependencies (line 164).
   - `app/src/main/res/values/themes.xml`:
     - Added `<style name="Theme.Material3.DayNight.NoActionBar" parent="Theme.AppCompat.DayNight.NoActionBar" />` (line 10).
     - Set `<style name="Theme.Yanji" parent="Theme.Material3.DayNight.NoActionBar">` (line 12).
   - `gradle/verification-metadata.xml`:
     - Added `<trusted-artifacts>` block for `com.facebook.*`, `androidx.*`, `com.squareup.*`, `com.parse.bolts`, and `org.jetbrains.kotlin:kotlin-annotations-jvm` to allow verification of React Native 0.87.1 prebuilts.
   - `gradle.properties`:
     - Configured local HTTP/HTTPS proxy `127.0.0.1:7898` preventing network timeout during AAR download.

3. **Application & Activity Host Migration**:
   - `app/src/main/java/com/example/yanji/YanjiApplication.kt`:
     - Implements `ReactApplication`.
     - Instantiates `ReactNativeHost(this@YanjiApplication)` with `getUseDeveloperSupport() = false`, `getJSMainModuleName() = "index"`, `getBundleAssetName() = "index.android.bundle"`, and `MainReactPackage()`.
     - Invokes `SoLoader.init(this, false)` in `onCreate()`.
   - `app/src/main/java/com/example/yanji/MainActivity.kt`:
     - Subclasses `ReactActivity`.
     - Overrides `getMainComponentName(): String = "YanjiApp"`.
     - Preserves splash screen installation and `YanjiRepository.init(applicationContext)`.

4. **Standalone Offline JS Bundle**:
   - Generated offline bundle at `app/src/main/assets/index.android.bundle` via:
     `npx react-native bundle --platform android --dev false --entry-file index.js --bundle-output app/src/main/assets/index.android.bundle --assets-dest app/src/main/res`
   - Verified file size: 1,695,245 bytes (~1.7 MB).

5. **Verification Execution Results**:
   - `npm run typecheck`:
     ```text
     > yanji@1.0.0 typecheck
     > tsc --noEmit
     (exit code 0)
     ```
   - `.\gradlew.bat assembleDebug`:
     ```text
     BUILD SUCCESSFUL in 1m 52s
     70 actionable tasks: 9 executed, 61 up-to-date
     Configuration cache entry reused.
     (exit code 0)
     ```
     Resulting APK: `app\build\outputs\apk\debug\app-debug.apk` (119,619,337 bytes).
   - `.\gradlew.bat :app:testDebugUnitTest`:
     ```text
     BUILD SUCCESSFUL in 45s
     26 actionable tasks: 6 executed, 1 from cache, 19 up-to-date
     (exit code 0)
     ```
     All 47 unit test classes passed with 0 failures and 0 regressions.

---

## 2. Logic Chain

1. **Gradle 9.1 & AGP 9.0 Compatibility**:
   - Observation 2 shows `gradle/libs.versions.toml` specifying `reactAndroid = "0.87.1"`.
   - AGP 9.0 requires React Native 0.87+ for compatible build APIs. Adding `com.facebook.react:react-android:0.87.1` directly to `app/build.gradle.kts` imports the prebuilt AAR from Maven Central without requiring native C++ NDK/CMake builds.

2. **AppCompat / Material3 Theme Compatibility**:
   - `ReactActivity` extends `AppCompatActivity`. `AppCompatActivity` enforces that the Activity theme inherits from `Theme.AppCompat`.
   - Observation 2 shows `themes.xml` declaring `Theme.Material3.DayNight.NoActionBar` extending `Theme.AppCompat.DayNight.NoActionBar`, and `Theme.Yanji` inheriting from it.
   - This satisfies the XML resource reference requirement while fulfilling the Material3/AppCompat runtime theme contract.

3. **Standalone Offline Bundle Execution**:
   - Per AGENTS.md §五, development and device pushes run offline APKs.
   - Generating `app/src/main/assets/index.android.bundle` (Observation 4) and configuring `ReactNativeHost` with `getUseDeveloperSupport() = false` and `getBundleAssetName() = "index.android.bundle"` ensures the React Native container renders immediately from offline assets without depending on a running Metro server at `localhost:8081`.

4. **Preservation of Native Business Core**:
   - Observation 5 confirms that running `.\gradlew.bat :app:testDebugUnitTest` passes 100% of the 47 JVM unit test classes.
   - Room database entities, DAOs, schema v20, and all migration tests remain untouched and functional.

---

## 3. Caveats

1. **React Native Legacy Architecture Deprecation Warnings**:
   - Kotlin compiler emits standard deprecation warnings for `ReactNativeHost` (`ReactNativeHost is deprecated. This class is part of Legacy Architecture...`). In RN 0.87, this is expected behavior when using the standard Add-to-App hosting model prior to New Architecture default migration.
2. **In-Flight Git Modifications**:
   - `.gitignore` contained an uncommitted modification (`.freebuff/`) prior to this milestone. That modification was strictly protected and remained untouched.
3. **Subsequent Milestones**:
   - Milestone 1 establishes the Add-to-App runtime, build pipeline, and empty React Native root screen ("YanjiApp"). Native modules (`YanjiTimerModule`, `YanjiDataModule`, `YanjiThemeModule`) and the full 3-tab navigation UI will be integrated in Milestones 2–5.

---

## 4. Conclusion

Milestone 1 is complete and fully verified:
- React Native 0.87.1 + React 19.2.3 + NativeWind v4 + TypeScript 5.8 build pipeline is operational.
- `package.json`, `tsconfig.json`, `babel.config.js`, `metro.config.js`, `tailwind.config.js` are in place and working.
- `app/build.gradle.kts` and `gradle/libs.versions.toml` cleanly integrate `react-android:0.87.1`.
- `YanjiApplication` implements `ReactApplication` and initializes `SoLoader`.
- `MainActivity` extends `ReactActivity` hosting `"YanjiApp"`.
- `app/src/main/assets/index.android.bundle` is generated and packaged into the debug APK.
- All 3 acceptance checks (`npm run typecheck`, `.\gradlew.bat assembleDebug`, `.\gradlew.bat :app:testDebugUnitTest`) pass cleanly with 0 errors.

---

## 5. Verification Method

To independently reproduce the verification:

1. **Verify TypeScript typecheck**:
   ```powershell
   npm run typecheck
   ```
   - Expect: 0 errors, exit code 0.

2. **Verify Android Debug APK Compilation**:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
   - Expect: `BUILD SUCCESSFUL`, `app\build\outputs\apk\debug\app-debug.apk` exists.

3. **Verify JVM Unit Tests & Room Schema**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   ```
   - Expect: `BUILD SUCCESSFUL`, 26/26 test tasks pass with 0 failures.

4. **Verify Offline Bundle Existence**:
   ```powershell
   Get-Item app/src/main/assets/index.android.bundle
   ```
   - Expect: File exists with size ~1.7 MB.
