# Challenger 2 — Failure Modes & Lifecycle Review (Milestone 2-4)

**Reviewer:** `challenger_m25_2` (adversarial failure-mode / lifecycle reviewer)
**Target:** 「研迹」React Native migration — native bridge + design system + three core screens
**Baseline commit:** `4530352 feat: Milestone 2-4 — 原生桥接层、设计系统与三大核心页面`
**Method:** static code reading only (read-only exploration). **No typecheck, no Gradle build, no unit tests, no device push were executed by this reviewer.** Every finding below is derived from reading source; nothing is claimed as "verified by execution" (per `AGENTS.md` §三.10 无证据不宣称通过).
**Behavioural oracle:** `test-e2e/contracts/mock-bridge.js`

---

## 1. Observation

### 1.1 Scope reviewed

| Layer | Files |
| :--- | :--- |
| RN screens / components | `src/screens/FocusScreen.tsx` (312), `src/screens/TodayScreen.tsx` (240), `src/screens/RecordMomentModal.tsx` (162), `src/screens/SettingsScreen.tsx`, `src/screens/ReviewScreen.tsx` |
| RN bridge / theme | `src/bridge/index.ts` (256), `src/theme/ThemeProvider.tsx` (76) |
| Native bridge modules | `app/src/main/java/com/example/yanji/bridge/YanjiTimerModule.kt` (248), `YanjiDataModule.kt` (449), `YanjiThemeModule.kt` (93) |
| Native timer lifecycle | `data/timer/ActiveSessionCoordinator.kt` (477), `data/timer/TimerStore.kt` (404), `service/FocusTimerService.kt` (560) |
| Native data | `data/YanjiRepository.kt`, `data/note/NoteStore.kt`, `data/timer/TimerCalculator.kt`, `data/timer/TimerMachine.kt`, `data/Models.kt`, `data/db/Entities.kt` |
| Contracts / E2E | `test-e2e/contracts/mock-bridge.js`, `test-e2e/contracts/bridge-schema.js`, `test-e2e/framework/harness.js` |
| Wiring | `App.tsx`, `src/navigation/NavigationShell.tsx`, `src/navigation/BottomTabBar.tsx`, `bridge/YanjiPackage.kt`, `MainActivity.kt`, `YanjiApplication.kt`, `AndroidManifest.xml` |

### 1.2 Overall shape

The native timer lifecycle (monotonic-clock driven, `ActiveSessionCoordinator` + `FocusTimerService` + `TimerStore`) is genuinely well built and honours `AGENTS.md` §二.7 计时韧性: all physics come off `SystemClock.elapsedRealtime`-based sources, persistence happens at the coordinator's completion/cancel exits, and the RN screen is a pure renderer of pushed state. Most of my lifecycle attacks on that path came back **SAFE**.

The defects cluster almost entirely at **two seams**:

1. **The Kotlin→JS bridge contract seam** (`YanjiDataModule` promise settlement + event vocabulary).
2. **The RN unmount/state seam** (tab switching unmounts screens; per-modal draft state and silent error paths).

---

## 2. Logic Chain

### 2.1 P0 — `YanjiDataModule.collectFirst` never returns, and crashes the process

`app/src/main/java/com/example/yanji/bridge/YanjiDataModule.kt:57-65`:

```kotlin
private suspend fun <T> collectFirst(flow: Flow<T>): T {
    var result: T? = null
    flow.collect { value ->
        result = value
        throw FirstEmissionException()   // <-- used to abort collection after first emission
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
```

`FirstEmissionException` is thrown **from inside `flow.collect { }`** and is **never caught**. The throw propagates out of `collectFirst`, out of the enclosing `scope.launch { ... }`, and cancels the coroutine with that exception.

`scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` (`YanjiDataModule.kt:42`) has **no `CoroutineExceptionHandler`**. I grepped every `.kt` under `app/src/main/java` for `CoroutineExceptionHandler`, `uncaughtException`, and `handleCoroutineException` — **zero hits**. Therefore:

- The code **after** the `collectFirst(...)` call in every affected method is **never reached** — including `promise.resolve(...)`. The JS promise **never settles** (neither resolved nor rejected): the screen shows a permanent loading state.
- The uncaught exception escapes to the default handler, which on Android takes down the process.

Affected call sites (all three are core-screen entry points):

| Method | Lines | Consumer |
| :--- | :--- | :--- |
| `getTodayStats` | `YanjiDataModule.kt:74` | `TodayScreen` — today focus total / goal progress |
| `getTodayTasks` | `YanjiDataModule.kt:101` | `TodayScreen` — pending task list |
| `getDailyTimeline` | `YanjiDataModule.kt:304` | `ReviewScreen` — daily timeline + 7-day stats |

Net effect: **Today and Review are non-functional, and the app crashes on their first data fetch.** This is release-blocking.

The codebase already contains the correct idiom, so the fix is mechanical rather than architectural: `BackupTransfer.kt:26-36` uses `flow.first()`. Alternatively wrap the body in `try { ... } catch (e: FirstEmissionException) { /* expected */ }` or `runCatching`. Additionally, a `CoroutineExceptionHandler` on the module `scope` is a cheap second line of defence so no future coroutine exception can crash the process.

### 2.2 P1 — Record Moment draft is destroyed by a tab switch

`App.tsx:40-42` renders only the active tab:

```tsx
{activeTab === 'today' ? <TodayScreen /> : null}
{activeTab === 'focus' ? <FocusScreen /> : null}
...
```

`RecordMomentModal` holds its own `text` / `saving` / `error` state (`RecordMomentModal.tsx:42-44`) and is mounted **inside** `TodayScreen` (and separately inside `FocusScreen`, at lines 245 and 304). So:

1. User is on Today, opens Record Moment, types a paragraph.
2. The modal is `transparent` (`RecordMomentModal.tsx:82`) and `BottomTabBar` is a **sibling** rendered outside the tab content (`App.tsx:44`) — i.e. the tab bar is visible and **tappable behind the sheet**.
3. User taps "专注" → `TodayScreen` unmounts → the modal and its `text` state are destroyed.
4. User taps back to Today → a fresh modal with empty text. **The typed content is silently gone**, no confirmation, no draft persistence.

Because `FocusScreen` mounts its *own* `RecordMomentModal` instance, the draft does not survive the detour either. Note the tab-bar-reachable-while-modal-open property is the root cause; even hoisting the draft into a screen-level state would not fix it unless the state lives above the tab switch (a shared provider or the app shell).

### 2.3 P1 — Sub-60-second sessions are dropped with zero user feedback

`data/timer/TimerStore.kt:51-52`:

```kotlin
private const val MIN_RECORDED_FOCUS_SECONDS = 60L
```

`TimerStore.persistCompletedFocus` (`TimerStore.kt:292-295`) simply sets `_activeFocus.value = null` and returns when the session is shorter than 60s — **no Room row is written, and no event is emitted to JS**. The comment at `TimerStore.kt:44,48-49` records that the rule was deliberately moved to the persistence exit only ("UI 不再预判"). The old Kotlin UI layer had a "不足 1 分钟不予保存" hint; the rebuilt RN layer has **neither the pre-check nor the post-hoc feedback**.

Consequence chain:

1. User starts focus, completes after 20s.
2. `onTimerStateChanged` fires `COMPLETING` → `session: null` → `FocusScreen` returns to the preparation state (`FocusScreen.tsx:152`).
3. User goes to Today. `getTodayStats` returns `totalFocusSeconds = 0`.
4. `formatDuration(0)` → **"还没开始"** (`TodayScreen.tsx:47`).

The user believes they studied and the app asserts they never started. This is a misleading-UI defect, and it also sits against `AGENTS.md` §三.3 (零假数据) in spirit — the UI silently presents a false state.

### 2.4 P2 — Concurrent start failure is swallowed with no user feedback

`YanjiTimerModule.kt:101-104` correctly rejects with `E_SESSION_ACTIVE` (matching `mock-bridge.js:62-64` `/already active/`; `TimerStore.kt:184` `if (!accepted) return null`). The native side honours the contract.

The JS side does not. `FocusScreen.handleStart` (`FocusScreen.tsx:114-118`):

```tsx
try {
  const s = await YanjiTimerNative.startFocus({...});
  setSession(s);
} catch { /* swallow */ }
finally { setBusy(false); }
```

The bare `catch {}` discards the rejection. Because `startFocus` rejected, the following `refreshSession()` is skipped, so nothing is shown; the button just un-busies and the tap appears to do nothing. The state is *not* left stale (good), but there is zero explanation for a real, reachable failure.

### 2.5 P2 — `settingsMap` fabricates duration values

`YanjiDataModule.kt:435-443` hardcodes:

```kotlin
"focusDurationMinutes" to 45,
"breakDurationMinutes" to 10,
```

`SettingsScreen.tsx:131` renders this as if it were the user's stored preference. No real setting is read (and, per my reading of `Models.kt` / `Entities.kt`, no such persisted field exists for durations). This is fabricated data surfaced to the user, which violates `AGENTS.md` §三.3. Either expose a real source of truth or remove the row.

### 2.6 P3 — Three mutually inconsistent `phase` vocabularies

| Source | Values |
| :--- | :--- |
| `src/bridge/index.ts:16` `TimerPhase` | `'IDLE' \| 'RUNNING' \| 'PAUSED' \| 'COMPLETED' \| 'CANCELLED'` |
| `YanjiTimerModule.sessionPhase()` (`:225-229`) | `"COUNTDOWN"` / `"STOPWATCH"` |
| `test-e2e/contracts/bridge-schema.js:18` | `FOCUS \| BREAK \| IDLE` |

No RN consumer currently reads `phase` (`FocusScreen` uses only `isCountdown` / `isPaused`), so there is no live crash — but the type contract is wrong and will mislead the next implementer.

### 2.7 P3 — Native-module resolution throws at import time

`src/bridge/index.ts:208-218` calls `requireNativeModule` in top-level `const` initializers. Any JS-only context (Jest without a native mock, a web preview) that imports anything from `../bridge` — which is every screen plus `ThemeProvider` — crashes the bundle at load with `LINKING_ERROR` rather than degrading. Note `ThemeProvider.tsx:39-42` contains a `catch` with the comment "Native module unavailable (web/tests)" that is **dead code**, because the throw happens at import time before any `.catch` can run.

---

## 3. Caveats

1. **No execution evidence.** I did not run `./gradlew.bat assembleDebug`, typecheck, unit tests, or any device push. §2.1 (the `collectFirst` defect) is a static reading of the Kotlin control flow; it is unambiguous on its face (an uncaught `throw` inside `flow.collect` with no handler anywhere in the module), but it has not been confirmed by a runtime reproduction.
2. **§2.1 impact is inferred, not measured.** I assert the app crashes and the promise never settles based on the absence of any `CoroutineExceptionHandler` in the codebase. RN's actual default-uncaught-handler behaviour on Android may vary by RN version; the *promise-never-settles* half of the finding is certain regardless.
3. **The E2E suite does not currently exercise these paths.** `test-e2e` deliberately imports only `contracts/mock-bridge.js`, never `src/`, so neither the import-time throw nor `collectFirst` is covered by the existing suite. My findings therefore do not represent "failing tests" — they represent untested defects.
4. **Tab-bar reachability assumption.** In §2.2 I assume the transparent modal does not block touches to `BottomTabBar`. I verified structurally that `BottomTabBar` is a sibling of the tab content (`App.tsx:44`) and that the modal is `transparent`, but I did not run the app to confirm the hit-testing behaviour on device. If the modal does in fact swallow touches, the severity of §2.2 drops from DEFECT to theoretical.
5. **Sub-60s rule is intentional product behaviour** (`TimerStore.kt:44` comment). My objection is not to the rule but to the *silence*; a deliberate rule with no user-visible explanation is still a UX defect.
6. **Nothing in the repo was modified by this reviewer.** All exploration was read-only. No source file, no test, no config was changed; no `git add` / `commit` / `push` was executed.

---

## 4. Conclusion

# REQUEST_CHANGES

The native timer lifecycle core is sound and honours the计时韧性 red line. However, one release-blocking bridge defect (`collectFirst`) renders Today and Review non-functional and can crash the process, and two P1 defects (lost Record Moment draft, silent sub-60s drop) directly degrade the three core screens this milestone is supposed to deliver. These must be fixed before this milestone can be considered complete.

---

## 5. Attack Results Table

| # | Attack | Outcome | Evidence | Severity |
| :-- | :--- | :--- | :--- | :--- |
| 1 | Concurrent start while a session is active | **DEFECT (UX)** — contract honoured natively, rejection swallowed in JS | `YanjiTimerModule.kt:101-104` (correct `E_SESSION_ACTIVE`), `TimerStore.kt:184`, `mock-bridge.js:62-64`; JS gap at `FocusScreen.tsx:114-118` | Medium |
| 2 | 结束 vs 放弃 swapped in the UI | **SAFE** | `FocusScreen.tsx:288` 结束 → `handleComplete`; `:291` 放弃 → `handleDiscard`; `FocusTimerService.kt:341,454`; `ActiveSessionCoordinator.kt` complete/cancel | — |
| 3 | Session shorter than 60 s | **DEFECT** — silently dropped, UI then claims "还没开始" | `TimerStore.kt:51-52,292-295`; `TodayScreen.tsx:47` | Medium |
| 4 | Countdown reaching zero | **SAFE** — correct COMPLETING → null transition, completion alert posted for countdown | `FocusTimerService.kt:263-268,316`; `YanjiTimerModule.kt:147-153,200-202`; `FocusScreen.tsx:152` | — |
| 5 | Process death / restore | **SAFE (with caveat)** — paused restore is correct; `phase` vocabulary is inconsistent | `ActiveSessionCoordinator.kt:428-452`; `YanjiTimerModule.kt:62-72,221,225-229`; `FocusScreen.tsx:276,281`; vs `src/bridge/index.ts:16`, `bridge-schema.js:18` | Low (caveat) |
| 6 | Pause/resume tick guard emitting duplicate or stale ticks | **SAFE** — guard suppresses only repeated ticks while paused; first post-resume tick is emitted | `YanjiTimerModule.kt:155-166`; `FocusTimerService.kt:289-291,307-308` | — |
| 7 | Save a Record Moment while the timer runs | **SAFE** — no pause side-effect; JS resolves `sessionId` explicitly | `RecordMomentModal.tsx:46-74`; `YanjiDataModule.kt:165-184`; `mock-bridge.js:239-254` | — |
| 8 | Save failure preserves typed input | **SAFE** — text kept, error shown, retry possible, double-submit guarded | `RecordMomentModal.tsx:46-74,142` | — |
| 9 | Empty input | **SAFE** — rejected on both sides, modal stays open, text preserved | `RecordMomentModal.tsx:48-52`; `YanjiDataModule.kt:167-170`; `mock-bridge.js:240-242` | — |
| 10 | Write-then-resolve race on note save | **RISK** — fire-and-forget DAO write resolves before commit; self-heals via `onDataChanged` | `NoteStore.kt:49-60`; `YanjiDataModule.kt:171-183` | Medium |
| 11 | Keyboard layout jump when the Record Moment sheet opens | **SAFE** — `adjustResize` + `behavior=undefined` on Android is the correct pairing | `AndroidManifest.xml:36`; `RecordMomentModal.tsx:82-92` | — |
| 12 | Theme write failure path | **SAFE (no reachable path)** — `apiKey == cachedAiApiKey` always, so `E_SETTINGS_SAVE_FAILED` is unreachable for a theme-only change; no credential exposure | `YanjiThemeModule.kt:50-65`; `YanjiRepository.kt:298,528-545`; `YanjiRepository.kt:55` | — |
| 13 | Settings clobbering on partial update | **SAFE** for field preservation (`current.copy(...)`), **DEFECT** for fabricated durations | `YanjiDataModule.kt:335-345,435-443`; `SettingsScreen.tsx:131` | Low–Medium |
| 14 | Unmount / tab switch while the Record Moment modal is open | **DEFECT** — typed draft silently lost, tab bar tappable behind the transparent sheet | `App.tsx:40-44`; `RecordMomentModal.tsx:42-44,82` | Medium |
| 15 | Stale data / duplicate listeners after returning to a tab | **SAFE** — screens unmount on switch, listeners cleaned up, no leak | `TodayScreen.tsx:84-96`; `FocusScreen.tsx:77-87`; `ReviewScreen.tsx:69-76`; `ThemeProvider.tsx:31-52` | — |
| 16 | Native module absent in a JS-only context | **DEFECT (by design)** — `requireNativeModule` throws at import time; dead "web/tests" catch in `ThemeProvider` | `src/bridge/index.ts:208-218`; `ThemeProvider.tsx:39-42` | Medium |
| 17 | Promise rejection without a code | **SAFE** — all 6 reject sites carry a code | `YanjiTimerModule.kt:102`; `YanjiDataModule.kt:119,168`; `YanjiThemeModule.kt:53,60` | — |
| 18 | `NativeEventEmitter` constructed with a null module | **SAFE** on Android — the invariant only fires on iOS | `src/bridge/index.ts:220-221`; `node_modules/react-native/Libraries/EventEmitter/NativeEventEmitter.js:73-102,117-118` | — |
| **19** | **Promise settlement in `YanjiDataModule`** | **DEFECT (release-blocking)** — uncaught `throw` inside `flow.collect`; `promise.resolve` never reached, promise never settles; no exception handler anywhere | `YanjiDataModule.kt:42,57-65,74,101,304`; correct idiom at `BackupTransfer.kt:26-36`; zero `CoroutineExceptionHandler` hits repo-wide | **Critical** |
| 20 | Task-bound focus session (继续学习 → taskId) | **RISK / design gap** — `taskId` never passed, so per-task minutes are not wired in RN | `TodayScreen.tsx` primary action; `FocusScreen.tsx:109-111` | Low |
| 21 | Focus duration presets ignore stored preference | **RISK / design gap** — hardcoded `[25,45,60,90]` | `FocusScreen.tsx:44` | Low |
| 22 | `finishWithFailure` (Room write failure) in RN | **RISK** — native keeps the session ACTIVE with a retry notification, RN has no retry affordance | `FocusTimerService.kt:424-430` | Low |
| 23 | `getSubjects` before `SubjectCatalog` is populated | **SAFE in practice** — `SubjectCatalog.all` falls back to defaults | `YanjiDataModule.kt:237`; `YanjiRepository.kt:303-306`; `Models.kt:176-179` | Low |
| 24 | Theme event noise | **SAFE** — `onThemeChanged` re-emits on every settings change, idempotent | `YanjiThemeModule.kt:30-36` | — |
| 25 | System dark/light mode touched anywhere? | **SAFE** — `YanjiThemeModule` only writes `user_settings.themeMode`; no `UiModeManager` / `settings put` / `cmd uimode` in the migration | `YanjiThemeModule.kt:20-22,58` | — |
| 26 | Credential leakage via any reviewed path | **SAFE** — no AI key written to Room/Preferences/logs by the reviewed bridge methods | `YanjiDataModule.kt:335-345`; `YanjiRepository.kt:528-545` | — |
| 27 | Timer physics on a non-monotonic clock | **SAFE** — all elapsed values derive from monotonic sources; no `notify()`-per-second | `FocusTimerService.kt`; `ActiveSessionCoordinator.kt` | — |
| 28 | Ticker flow stops while the coordinator survives | **RISK (self-healing)** — display freezes until the next state change, which recomputes from the monotonic clock | `YanjiTimerModule.kt:155-166`; `FocusScreen` `displaySeconds` | Low |

---

## 6. Prioritized Required Changes

### P0 — Must fix (release-blocking)

1. **`app/src/main/java/com/example/yanji/bridge/YanjiDataModule.kt:57-65`** — replace the throw-based `collectFirst` with `flow.first()` (the idiom already proven in `BackupTransfer.kt:26-36`), or wrap the abort in `try/catch (e: FirstEmissionException)` / `runCatching`. Without this, `getTodayStats` (`:74`), `getTodayTasks` (`:101`) and `getDailyTimeline` (`:304`) never settle their promises and Today/Review are non-functional.
2. **`YanjiDataModule.kt:42`** — attach a `CoroutineExceptionHandler` to the module `scope` (and consider the same for `YanjiTimerModule`) so no future uncaught coroutine exception can take down the process.

### P1 — Must fix (core-screen defects)

3. **`src/screens/RecordMomentModal.tsx` + `App.tsx`** — make the typed draft survive a tab switch. Either hoist the draft state above the tab switch (a shared record-moment provider / app-shell state), or prevent tab interaction while the sheet is open. Today the `BottomTabBar` (`App.tsx:44`) is reachable behind the transparent sheet (`RecordMomentModal.tsx:82`), so content is silently destroyed.
4. **Sub-60s completion feedback** — surface the "不足 1 分钟未保存" outcome to JS (e.g. return a result enum from `completeTimer`, or have `TimerStore.persistCompletedFocus` (`TimerStore.kt:292-295`) emit an event) so `FocusScreen` can show a one-line explanation. Today the user is told "还没开始" (`TodayScreen.tsx:47`) after genuinely completing a session.

### P2 — Should fix

5. **`src/screens/FocusScreen.tsx:114-118`** — surface the `E_SESSION_ACTIVE` rejection as a quiet inline hint instead of a bare `catch {}`.
6. **`YanjiDataModule.kt:440-441`** — stop hardcoding `focusDurationMinutes = 45` / `breakDurationMinutes = 10`; expose real values or delete the row from `SettingsScreen.tsx:131`. This is fabricated data shown to the user (§三.3).
7. **`phase` vocabulary** — reconcile `src/bridge/index.ts:16`, `YanjiTimerModule.sessionPhase()` (`:225-229`) and `test-e2e/contracts/bridge-schema.js:18` into one contract.

### P3 — Nice to have

8. **`src/bridge/index.ts:208-218`** — make native-module resolution lazy/defensive so a missing module degrades instead of throwing at import time; remove or fix the now-dead "Native module unavailable (web/tests)" catch in `ThemeProvider.tsx:39-42`.
9. **`FocusScreen.tsx:109-111`** — pass the selected task's `taskId` on start so the 继续学习 primary action actually binds the session to the task.
10. **`FocusTimerService.kt:424-430` `finishWithFailure`** — give the RN layer a retry affordance when the Room write fails, instead of leaving a frozen timer with only a native notification action.

---

*End of report. Read-only review; no repository state was modified.*
