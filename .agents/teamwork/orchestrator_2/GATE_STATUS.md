# Gate Status: Retrospective Review of M2–M5

## Dispatch — 2026-10-08T23:26:54Z
Model directive: `space bunny`

| Agent | Role | Focus | Verdict |
|-------|------|-------|---------|
| reviewer_m25_1 | reviewer | Native bridge correctness & authority boundary | **REQUEST_CHANGES** (R1–R5) |
| reviewer_m25_2 | reviewer | Design system & constraint compliance | **APPROVE** (7 non-blocking) |
| challenger_m25_1 | challenger | Adversarial requirement audit vs original brief | **REQUEST_CHANGES** (P0-1…P0-5) |
| challenger_m25_2 | challenger | Failure-mode & lifecycle attack | **REQUEST_CHANGES** (P0–P3) |
| auditor_m25_1 | auditor | Forensic integrity & red-line compliance | **CLEAN** (3 WARN) |

## Commit Under Review
- `4530352` — feat: Milestone 2-4 — 原生桥接层、设计系统与三大核心页面
- `9d6879d` — feat: Milestone 1 — React Native 0.87.1 Add-to-App 基建工程与 E2E 测试框架
- `2fbdbcc` — fix: 修复 values-night/themes.xml 深色模式冷启动崩溃并补充 E2E 守卫

## Verification Baseline (pre-review, single agent)
- `npm run typecheck` → 0 errors
- `.\gradlew.bat :app:compileDebugKotlin` → BUILD SUCCESSFUL (deprecation warnings only)
- `.\gradlew.bat :app:testDebugUnitTest` → BUILD SUCCESSFUL
- `node test-e2e/runner.js` → 58/58 passed
- `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL

## Why this review exists
The teamwork orchestrator hit an API 429 rate limit after M1's gate review and was
never resumed. M2–M5 were implemented by a single agent with no adversarial review.
The M1 gate had already proven its value by catching a critical dark-mode
cold-launch crash (`values-night/themes.xml` missing AppCompat inheritance) that
the implementing agent missed. This retrospective gate gives M2–M5 the same
scrutiny.

## Gate Result
**PENDING** — awaiting all five verdicts (2 received: reviewer_m25_1, auditor_m25_1).

## Orchestrator Notes (source facts verified while waiting, no files modified)

### R1 confirmed — `YanjiTimerModule.kt:225-229` + `215`
`sessionPhase()` emits `PAUSED`/`COUNTDOWN`/`STOPWATCH`, and `mode` passes the raw
Chinese `FocusModes` name straight through (`"正向计时"`). Contract requires
`phase ∈ {FOCUS,BREAK,IDLE}` and `mode ∈ {COUNTDOWN,STOPWATCH}`.
The module already declares unused `PHASE_FOCUS`/`PHASE_BREAK`/`PHASE_IDLE` constants
(`:243-245`). Fix: derive `mode` from `targetDurationSeconds > 0`, `phase` from
`session.paused` + `kind`. `src/bridge/index.ts:16` declares a third vocabulary
(`'IDLE'|'RUNNING'|'PAUSED'|'COMPLETED'|'CANCELLED'`) — must be aligned too.
Runtime impact is currently nil (`FocusScreen.tsx` only reads `isCountdown` /
`isPaused`), so the defect is purely a contract-conformance failure.

### R2 confirmed — `YanjiDataModule.kt:420`
`putInt("actualMinutes", 0)`. Real source exists:
`YanjiRepository.observeTaskActualSeconds(): Flow<Map<String, Long>>` (line 431),
backed by `FocusSessionDao.observeTaskActualSeconds()`.

### R3 confirmed — `YanjiDataModule.kt:257`
`getReviewStats` uses `getWeeklyStudySummary(0)` (calendar Mon–Sun) and ignores `days`.
Rolling window helper already exists: `YanjiTime.lastDaysRange(days)` (line 92) and
`StudyStatisticsRepository.getStudyDurationFlow(EpochRange)` (line 348).
**Contract side-effect to decide:** `bridge-schema.js` `isValidReviewStats` and
`PROJECT.md` do NOT list `dailyAverageMinutes` / `activeDays`, yet both Kotlin
(`:274-275`) and TS (`ReviewStats`, `src/bridge/index.ts:70-71`) emit them.
These are contract-overflow fields and must be adjudicated alongside R3.

### R4 confirmed — `YanjiDataModule.kt:180` + `:432`
`repository.addOrUpdateNote(entry)` → `NoteStore.addOrUpdate` (`NoteStore.kt:49`) is
**non-suspend** and launches its own coroutine, so `promise.resolve` returns before
the Room write lands. `noteMap` emits `sessionId: ""` instead of `null`.
`NoteEntryDao.insert(entry)` IS suspend (`Daos.kt:177`) — a suspend write path is
available without touching `NoteStore`'s in-memory mirror.

### WARN 2 confirmed — `YanjiDataModule.kt:440-441`
`settingsMap()` hardcodes `focusDurationMinutes = 45` / `breakDurationMinutes = 10`.
`UserSettings` (`Models.kt:333-355`) has **no** such fields — no focus/break duration
exists in the domain model at all. `SettingsScreen.tsx:131` therefore renders a
fabricated "45 分钟" as if it were a real user setting.

### R5 confirmed — zero bridge test coverage
`app/src/test/**` contains 49 test files, none under a `bridge/` package. This is the
root cause that let R1 and R3 survive a green E2E suite: `test-e2e/` drives
`MockYanjiBridge`, which is contract-conformant by construction and never executes
the Kotlin modules.

---

## reviewer_m25_2 verdict — APPROVE (verified independently)

All hard red lines hold: dark bg `#0D111A` == `YanjiDarkBackground` exactly, no
`#000000` in the RN dark palette, `YanjiCard` has zero border across all 5 screens,
all 13 radius sites use `YanjiRadius.*`, `TABS` is exactly 3, Settings is a Today-header
overlay, no forbidden elements, no dead AI entry, and `YanjiThemeModule` writes only
`user_settings.themeMode`.

Two of the 7 follow-ups are **not** cosmetic — orchestrator adjudication:

1. **Missing `onAccent` token → real contrast defect (highest user impact).**
   Verified: `src/theme/tokens.ts` declares **no** on-accent foreground at all, while
   4 call sites hardcode `#FFFFFF` on the dark accent `#4F7DF3`. Compose solves this
   deliberately with `YanjiDarkOnPrimary = Color(0xFF0D111A)` and documents why
   (`Color.kt:205-212`: white on the raised dark primary measures only 2.56–3.78:1
   across the five mascot themes). The RN side reintroduced the exact failure the
   Compose comment forbids. Fix: add an `onAccent` token per mode (dark = `#0D111A`).
   Also note light-mode `YanjiOnPrimary = #FFFFFF` is correct and must stay.

2. **Semantic colors regressed to retired values.**
   Verified: `tokens.ts` light `success/warning/danger` = `#2F9E6D`/`#E67E22`/`#D64545`,
   but `Color.kt:105-111` explicitly retired these and documents the measured failures
   (success 3.37:1, warning 2.64:1, danger 4.15:1) in favour of `#17784F`/`#9A6100`/`#BE3232`.
   The RN side adopted the values Compose had already rejected on WCAG grounds.

3. **`theme-tokens.js` contract is decorative and stale** — it asserts `#0B132B`,
   `#48CAE4`, `#0F172A`, `#0284C7`, none of which exist in the implementation, and its
   radius scale omits `28` (which `Radius.kt:22` `HeroCardRadius` and
   `RecordMomentModal.tsx` both use). No test reads it, so nothing catches the drift.
   Fix the contract to mirror the implementation, and add the `28` step.

4. **NativeWind is inert** — `className` used 0 times across 114 `style=` objects;
   `tailwind.config.js` duplicates `tokens.ts` unreferenced. Decide the role explicitly.

5. **58/58 E2E proves nothing about the RN tree** — no test in `test-e2e/` reads any
   file under `src/`. This corroborates R5 from the design side.

---

## challenger_m25_1 verdict — REQUEST_CHANGES (verified independently)

Tally: 20 MET · 6 PARTIAL · 7 MISSING · 1 VIOLATION · 2 acceptable deferrals.

### P0-1 · Task → focus loop cut in half (CONFIRMED)
`TodayScreen.tsx:179-201`: the only `onPress` in a task row is the checkbox.
All three primary-CTA branches (`:101`, `:105`, `:107`) call only `selectTab('focus')`
— none carries the task. `FocusScreen.tsx:110` always passes `taskId: null`.
`startFocus(..., taskId)` already accepts it at the bridge, so this is a UI wiring gap,
not a native limitation. Consequence: `actualMinutes` can never move off zero.

### P0-2 · Task create/edit/delete absent (CONFIRMED)
`git grep createTask|deleteTask -- src` returns only the two interface declarations in
`src/bridge/index.ts:173,181` — **zero call sites**. A first-run user cannot create a
task at all, which contradicts §03 "提供轻量的添加、编辑和删除操作".

### P0-3 · Hardcoded fake setting surfaced as real (CONFIRMED — same as WARN 2)
`YanjiDataModule.kt:440-441` + `SettingsScreen.tsx:120-133` render "单次专注时长 45 分钟"
as a persisted preference. `UserSettings` (`Models.kt:333-355`) has neither field.

### P0-4 · Quick-note session binding is structurally impossible (CONFIRMED — NEW)
`NoteEntryEntity` (`Entities.kt`) has **no `sessionId` column**. The `sessionId` column
that does exist at `Entities.kt:253-265` belongs to `ChatMessageEntity`, not notes.
`saveQuickNote` injects `sessionId` into the *response only* (`noteMap(entry, sessionId)`,
`YanjiDataModule.kt:182`); every read path calls `noteMap(it)` with the default `null`
and emits `""`. So the "自动关联正在进行的专注" clause holds only inside the save callback
and is invisible to every subsequent read. This is deeper than R4's race: even a correct
suspend write would still lose the binding, because there is nowhere to persist it.
**Requires a schema decision** — either add the column with a hand-written migration
(AGENTS.md §三.5) or drop `sessionId` from the note contract entirely. Do NOT paper over it.

### P0-5 · Tab switching destroys in-progress input (CONFIRMED)
`App.tsx:40-42` conditionally renders screens, so inactive tabs unmount. The
`RecordMomentModal` draft lives in local state inside the screen, so switching tabs
mid-draft loses it. The same unmount resets Focus's subject/duration and Review's date.
`NavigationShell.tsx:9-11` and `App.tsx:9-10` document the opposite of the code.

### P1 items noted for the fix batch
- Stopwatch unreachable: `FocusScreen` only offers minute presets → `isCountdown`
  always true → `TimerMode = 'STOPWATCH'` is dead code in RN.
- No `BackHandler` anywhere in `src/`, none in `MainActivity.kt`.
- Review missing 日均专注时长 (available at `YanjiDataModule.kt:274`, typed, never rendered)
  and task-completion stats.
- E2E suite integrity: `theme-tokens.js` palette does not match shipped `tokens.ts`;
  `aiSlotConfig` / `e2eMetrics` / `navigationFlow` are self-defined facade tests.

---

## challenger_m25_2 verdict — REQUEST_CHANGES (all 5 verdicts now received)

28-row attack table; verified SAFE: discard/complete not swapped, countdown-to-zero
transition, pause/resume tick guard, process-death restore, note save during a running
timer, save-failure input preservation, empty input, keyboard layout, theme-write
failure path unreachable, settings clobbering, listener cleanup, all 6 `promise.reject`
sites carry a code, `NativeEventEmitter` null-module safe on Android, §二.7 and §二.6 both hold.

### ⚠️ CONFLICT ADJUDICATION — `collectFirst` (reviewer_m25_1 says safe, challenger_m25_2 says P0)

**challenger_m25_2 is correct. reviewer_m25_1 is wrong on this specific point.**

Orchestrator evidence:

1. `collectFirst` (`YanjiDataModule.kt:57-65`) has **no `try`/`catch`**. The
   `throw FirstEmissionException()` inside the collector propagates out of
   `flow.collect {}` and out of `collectFirst` itself — so `return result as T`
   (line 64) is **unreachable**. An uncaught exception in a `suspend` function means
   the function never returns a value; "result is already assigned" does not help,
   because the assignment is lost when the stack unwinds.
2. The three call sites (`getTodayStats:74`, `getTodayTasks:101`,
   `getDailyTimeline:304`) all have `promise.resolve(...)` **after** the
   `collectFirst` call and no `catch`. So the JS promise **never settles**.
3. `scope` (`:42`) is `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`
   with **no `CoroutineExceptionHandler`**. Verified: `git grep -rn
   CoroutineExceptionHandler -- app/src/main/java` → **0 matches** across the entire
   main source tree. The exception therefore reaches the default handler.
4. `repository.observeStudyTasks(date)` is a Room-backed `Flow` that **always emits
   at least `emptyList()`**, so the throwing path is not a corner case — it fires on
   **every** call. Today and Review are non-functional, and the process can crash.

reviewer_m25_1's bytecode analysis was about *where* the exception surfaces (correct:
it does propagate, there is no `AbortFlowException` interception), but it then drew
the wrong conclusion — it treated the already-assigned `result` as recoverable.

The repo's own idiom is `flow.first()`, used consistently in
`BackupTransfer.kt:26-36` (`db.xxxDao().getAll().first()`). R4's fix should replace
`collectFirst` with `flow.first()` rather than patch the throw.

**Executable proof (orchestrator):** `app/src/test/java/com/example/yanji/bridge/
CollectFirstSemanticsTest.kt` — 4/4 tests executed and passed via
`.\gradlew.bat :app:testDebugUnitTest --tests "...CollectFirstSemanticsTest"`
(`tests=4 failures=0 errors=0 skipped=0`). `callSiteWithoutCatchNeverReachesResolve`
asserts the `promise.resolve` line is unreachable, which is the exact mechanism that
hangs the JS promise. This file is also the first of the R5 bridge tests.

### P0/P1/P2 from challenger_m25_2
- **P0**: `collectFirst` as above → promise never settles; affects Today + Review.
- **P1**: Record Moment draft destroyed by tab switch (same root cause as challenger_m25_1 P0-5);
  transparent sheet leaves `BottomTabBar` tappable behind it.
- **P1**: sub-60s sessions (`TimerStore.kt:51-52,292-295`) dropped with no event to JS,
  yet `TodayScreen.tsx:47` then says "还没开始" — misleading UI (§三.3).
- **P2**: `FocusScreen.tsx:114-118` bare `catch {}` swallows `E_SESSION_ACTIVE` → silent failure.
- **P2**: hardcoded 45/10 settings (same as WARN 2 / P0-3).
- **P2**: three inconsistent `phase` vocabularies (same as R1).

---

# Gate Result: **REQUEST_CHANGES**

4 of 5 agents returned REQUEST_CHANGES (`reviewer_m25_1`, `challenger_m25_1`,
`challenger_m25_2`); `reviewer_m25_2` returned APPROVE; `auditor_m25_1` returned CLEAN
with 3 warnings. The code is **not releasable**.

The M1 precedent held: this gate caught defects that `assembleDebug` green +
`testDebugUnitTest` 338 green + E2E 58/58 all passed straight over. The single most
severe (`collectFirst`) would have shipped Today and Review as permanently
non-functional screens.

## Consolidated fix list (single batch, priority order)

| # | Source | Severity | Fix |
|---|--------|----------|-----|
| F1 | challenger_m25_2 P0 | **BLOCKER** | Replace `collectFirst` with `flow.first()` (repo idiom, `BackupTransfer.kt:26-36`); delete `FirstEmissionException`. Restores Today + Review. |
| F2 | reviewer_m25_1 R1 / challenger_m25_2 P2 | BLOCKER | `sessionPhase()` → `phase ∈ {FOCUS,BREAK,IDLE}` via the already-declared `PHASE_*` constants; `mode` → `COUNTDOWN`/`STOPWATCH` from `targetDurationSeconds > 0`; align `src/bridge/index.ts:16` to one vocabulary. |
| F3 | challenger_m25_1 P0-4 | BLOCKER | **Needs user decision** — `NoteEntryEntity` has no `sessionId` column. Option A: add column + hand-written `MIGRATION_20_21` + `app/schemas/**`. Option B: drop `sessionId` from the note contract. Do not paper over. |
| F4 | reviewer_m25_1 R2 | HIGH | `taskMap` must emit real `actualMinutes` from `observeTaskActualSeconds()`. |
| F5 | reviewer_m25_1 R3 / challenger_m25_1 P1 | HIGH | `getReviewStats(days)` must use `YanjiTime.lastDaysRange(days)` rolling window and honour `days`; no future-dated zero bars. Also adjudicate the contract-overflow `dailyAverageMinutes`/`activeDays`. |
| F6 | challenger_m25_1 P0-1 | HIGH | Wire task → focus: task row body `onPress` carries `taskId`; `FocusScreen` must pass it to `startFocus`. |
| F7 | challenger_m25_1 P0-2 | HIGH | Add task create/edit/delete UI (`createTask`/`deleteTask` bridged but zero call sites). |
| F8 | challenger_m25_1 P0-3 / WARN 2 / challenger_m25_2 P2 | HIGH | Remove hardcoded `focusDurationMinutes = 45` / `breakDurationMinutes = 10`; `UserSettings` has no such fields. |
| F9 | challenger_m25_1 P0-5 / challenger_m25_2 P1 | HIGH | Keep tab screens mounted so Record Moment drafts, Focus subject/duration and Review date survive tab switches. |
| F10 | reviewer_m25_1 R4 | HIGH | `saveQuickNote` must resolve only after the Room write lands. |
| F11 | reviewer_m25_2 #3 | HIGH | Add `onAccent` token per mode (dark `#0D111A`, matching `YanjiDarkOnPrimary`); replace the 4 hardcoded `#FFFFFF` on-accent foregrounds. |
| F12 | reviewer_m25_2 #4 | HIGH | Adopt Compose's retired semantic colors `#17784F`/`#9A6100`/`#BE3232` instead of the WCAG-failing `#2F9E6D`/`#E67E22`/`#D64545`. |
| F13 | reviewer_m25_1 R5 | HIGH | Bridge unit tests (this test file is the first: `CollectFirstSemanticsTest`). |
| F14 | auditor_m25_1 WARN 1 | MEDIUM | Add `node_modules/` to `.gitignore` — 18,427 untracked entries are one `git add .` from catastrophe. Preserve the user's in-flight `.freebuff/` line. |
| F15 | challenger_m25_1 P1 / challenger_m25_2 P1 | MEDIUM | Sub-60s dropped sessions must not leave Today claiming "还没开始". |
| F16 | challenger_m25_1 P1 | MEDIUM | Add `BackHandler`; Settings must be a real route with predictable back. |
| F17 | challenger_m25_2 P2 | MEDIUM | `FocusScreen` bare `catch {}` must surface `E_SESSION_ACTIVE`. |
| F18 | reviewer_m25_2 #1/#2/#5/#6/#7 + auditor WARN 3 | LOW | Fix stale `theme-tokens.js` palette + add radius `28`; decide NativeWind's role; `accessibilityRole` on modal footer buttons; update `TEST_READY.md` to 58/58, Tier 1 = 42/42. |

---

## Batch execution log

### fixer_design_system — report received, changes applied by orchestrator
The agent runs **read-only** (no file-edit tool), so it delivered a fully specified
patch instead. The orchestrator applied it verbatim:

- **F11** `src/theme/tokens.ts`: added `onAccent` (light `#FFFFFF`, dark `#0D111A`) and
  `scrim` (light `rgba(0,0,0,0.32)`, dark `rgba(0,0,0,0.56)`) to both palettes.
  Replaced hardcoded on-accent `#FFFFFF` with `theme.colors.onAccent` in
  `YanjiUI.tsx:77`, `RecordMomentModal.tsx:152`, `SettingsScreen.tsx:89`;
  scrim tokenized at `RecordMomentModal.tsx:88`.
- **F12** `src/theme/tokens.ts` light semantics now `#17784F` / `#9A6100` / `#BE3232`
  per `Color.kt:105-111`, with the authority + measured ratios in a comment.
- **F5** `ReviewScreen.tsx`: renders `日均专注时长` from `stats.dailyAverageMinutes`
  and a `任务完成` section (`completed / total`) scoped to the selected date.
  `activeDays` deliberately NOT rendered (it is the average's denominator and
  invites the streak/goal framing §05 forbids). Zero-task days render nothing.
- **F8** `SettingsScreen.tsx`: deleted the fabricated `默认专注` / `单次专注时长` block.
- **F18** `RecordMomentModal.tsx`: `accessibilityRole` + `accessibilityLabel` on both
  footer buttons.

Verified: `npm run typecheck` → exit 0. `grep "#FFFFFF" src/` now returns only
`FocusScreen.tsx:216`, which belongs to `fixer_ui_nav` (relayed in its brief).
`grep focusDurationMinutes|breakDurationMinutes src/screens/SettingsScreen.tsx` → empty.

**NativeWind recommendation relayed to the user (architecture decision, NOT acted on):**
remove NativeWind rather than adopt it. Facts: `className` = 0 occurrences vs 114
`style={{}}` objects; the toolchain is fully wired and fully paid for; and
`tailwind.config.js:21-23` still carries the retired `#2F9E6D`/`#E67E22`/`#D64545`
values F12 just removed — two token sources already caused the exact drift §三.8
forbids. NativeWind's `dark:` is OS-driven and cannot express Yanji's persisted
`user_settings.themeMode` without a custom colorScheme bridge. Deleting is cheaper
than converting 114 call sites and also shrinks the bundle (nativewind is currently
in `dependencies`). **Awaiting user adjudication** — not performed in this batch.

### Latent breakage flagged by fixer_design_system (owned by fixer_ts_contract)
`test-e2e/contracts/mock-bridge.js:65` reads `this.userSettings.focusDurationMinutes * 60`
to seed countdown `remainingSeconds`. If F8 removes that default without replacing the
read, every E2E `startFocus` gets `NaN` remaining seconds. Flagged to `fixer_ts_contract`.
