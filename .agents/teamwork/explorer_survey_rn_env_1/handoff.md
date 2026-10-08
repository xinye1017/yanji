# Handoff Report: React Native Add-to-App Environment Survey & Integration Architecture

## 1. Observation

### 1.1 Existing Repository Layout & Dependency Audit
- **Project Structure**:
  - The repository root `d:\AI项目\yanji\` is an Android Gradle root project containing modules `:app`, `:macrobenchmark`, `:baselineprofile`, plus `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, and `gradle/`.
  - There is currently **no** `package.json`, **no** `node_modules/`, and **no** separate `android/` subfolder.
- **Gradle & AGP Build Configuration**:
  - `gradle/libs.versions.toml`:
    - Line 2: `androidGradlePlugin = "9.0.1"`
    - Line 19: `kotlin = "2.3.20"`
    - Line 20: `ksp = "2.3.11"`
  - `settings.gradle.kts`:
    - Line 14–16:
      ```kotlin
      dependencyResolutionManagement {
          repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
          repositories {
              google { ... }
              mavenCentral()
          }
      }
      ```
  - `app/build.gradle.kts`:
    - Line 35: `compileSdk = 36`
    - Line 38: `minSdk = 24`
    - Line 39: `targetSdk = 36`
    - Line 90–91: `sourceCompatibility = JavaVersion.VERSION_17`, `targetCompatibility = JavaVersion.VERSION_17`
    - Line 123: `jvmToolchain(17)`
- **Existing Android Theme & Lifecycle**:
  - `app/src/main/res/values/themes.xml`:
    - Line 10: `<style name="Theme.Yanji" parent="android:Theme.Material.Light.NoActionBar">`
    - Line 16: `<style name="Theme.Yanji.Starting" parent="Theme.SplashScreen">`
  - `app/src/main/java/com/example/yanji/MainActivity.kt`:
    - Currently extends `androidx.activity.ComponentActivity`.
    - Handles `installSplashScreen()`, `enableEdgeToEdge()`, `onNewIntent()`, and hosts Jetpack Compose `setContent`.
  - `app/src/main/java/com/example/yanji/YanjiApplication.kt`:
    - Extends `android.app.Application`.
    - Initializes `YanjiRepository.init(this)` and `container = DefaultAppContainer(this)`.

### 1.2 Tooling Environment Audit
Command execution results:
- `node -v`: `v22.20.0`
- `npm -v`: `10.9.3`
- `npx --version`: `10.9.3`
- `java -version`:
  ```text
  openjdk version "17.0.19" 2026-04-21
  OpenJDK Runtime Environment Temurin-17.0.19+10 (build 17.0.19+10)
  OpenJDK 64-Bit Server VM Temurin-17.0.19+10 (build 17.0.19+10, mixed mode, sharing)
  ```
- `.\gradlew.bat --version`:
  ```text
  ------------------------------------------------------------
  Gradle 9.1.0
  ------------------------------------------------------------
  Kotlin:        2.2.0
  Groovy:        4.0.28
  Launcher JVM:  17.0.19 (Eclipse Adoptium)
  OS:            Windows 11 10.0 amd64
  ```
- Android SDK check:
  - Location: `C:\Users\dex\Android\Sdk` (configured in `local.properties`).
  - Platforms: `android-36` installed.
  - NDK directory `C:\Users\dex\Android\Sdk\ndk` exists but contains **no installed NDK toolchain**.
  - CMake directory `C:\Users\dex\Android\Sdk\cmake` **does not exist**.
- Baseline build execution:
  - `.\gradlew.bat assembleDebug`: `BUILD SUCCESSFUL in 13s` (70 up-to-date tasks).
  - `.\gradlew.bat :app:testDebugUnitTest`: `BUILD SUCCESSFUL in 18s` (all 26 test tasks passed).

### 1.3 React Native & Maven Central Verification
- NPM Registry Check:
  - `react-native` dist-tags: latest is `0.87.1`.
  - `react-native@0.87.1` engines: `{ node: '^22.13.0 || ^24.3.0 || >= 26.0.0' }` (Node 22.20.0 satisfies this).
  - `react-native@0.87.1` peerDependencies: `{ react: '^19.2.3', '@types/react': '^19.1.1' }`.
- Maven Central Check:
  - Direct HTTP HEAD request: `https://repo1.maven.org/maven2/com/facebook/react/react-android/0.87.1/react-android-0.87.1.pom` returned `HTTP/1.1 200 OK`.
  - In RN 0.87.1, `com.facebook.react:react-android:0.87.1` is a unified AAR containing precompiled Hermes runtime (`libhermes.so`) and JNI bindings (`libreactnativejni.so`, `libfbjni.so`, `libjsi.so`), without requiring a separate `hermes-android` POM.
  - Precompiled AAR contains native `.so` for `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, eliminating any requirement to compile C++ via NDK or CMake.

---

## 2. Logic Chain

1. **Gradle 9 & AGP 9 Compatibility Dictates React Native Version**:
   - The project uses Gradle `9.1.0` and Android Gradle Plugin `9.0.1`.
   - React Native versions prior to `0.87` (such as 0.76 - 0.86) rely on deprecated/removed Gradle APIs in their `@react-native/gradle-plugin` that break under AGP 9.0+.
   - React Native `0.87` is the official release introducing Gradle 9 and AGP 9 compatibility.
   - Therefore, `react-native@0.87.1` is the strictly compatible and optimal React Native version for this repository.

2. **No NDK / CMake Installed Means Java/Kotlin Prebuilt Integration is Mandatory**:
   - SDK audit confirmed that neither NDK nor CMake is installed under `C:\Users\dex\Android\Sdk`.
   - Enabling C++ compilation (e.g., custom C++ TurboModules or compiling RN from C++ source) would fail immediately.
   - Using the prebuilt Maven artifact `com.facebook.react:react-android:0.87.1` together with pure Kotlin Native Modules (`ReactContextBaseJavaModule`) executes entirely on JVM/DEX bytecode and links against prebuilt `.so` libraries already packaged inside the AAR.
   - Therefore, the integration must rely on prebuilt AAR artifacts and Kotlin-based native module bridges.

3. **Workspace Layout Must Keep Android at Root**:
   - Per AGENTS.md rules §一 and §三, breaking scripts or restructuring the Android project into an `android/` subfolder would break `scripts/adb-push.sh`, `scripts/check-*.sh`, and existing CI anchors.
   - In React Native Add-to-App architectures, `package.json` resides cleanly at the workspace root (`d:\AI项目\yanji\package.json`), with `node_modules/` at root, and `app/` configured to reference the root `node_modules`.

4. **Theme Requirement for `ReactActivity`**:
   - `ReactActivity` inherits from `androidx.appcompat.app.AppCompatActivity`.
   - `AppCompatActivity` crashes at runtime with `IllegalStateException` if the Activity's theme does not inherit from `Theme.AppCompat` or `Theme.Material3`.
   - The current `Theme.Yanji` inherits from `android:Theme.Material.Light.NoActionBar`.
   - Changing `Theme.Yanji` parent to `Theme.Material3.DayNight.NoActionBar` or `Theme.AppCompat.Light.NoActionBar` is strictly required before `MainActivity` subclasses `ReactActivity`.

5. **Self-Contained Offline Debug APK Requirement**:
   - AGENTS.md §五 specifies a two-step push workflow: `./gradlew.bat assembleDebug` followed by `adb install` and `am start`.
   - By default in React Native, debug builds attempt to fetch JS bundles from Metro at `localhost:8081`. If Metro is not running, launching the app causes an instant red screen or bundle loading error.
   - To ensure `./gradlew.bat assembleDebug` succeeds standalone without network or dev server dependencies:
     - The Metro bundling command generates `app/src/main/assets/index.android.bundle` (`react-native bundle --platform android --dev false ...`).
     - `ReactNativeHost` / `ReactHost` defaults `getUseDeveloperSupport()` to `false` for standalone APK verification, or reads `assets:/index.android.bundle`.
     - This guarantees that any debug APK installed onto a physical device starts cleanly and renders immediately.

6. **NativeWind v4 & TypeScript Integration**:
   - NativeWind v4 (`nativewind@^4.0.1` + `tailwindcss@^3.4.17`) operates by compiling Tailwind utility classes into React Native `StyleSheet` objects via Babel and Metro (`withNativeWind`).
   - TypeScript compiler (`tsc --noEmit`) validates all typing with zero runtime overhead, satisfying the requirement for strict type verification (`npm run typecheck`).

---

## 3. Caveats

1. **Gradle `dependencyResolutionManagement` Repositories Mode**:
   - Currently, `settings.gradle.kts` sets `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`.
   - When integrating React Native npm dependencies or the React Native Gradle plugin, if any third-party plugin or script declares project-level `repositories {}`, Gradle will fail.
   - Switching `repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)` in `settings.gradle.kts` ensures smooth resolution while prioritizing settings repositories.
2. **SoLoader Initialization**:
   - React Native's JNI bridge requires `SoLoader.init(this, false)` inside `YanjiApplication.onCreate()`. Omitting this causes `UnsatisfiedLinkError` when C++ native libraries load.
3. **In-Flight Git Modifications**:
   - `git status --short` revealed `M .gitignore` (adding `.freebuff/`). This file must remain untouched during Add-to-App setup to protect unrelated in-flight work.

---

## 4. Conclusion

The existing project environment is healthy and fully prepared for Add-to-App integration:
- **Recommended React Native Stack**:
  - `react-native`: `0.87.1`
  - `react`: `19.2.3`
  - `@types/react`: `^19.1.1`
  - `typescript`: `^5.8.0`
  - `nativewind`: `^4.0.1`
  - `tailwindcss`: `^3.4.17`
  - `@react-native/babel-preset`: `^0.87.1`
  - `@react-native/metro-config`: `^0.87.1`
- **Native Android Stack**:
  - Add `com.facebook.react:react-android:0.87.1` to `gradle/libs.versions.toml` and `app/build.gradle.kts`.
  - Update `themes.xml` so `Theme.Yanji` inherits from `Theme.Material3.DayNight.NoActionBar`.
  - Subclass `ReactApplication` in `YanjiApplication` and `ReactActivity` in `MainActivity`.
  - Create `YanjiNativePackage` with `YanjiTimerModule`, `YanjiDataModule`, and `YanjiReviewModule` to bridge Room DAOs and `FocusTimerService`.
  - Bundle `index.android.bundle` into `app/src/main/assets/` to ensure offline standalone debug execution.

---

## 5. Verification Method

To independently verify this environment and the proposed setup:

1. **Verify Tooling Versions**:
   ```powershell
   node -v
   npm -v
   java -version
   .\gradlew.bat --version
   ```
   - Expect: Node >= 22.13.0, JDK 17, Gradle 9.1.0.

2. **Verify Maven Central Availability of React Native 0.87.1**:
   ```powershell
   curl.exe -I https://repo1.maven.org/maven2/com/facebook/react/react-android/0.87.1/react-android-0.87.1.pom
   ```
   - Expect: `HTTP/1.1 200 OK`.

3. **Verify Baseline Native Build & Tests**:
   ```powershell
   .\gradlew.bat assembleDebug
   .\gradlew.bat :app:testDebugUnitTest
   ```
   - Expect: `BUILD SUCCESSFUL` for both commands.

4. **Invalidation Conditions**:
   - If React Native < 0.87 is selected, AGP 9.0.1 / Gradle 9.1.0 build will fail due to deprecated Gradle APIs.
   - If NDK compilation is attempted without installing NDK via Android SDK manager, CMake/NDK build tasks will fail.
   - If `Theme.Yanji` parent is kept as `android:Theme.Material.Light.NoActionBar` while `MainActivity` extends `ReactActivity`, the app will crash on launch with `IllegalStateException: You need to use a Theme.AppCompat theme`.
