# TEST_INFRA: 「研迹」React Native Refactoring E2E Test Suite Specification

## 1. Test Philosophy & Architecture

### 1.1 Opaque-Box, Requirement-Driven Principles
The 「研迹」 E2E test suite adheres to strict **opaque-box testing**. Tests verify the system from the perspective of user requirements and external interface contracts, not internal implementation quirks:
1. **Authoritative Sources**: All test behaviors and assertions are derived directly from `ORIGINAL_REQUEST.md` and `PROJECT.md`.
2. **Native Business Authority**: The Kotlin native core remains the authoritative physical fact source (`SystemClock.elapsedRealtime`, Room database v20, foreground notification service). The test suite verifies that the React Native layer does not create duplicate timing logic or bypass data repositories.
3. **Zero Facade / Zero Cheating**: Facade tests that pass vacuously without exercising business logic are strictly forbidden. Every test asserts observable, concrete state transitions and data contracts.
4. **Hermetic & Independent Execution**: Every test sets up its own state, executes deterministically, and tears down cleanly without side-effects or execution order dependencies.

---

## 2. Feature Inventory Coverage Table (All 21 Features)

The test suite covers 100% of the 21 features defined in `PROJECT.md`:

| # | Feature | Milestone | Test File | Tier | Verification Contract |
|---|---------|-----------|-----------|------|------------------------|
| 1 | RN Add-to-App Gradle & Maven setup | M1 | `test-e2e/tier1-features/m1-infra-bundle.test.js` | Tier 1 | `libs.versions.toml`, `app/build.gradle.kts`, `settings.gradle.kts` AAR resolution |
| 2 | Offline Standalone JS Bundle | M1 | `test-e2e/tier1-features/m1-infra-bundle.test.js` | Tier 1 | `package.json` bundle script targeting `assets/index.android.bundle` |
| 3 | Material3 Theme Compatibility | M1 | `test-e2e/tier1-features/m1-infra-bundle.test.js` | Tier 1 | `themes.xml` inherits from `Theme.Material3.DayNight.NoActionBar` |
| 4 | React Native Host Activity & App | M1 | `test-e2e/tier1-features/m1-infra-bundle.test.js` | Tier 1 | `YanjiApplication` implements `ReactApplication`, `MainActivity` extends `ReactActivity` |
| 5 | Native Timer Module (`YanjiTimerModule`) | M2 | `test-e2e/tier1-features/m2-native-bridge.test.js` | Tier 1 | Session controls (`startFocus`, `pause`, `resume`, `complete`, `discard`) & tick events |
| 6 | Native Data Module (`YanjiDataModule`) | M2 | `test-e2e/tier1-features/m2-native-bridge.test.js` | Tier 1 | Room queries & mutations: tasks, notes, today stats, daily timeline, review stats |
| 7 | Native Theme Module (`YanjiThemeModule`) | M2 | `test-e2e/tier1-features/m2-native-bridge.test.js` | Tier 1 | `getThemePreference`, `setThemePreference`, theme events (SYSTEM, LIGHT, DARK) |
| 8 | TypeScript Bridge Interface Contracts | M2 | `test-e2e/tier1-features/m2-native-bridge.test.js` | Tier 1 | Strict schema validation against `BridgeSchemas` for all data transfer models |
| 9 | Design Tokens (Midnight Blue, Radius, Zero-border) | M3 | `test-e2e/tier1-features/m3-design-navigation.test.js` | Tier 1 | Dark mode Midnight Blue (`#0B132B`), forbidden `#000000`, card 0 border, `YanjiRadius` scale |
| 10 | Three-tab Navigation Shell | M3 | `test-e2e/tier1-features/m3-design-navigation.test.js` | Tier 1 | Exactly 3 destinations (`Today`, `Focus`, `Review`), zero medals/walls/mascots |
| 11 | Settings Screen & Navigation Flow | M3 | `test-e2e/tier1-features/m3-design-navigation.test.js` | Tier 1 | Settings access from Today top-right header, exam date & theme preferences |
| 12 | Today Screen Overview & Adaptive Single CTA | M4 | `test-e2e/tier1-features/m4-today-focus-moment.test.js` | Tier 1 | Single CTA resolution ("开始专注", "继续学习", "继续专注"), study hours/minutes formatting |
| 13 | Today Screen Task List & Quick Start | M4 | `test-e2e/tier1-features/m4-today-focus-moment.test.js` | Tier 1 | Task list rendering, toggle completion, quick start session bound to task |
| 14 | Focus Screen Monotonic Timer & Mode Memory | M4 | `test-e2e/tier1-features/m4-today-focus-moment.test.js` | Tier 1 | Large timer display, stopwatch/countdown toggle, mode preference memory, monotonic ticks |
| 15 | Cross-page "Record Moment" Instant Modal | M4 | `test-e2e/tier1-features/m4-today-focus-moment.test.js` | Tier 1 | Instant modal trigger, timestamp capture, active session ID binding, instant dismissal |
| 16 | Non-interrupting Record Moment during Focus | M4 | `test-e2e/tier1-features/m4-today-focus-moment.test.js` | Tier 1 | Record Moment during active timer does not pause, restart, or jitter monotonic clock |
| 17 | Review Screen Deterministic Daily Timeline | M5 | `test-e2e/tier1-features/m5-review-ai-e2e.test.js` | Tier 1 | Deterministic daily aggregation of focus sessions, completed tasks, and notes |
| 18 | Review Screen 7-Day Trend & Subject Breakdown | M5 | `test-e2e/tier1-features/m5-review-ai-e2e.test.js` | Tier 1 | 7-day focus duration trend and subject breakdown derived accurately from Room |
| 19 | Clean AI Capability Placeholder | M5 | `test-e2e/tier1-features/m5-review-ai-e2e.test.js` | Tier 1 | Graceful expansion slot reservation with ZERO fake dialogue or hallucinated mock data |
| 20 | E2E Test Suite Pass (Tiers 1-4) | Final | `test-e2e/tier1-features/m5-review-ai-e2e.test.js` | Tier 1 | 100% test suite execution pass rate across all tiers |
| 21 | Adversarial Coverage Hardening (Tier 5) | Final | `test-e2e/tier1-features/m5-review-ai-e2e.test.js` | Tier 1 | Malformed inputs, SQL injection strings, unicode stress, concurrency resistance |

---

## 3. Test Runner & Directory Layout

### 3.1 Invocation Command
```bash
node test-e2e/runner.js
```

### 3.2 Invocation Semantics
- **Runtime**: Node.js v22+ native ES Modules (`test-e2e/package.json` has `"type": "module"`).
- **Execution Mode**: Standalone and offline. Does not require Metro dev server, internet connection, or external cloud test runners.
- **Exit Code**: Returns `0` when all tests pass; returns `1` on any failure.
- **Reporting**: Emits tiered category headers, per-test pass/fail status with timings, and an aggregated summary matrix.

### 3.3 Directory Structure
```
test-e2e/
├── package.json                          # Sub-package declaration {"type": "module"}
├── runner.js                             # Executable runner entrypoint
├── framework/
│   ├── assertions.js                     # Rich assertion library with deep diffs
│   └── harness.js                        # Test suite registry, lifecycle hooks, and reporter
├── contracts/
│   ├── bridge-schema.js                  # Schema validators for all bridge models
│   ├── theme-tokens.js                   # Design system tokens and constraints checker
│   └── mock-bridge.js                    # In-memory oracle & driver for native bridge
├── tier1-features/
│   ├── m1-infra-bundle.test.js           # Features 1-4 (Infra, Bundle, Theme, Host Activity)
│   ├── m2-native-bridge.test.js          # Features 5-8 (Timer, Data, Theme modules, Contracts)
│   ├── m3-design-navigation.test.js      # Features 9-11 (Tokens, 3-tab Navigation, Settings)
│   ├── m4-today-focus-moment.test.js     # Features 12-16 (Today CTA, Tasks, Focus, Moments)
│   ├── m5-review-ai-e2e.test.js          # Features 17-21 (Review Timeline, Stats, AI slot, E2E)
│   ├── m8-shipped-tokens.test.js         # Shipped src/theme/tokens.ts red lines
│   └── m9-review-view-logic.test.js      # Shipped src/screens/reviewViewLogic.ts (date clamp, timeline merge)
├── tier2-boundaries/
│   └── boundaries.test.js                # Empty data, limits, date edges, unicode stress
├── tier3-interactions/
│   └── interactions.test.js              # Timer + Record Moment + Room sync, navigation continuity
└── tier4-workflows/
    └── workflows.test.js                 # Day in life of student, quick start, discard flow
```

---

## 4. Test Tiers Specification

### Tier 1: Feature Coverage (Isolated Happy-Path)
Validates all 21 features in isolation, verifying interface signatures, return payloads, event emitters, and configuration files.

### Tier 2: Boundary & Corner Cases
Stress tests edge conditions:
- **Empty Datasets**: Zero tasks, zero notes, zero study records on first launch. Verifies clean zero-state display without `NaN` or unhandled exceptions.
- **Timer Boundaries**: Countdown timer reaching `00:00:00` clamping remaining seconds to 0; repeated pause-resume cycling retaining accurate elapsed time.
- **Date & Countdown Boundaries**: Exam date today (0 days remaining), exam date in the past (clamped to 0), leap year (Feb 29) handling.
- **Text & Input Boundaries**: Empty or whitespace-only notes rejected; massive notes (10,000+ characters) and unicode symbols preserved without truncation or crash.
- **Theme Boundaries**: Rapid cycling through `SYSTEM` -> `LIGHT` -> `DARK` -> `SYSTEM`.

### Tier 3: Cross-Feature Interactions
Validates cross-module coordination and state consistency:
- **Focus Timer + Record Moment + Today Stats + Room Sync**: Opening Record Moment and saving notes while timer runs does not pause or reset the timer; session completion updates task actual study time, Today stats, and Room records synchronously.
- **Navigation Continuity**: Active focus session state is preserved across bottom tab transitions (`Focus` -> `Today` -> `Review` -> `Focus`); Today screen adaptive CTA dynamically reflects ongoing session status.
- **Task Completion Sync**: Toggling task completion immediately synchronizes with Today stats and Review timeline.
- **Theme Preference Retention**: Toggling theme mode while focus session is running updates visual styling without losing active timer state.

### Tier 4: Real-World Workflows (Application Scenarios)

| Scenario | Objective | Workflow Sequence | Verification Criteria |
|---|---|---|---|
| **Scenario 1**: 全天备考闭环 (Day in the Life of a Graduate Exam Candidate) | Simulates full morning-to-night study cycle through all tabs | 1. Morning (07:30): Check countdown, view 0m study, create 3 tasks.<br>2. Forenoon: 60m Math session + record moment during session + complete.<br>3. Afternoon: 45m English session + mark task completed.<br>4. Evening: Review tab (timeline, 7-day trend, subject distribution).<br>5. Night: Settings review. | • 105m total study time<br>• 1 completed task<br>• 1 moment note linked to session<br>• 7-day trend reflects 1.8h<br>• Subject breakdown: Math 60m, Eng 45m<br>• No UI jitter or data desync |
| **Scenario 2**: 无任务快速开始专注 (Zero-Configuration Quick Start) | Spontaneous focus session without prior task planning | 1. Open app with 0 tasks.<br>2. Single CTA says "开始专注".<br>3. Tap starts stopwatch session.<br>4. Study 25m and complete. | • Session completes cleanly<br>• Today stats reflect 25m<br>• Review timeline displays session with no task binding |
| **Scenario 3**: 异常中断与舍弃流 (Interrupted Session & Discard Flow) | Handles sudden interruption / abandoned session | 1. Start focus session.<br>2. After 3 minutes, interruption occurs.<br>3. User discards session. | • Active session cleared immediately<br>• 0 study minutes added to Today stats<br>• 0 phantom records in Review timeline |

---

## 5. Coverage Thresholds & Quality Gates

To ensure the highest engineering standard, the refactored project must satisfy all of the following quality gates:

1. **E2E Test Pass Rate**: **100%** (all 57 tests passing across Tiers 1-4).
2. **TypeScript Compilation**: `npm run typecheck` (`tsc --noEmit`) passes with **0 errors**.
3. **Android Native Compilation**: `./gradlew.bat assembleDebug` succeeds.
4. **Android Native Unit Tests**: `./gradlew.bat :app:testDebugUnitTest` passes (all 47 native test classes preserved).
5. **Card Zero-Border Rule**: 100% compliance across light and dark modes.
6. **No Pure Black (#000000)**: Dark mode backgrounds strictly use the Midnight Blue palette.
7. **Zero Fake Data**: Room database is the sole persistence source; no mock dialogue or hallucinated data.
