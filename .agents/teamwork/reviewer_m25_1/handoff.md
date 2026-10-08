# Reviewer 1 — Native Bridge Layer (Milestone 2) Adversarial Review

Scope: `app/src/main/java/com/example/yanji/bridge/*`, `YanjiApplication.kt`, `src/bridge/index.ts`
Oracle: `test-e2e/contracts/bridge-schema.js`, `test-e2e/contracts/mock-bridge.js`

## Verdict: REQUEST_CHANGES

---

## Observation

### A. Authority boundary — CLEAN

**Q1 (JS-side timer):** No. Grep across `src/**/*.ts(x)` for
`setInterval|setTimeout|Date.now()|performance.now` returns **zero matches**.
`src/bridge/index.ts` is pure declaration + `NativeEventEmitter` wiring.
`FocusScreen.tsx` derives `displaySeconds` from `session.isCountdown ?
tick.remainingSeconds : tick.elapsedSeconds` — it consumes the native tick, it
never accumulates. No second authoritative timer exists. PASS.

**Q2 (timer delegation):** PASS. `YanjiTimerModule` never computes time from
wall clock. Every elapsed value comes from
`TimerCalculator.calculateElapsedSeconds(snapshot, SystemMonotonicClock.nowMs())`
(lines 70, 195) or from `FocusTimerService.elapsedSecondsForUi` (line 157).
`startFocus` resolves the target duration via `FocusModes.targetSeconds(mode)`
(line 109) — the same single source the Compose path uses. No `+1` per tick, no
`System.currentTimeMillis()` deltas.

**Q3 (Room access):** PASS. Grep for `YanjiDatabase|Dao|dao|DAO` across
`app/src/main/java/com/example/yanji/bridge/*.kt` returns **zero matches**.
`YanjiDataModule` imports only `YanjiRepository`, `StudyStatisticsRepository`,
`SubjectCatalog`, `YanjiTime`, `NoteEntry`, `StudyStatisticsRepository` and
domain entities. All mutations go through `repository.saveStudyTask`,
`repository.setStudyTaskCompleted`, `repository.deleteStudyTask`,
`repository.addOrUpdateNote`, `repository.setNoteFavorite`, `repository.deleteNote`,
`repository.updateSettings`. Statistics go through
`StudyStatisticsRepository(repository).getWeeklyStudySummary(0)` /
`.getDailyStudySummary(date)`.

### B. `collectFirst` is CORRECT (not a defect — verified against library bytecode)

`YanjiDataModule.collectFirst` (lines 57-65) throws a private
`FirstEmissionException` from inside the collector lambda to break collection.

This is **safe**. I verified against the actual kotlinx-coroutines 1.10.2
bytecode in the local Gradle cache:

- `kotlinx/coroutines/flow/internal/SafeCollector.emit` wraps the downstream
  collector invocation and records any thrown exception in a
  `DownstreamExceptionContext` (constant pool contains
  `DownstreamExceptionContext`, `exceptionOrNull-impl`).
- `SafeCollector.exceptionTransparencyViolated` produces the string
  `"Flow exception transparency is violated: Previous 'emit' call has thrown exception ..."`.

That mechanism is specifically designed for this pattern: it distinguishes
"the *collector* threw" (which must propagate and terminate the flow — exactly
what `collectFirst` wants) from "the *emitter* threw after the collector threw"
(which is a transparency violation). Because `FirstEmissionException` is thrown
from the collector, not from an upstream `emit`, the exception propagates out of
`flow.collect {}`, unwinds the coroutine, and `result` is already assigned.
There is no `AbortFlowException` interception issue because the throw site is
inside the `collect` block, not inside an `emit` block.

The one theoretical hole is a `Flow` that emits zero values — then `result`
stays null and `collectFirst` returns null, silently. For
`repository.observeStudyTasks(date)` (a Room `Flow`) the table-backed flow
always emits at least `emptyList()`, so this is unreachable in practice.

**Simpler correct approach** (recommended as a cleanup, not a blocker):
`flow.first()` from `kotlinx.coroutines.flow` — the library's own
`first()` uses `collectWhile`, which is purpose-built. Note the current code
also *does* import `kotlinx.coroutines.flow.Flow` on line 25 while
`kotlinx.coroutines.flow.collectLatest` is imported on line 23 and
`kotlinx.coroutines.launch` on line 24 — the import block is unsorted
(`Flow` after `collectLatest`), a minor hygiene issue.

### C. `getTodayStats` criterion MATCHES the Compose UI

`YanjiDataModule.getTodayStats` filters
`it.status.name == "COMPLETED" && YanjiTime.localDate(it.startTime).toString() == date`.

`StudyStats.durationOnDay` filters
`it.status == SessionStatus.COMPLETED && isCompletedToday(it.startTime, dayEpochMs)`
where `isCompletedToday` is `YanjiTime.localDate(startTime) == YanjiTime.localDate(dayEpochMs)`.

Same predicate, same `ZoneId.systemDefault()`. Both are whole-day local-date
comparisons with no timezone conversion risk and no off-by-one. Note the bridge
additionally calls `.toString()` on the `LocalDate`, which for ISO formatter
output is identical to `LocalDate.equals` on the parsed date. MATCH. PASS.

One inconsistency worth flagging: `notesCount` uses
`repository.noteEntries.value.filter { it.date == date }` (string compare on
`date`), while `completedTasksCount` uses `collectFirst(repository.observeStudyTasks(date))`.
Different mechanisms, but both are date-correct. Not a defect.

### D. `taskMap.actualMinutes: 0` — TRUTHFULNESS PROBLEM

`taskMap` hardcodes `putInt("actualMinutes", 0)` (line 420). The contract
schema only requires `typeof obj.actualMinutes === 'number'`, so it is
schema-conformant. But the project already has
`YanjiRepository.observeTaskActualSeconds()` (YanjiRepository.kt line 431) which
returns `Map<String, Long>` of taskId -> seconds, aggregated by SQLite `GROUP BY`.
The Compose TodayScreen renders 实际/计划 from that map.

Emitting a constant `0` means the RN TodayScreen task rows will show "0 分钟"
for every task while the native app shows real accumulated minutes. This is a
**displayed-data divergence**, not fake data — but it violates the spirit of
"JS 侧拿到的永远是与 Compose UI 同一口径的真实本地数据" stated in the module's
own KDoc (line 33). See required change R2.

### E. `getReviewStats` uses calendar week — SEMANTIC MISMATCH with contract

`getReviewStats(days)` (line 255) calls
`StudyStatisticsRepository(repository).getWeeklyStudySummary(0)`.
`getWeeklyStudySummary(0)` -> `YanjiTime.weekRange(0)` -> Monday-to-Sunday of the
**current calendar week**. The `days` parameter is only echoed back into
`putInt("days", days.toInt())` and otherwise ignored.

- `bridge-schema.js` `isValidReviewStats` requires `days` to be a number. It
  does not assert window semantics. So schema-conformant.
- `mock-bridge.js` `getReviewStats(days)` builds `dailyFocusMinutes` over a
  **rolling** `days` window (lines 294-297: `virtualClockMs - i*86400000`).
- `ORIGINAL_REQUEST.md` R2 回顾 requires "近 7 天趋势".
- `PROJECT.md` Interface Contracts lists `getReviewStats(days: number)`.

So the native implementation answers "this calendar week" while the contract
oracle answers "last N rolling days". If today is Wednesday, the native module
returns Mon/Tue/Wed + future zero-days (Thu..Sun), whereas the mock returns
Thu..Wed. `ReviewScreen.tsx` sorts `dailyFocusMinutes` entries by key, so it
will render future dates with zero bars — visible wrong data on 6 of 7 days of
the week. See required change R3.

`YanjiTime.lastDaysRange(days)` (YanjiTime.kt line 92, "Rolling N-day semantics,
including today and excluding tomorrow") is the correct primitive and already
exists. Note `StudyStatisticsRepository.getLastDaysDurationFlow(days)` already
wraps it — but it returns only a total `Long`, not per-day bars, so a new
aggregate builder is needed. This is not a trivial one-liner.

`getDailyTimeline` is correct — it uses `getDailyStudySummary(date)` which is a
single-day window. No mismatch there.

### F. `sessionPhase()` emits schema-NONCONFORMANT `phase` and `mode`

This is the most serious contract defect.

`YanjiTimerModule.sessionPhase()` (lines 225-229) returns:
- `TimerPhase.PAUSED.name` = `"PAUSED"`
- `"COUNTDOWN"` / `"STOPWATCH"`

`BridgeSchemas.isValidActiveSessionState` (bridge-schema.js lines 18, 14) requires:
- `obj.phase` ∈ `{FOCUS, BREAK, IDLE}`
- `obj.mode` ∈ `{COUNTDOWN, STOPWATCH}`

So the native payload has `phase` = `"PAUSED"` or `"COUNTDOWN"`/`"STOPWATCH"`,
and `mode` = the raw Chinese mode string from `session.mode` (e.g.
`"25分钟番茄"`, `"正向计时"`, `"45分钟专注"`). **Both fields are non-conformant.**
`isValidActiveSessionState` would return `false` for every payload the native
module ever emits.

The module defines the correct constants in its own companion object
(`PHASE_FOCUS = "FOCUS"`, `PHASE_BREAK = "BREAK"`, `PHASE_IDLE = "IDLE"`, lines
243-245) with the KDoc "会话阶段常量（与 BridgeSchemas 契约一致）" — and then
never uses them. `sessionPhase()` is dead-wrong relative to its own declared
contract.

Consequence for the RN layer: `src/bridge/index.ts` line 30 declares
`phase: TimerPhase` where `TimerPhase = 'IDLE' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'CANCELLED'`
— a *third*, different vocabulary. `typecheck` passes because TS cannot verify
the runtime payload. `FocusScreen.tsx` never reads `phase` (it reads
`isPaused` and `isCountdown`), so no visible breakage today — but any future
consumer trusting `phase` gets `"PAUSED"` where the contract promises `"FOCUS"`.

Same problem for `mode`: `session.mode` is `FocusModes.COUNT_UP` =
`"正向计时"`, or `"25分钟番茄"`, or `"45分钟深度"`, or `"N分钟专注"`. The
contract requires `"COUNTDOWN"` or `"STOPWATCH"`. The mock bridge emits
`mode.toUpperCase()` of what it was passed (default `'COUNTDOWN'`), and
`m2-native-bridge.test.js` line 30 asserts `active.mode === 'COUNTDOWN'`. The
native module can never satisfy that assertion.

See required change R1.

### G. `observeTicker` `lastEmittedPaused` guard — DEFECTIVE (drops ticks)

```kotlin
private fun observeTicker() {
    scope.launch {
        FocusTimerService.elapsedSecondsForUi.collectLatest { elapsed ->
            val session = ActiveSessionCoordinator.active.value ?: return@collectLatest
            val snapshot = ActiveSessionCoordinator.currentTimerSnapshot ?: return@collectLatest
            val paused = snapshot.phase == TimerPhase.PAUSED
            if (lastEmittedPaused == paused && paused) return@collectLatest
            lastEmittedPaused = paused
            emitTick(elapsed, remainingOf(...), snapshot.phase, paused, session)
        }
    }
}
```

Consider pause -> resume -> pause:
1. Running: `lastEmittedPaused` is `null` initially. `paused=false`. Guard
   `null == false && false` -> false. Emit. `lastEmittedPaused = false`.
2. Pause: `paused=true`. Guard `false == true && true` -> false. Emit.
   `lastEmittedPaused = true`.
3. While paused, the ticker flow keeps emitting (it is a `StateFlow` whose value
   is only assigned in `pause()` at line 290 — actually `pause()` assigns
   `_elapsedSecondsForUi.value = elapsed` once and cancels `timerJob`, so no
   further emissions occur while paused). Assume one emission arrives.
   Guard `true == true && true` -> **true. Dropped.** OK — this is the intended
   behaviour (suppress redundant paused ticks).
4. Resume: `paused=false`. Guard `true == false && false` -> false. Emit.
   `lastEmittedPaused = false`. OK.

So the pause/resume/pause sequence is handled correctly *given the service's
current emission pattern*. However the guard is fragile for two reasons:

1. **It drops the final running tick when transitioning into a pause if the
   service does not emit one.** `FocusTimerService.pause()` does emit
   (`_elapsedSecondsForUi.value = elapsed` at line 290) so this is currently
   covered. But the invariant is implicit and unguarded by any test. If
   `pause()` were ever changed to not assign (e.g. relying on
   `publishSemanticState()`), the RN side would never learn the session paused.
   The `onTimerStateChanged` path would still fire via
   `ActiveSessionCoordinator.coordinatorState`, so the UI recovers — but the
   tick event stream would be silently wrong.

2. **`lastEmittedPaused` is never reset when a session ends.** After
   `completeTimer`, `lastEmittedPaused` retains its last value (say `false`).
   `_elapsedSecondsForUi` is reset to `0L` by `discard()` (line 460) and
   `onCountdownFinished`, which will re-emit `0` with `paused=false`, passing
   the guard. Fine. But after a *completion*, `_elapsedSecondsForUi` is set to
   `targetDurationSeconds`/`elapsed` and then the service stops ticking; the
   coordinator clears `active`, so `observeTicker` returns early at line 158.
   No stale emit. Acceptable.

Net: the guard is *currently* correct but **not provably correct**, and it
suppresses ticks based on a single boolean rather than on
"(elapsed, paused)" pairs. A `(elapsed, paused)` dedupe key or simply removing
the guard and relying on `elapsedSecondsForUi`'s own `StateFlow` distinctness
would be more robust. Classified as a caveat, not a blocker — I could not
construct a live drop scenario with the current `FocusTimerService` code.

### H. `getActiveSession` after process death — CORRECT

`ActiveSessionCoordinator.restorePersistedNow()` (lines 375-455) sets
`currentTimerSnapshot = snapshot` in **both** branches (same-boot line 424,
reboot line 439) before returning the session. `YanjiRepository.bindDatabase`
calls `ActiveSessionCoordinator.restorePersisted { ... }` at line 265, which
invokes `restorePersistedNow()`. So whenever `active.value != null` after
restore, `currentTimerSnapshot` is also non-null. The `if (session == null ||
snapshot == null) resolve(null)` guard in `getActiveSession` is therefore
defensive but not load-bearing. CORRECT.

`TimerStore.bind(db)` (line 70) only subscribes to DAO flows and mirrors
`ActiveSessionCoordinator.active` into `_activeFocus` — it does not touch
`currentTimerSnapshot`. No conflict.

Note the `TimerMachine` inside `FocusTimerService` is restored via
`machine.restore(snapshot)` in `startTimer` (line 225) / `restoreTimer`
(line 280), and `FocusTimerService.restoreActive(ctx)` is dispatched by
`bindDatabase` after restore completes. Ordering is correct.

### I. `startFocus` ordering — CORRECT, promise rejection correct

`YanjiTimerModule.startFocus` (lines 83-113):
1. `YanjiRepository.startFocus(...)` -> `timerStore.startFocus` -> creates
   `FocusSession` and calls `ActiveSessionCoordinator.begin(...)`.
   Returns `null` if the coordinator refused (state != IDLE, or persistence
   save failed).
2. If null: `promise.reject("E_SESSION_ACTIVE", ...)` and `return@launch`.
   Correct — the service is never started, no phantom timer.
3. Otherwise `FocusTimerService.startFocus(context, sessionId, subjectName, targetSeconds)`.

This ordering is **mandatory and correct**: the repository owns the durable
`ActiveSessionCoordinator.begin()` (which persists `ActiveSessionRecord` to
`noBackupFilesDir`), and the service refuses to start a timer whose sessionId
does not match the coordinator's active session (`startTimer` lines 211-222,
"must never invent a second timer"). Reversing the order would let the service
run with no coordinator registration and then `stopSelf()`.

One nuance: `promise.resolve(true)` is called immediately after
`FocusTimerService.startFocus(...)`, which is `dispatch(...)` ->
`context.startForegroundService(intent)` — an **asynchronous** handoff. The
promise resolves before `onStartCommand` runs. This is acceptable for a
boolean "accepted" contract, and the `onTimerStateChanged` event is the real
completion signal (the JS `FocusScreen` calls `refreshSession()` after
`startFocus` resolves, and also listens for the state event). Not a defect.

### J. pause/resume/complete/discard entry points — CORRECT

- `pauseTimer` -> `FocusTimerService.pauseTimer(context)` -> `ACTION_PAUSE` ->
  `pause()` -> `machine.pause()` + `ActiveSessionCoordinator.update { paused = true }`.
- `resumeTimer` -> `ACTION_RESUME` -> `resume()` -> `machine.resume()` + update.
- `completeTimer` -> `ACTION_COMPLETE` -> `completeByUser()` ->
  `ActiveSessionCoordinator.complete(actualSeconds = elapsed, ...)` ->
  `TimerStore.persistCompletedFocus` -> `db.focusSessionDao().insert(...)`.
  Records the real duration. COMPLETED semantics preserved.
- `discardTimer` -> `ACTION_DISCARD` -> `discard()` -> `machine.cancel()` +
  `ActiveSessionCoordinator.cancel()` -> `clearActiveSession()` with **no DB
  write**. CANCELLED semantics preserved — nothing recorded.

The COMPLETED vs CANCELLED distinction is intact. `TimerStore.isRecordableFocus`
(< 60s -> not persisted) is a business rule that applies equally to the Compose
path; the bridge does not bypass it.

All four resolve `true` unconditionally, even if no session is active (the
service's `pause()`/`resume()` early-return when phase mismatches, and
`completeByUser` logs + `stopSelf()` when nothing to restore). Returning `true`
for a no-op is a minor truthfulness issue but matches the contract's
`Promise<boolean>` and the mock's behaviour (mock returns `false` for no active
session — a divergence, see caveats).

### K. `saveQuickNote` resolves before the write lands — TRUTHFULNESS ISSUE

```kotlin
repository.addOrUpdateNote(entry)   // NoteStore.addOrUpdate — NOT suspend, launches own coroutine
emitDataChanged("notes")
promise.resolve(noteMap(entry, sessionId))
```

`NoteStore.addOrUpdate` (NoteStore.kt lines 49-60) is a plain `fun` that does
`scope.launch { ... dao.insert(...) }`. The bridge's `scope.launch { ... }`
block therefore returns before the Room insert is issued. `promise.resolve`
fires, the modal closes (`RecordMomentModal.tsx` lines 64-67:
`saveQuickNote` -> `setText('')` -> `onClose()`), and only afterwards does the
DAO write happen.

Consequences:
- The returned `NoteEntry` object **is** truthful in content (id, date,
  content, createdAt are all set by the bridge and identical to what will be
  persisted). But `NoteStore.addOrUpdate` **overwrites `updatedAt` with
  `System.currentTimeMillis()`** at write time — so the value the JS side holds
  for `updatedAt` (not exposed in `noteMap`, so invisible) differs.
- The `sessionId` returned is `sessionId ?: ""` — a **stringly-typed sentinel**
  instead of `null`. `src/bridge/index.ts` line 53 types it as
  `sessionId: string`, and `RecordMomentModal` passes `session?.sessionId ?? null`
  in, but the native `noteMap` converts null to `""`. So a note saved without a
  session comes back with `sessionId: ""` rather than `null`. Minor contract
  wobble; the mock returns `sessionId: sessionId || null` (line 248) — i.e.
  `null`. Divergence.
- Because the write is async, `onDataChanged("notes")` is emitted **before**
  the row exists. Any JS listener that immediately re-queries
  `getNotesForDate` may race and miss the just-saved note. In practice the
  Room Flow re-emits on insert and fires a second `onDataChanged`, so the UI
  converges — but the *first* refresh after save can be stale.

This is a genuine ordering defect. It does not lose data, but "保存成功" can be
shown while the write is still in flight, and an immediate reload can miss it.
See required change R4.

### L. `updateUserSettings` — no API key leak, but partial-write clobbering

`updateUserSettings` (lines 335-345) reads `current = repository.settings.value`,
then `current.copy(examDate=..., dailyGoalHours=..., validStudyThresholdMinutes=..., themeMode=...)`
guarded by `settings.hasKey(...)`. Absent keys correctly fall back to current.
It never reads or writes `aiApiKey` — the credential is not in the accepted key
set, and `UserSettingsEntity.fromDomainModel` drops `aiApiKey` entirely
(Entities.kt). `updateSettings` only touches the keystore when
`newSettings.aiApiKey.trim() != cachedAiApiKey`; since the bridge copies
`current` which carries `aiApiKey = cachedAiApiKey` (injected at
YanjiRepository.kt line 298), that branch is not taken. **No credential leak.**
PASS on the red line.

However there is a **lost-update / clobber hazard**: `current` is a snapshot of
a `StateFlow`. If the DB emits a newer settings row between the snapshot and
`updateSettings`, the bridge overwrites it with the stale copy. The KDoc at
YanjiRepository.kt lines 283-290 explicitly acknowledges this known tradeoff
("a user-initiated write that has not yet landed in the DB may briefly be
overwritten ... only visible under rapid parallel writes"), so it is a
pre-existing accepted behaviour, not introduced by the bridge.

Also: `focusDurationMinutes` and `breakDurationMinutes` are emitted as
**hardcoded 45 / 10** in `settingsMap` (lines 440-441) even though
`UserSettings` has no such fields — the real source is `FocusModes` /
duration presets. And `updateUserSettings` accepts `dailyGoalHours` /
`validStudyThresholdMinutes` which are **not** in `UserSettings` TS interface
(lines 100-107). So `getUserSettings` reports values that `updateUserSettings`
cannot round-trip. Not a red-line violation; a contract-completeness gap.

### M. `YanjiThemeModule.setThemePreference` — CORRECT, no system-mode violation

`setThemePreference` parses against `YanjiThemeMode.entries` (SYSTEM/LIGHT/DARK)
and calls `repository.updateSettings(current.copy(themeMode = parsed.name))`.
`current` is a full copy, so **no other settings field is clobbered** — this is
strictly better than `YanjiDataModule.updateUserSettings`, which builds its own
partial copy.

It never calls `UiModeManager`, `setApplicationNightMode`,
`setNightModeOverride`, or any `settings put` — verified by grep. It only reads
`resources.configuration.uiMode` in `isSystemDark()`. **No violation of
AGENTS.md §二.6 / §三.13.** PASS.

`isSystemDark()` compares
`configuration.uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES`.
`YanjiThemeMode.resolveDarkTheme()` uses `isSystemInDarkTheme()`, which
resolves to the same `UI_MODE_NIGHT_MASK` check on the current configuration.
Same resolution. PASS.

One divergence: `YanjiTheme` is constructed with `darkTheme = isSystemInDarkTheme()`
by default (Theme.kt line 146), and `MainActivity` must pass
`YanjiThemeMode.resolveDarkTheme()` — the Compose side may not yet honour the
stored `themeMode` at all (out of scope for this review; flagged for the M3/M4
reviewers). The bridge itself is consistent.

### N. `YanjiPackage` / `ReactPackage` deprecation — BENIGN

RN 0.87.1, `newArchEnabled` is **not set** in `gradle.properties` (the file
contains no RN architecture flags), so the app runs the old architecture. In
the old architecture, `ReactPackage` + `ReactNativeHost.getPackages()` +
`MainReactPackage()` is the supported path. `TurboReactPackage` /
`BaseReactPackage` are for interop/codegen with the new architecture and are
not required here.

`YanjiApplication` correctly: initializes `SoLoader`, calls
`YanjiRepository.init(this)` in `onCreate` (which is required because
`FocusTimerService.onCreate` also calls `YanjiRepository.init` defensively at
line 169, and `ActiveSessionCoordinator.bind` happens inside
`bindDatabase`), sets `getBundleAssetName() = "index.android.bundle"` and
`getUseDeveloperSupport() = false`. `YanjiPackage.createViewManagers` returns
`emptyList()` — correct, no native views.

**But**: there is no `ReactActivityDelegate`/`DefaultReactActivityDelegate`
override in `YanjiApplication`, and `MainActivity` is not in this review's file
set. `getJSMainModuleName() = "index"` matches `index.js`. The bundle asset
must exist at `app/src/main/assets/index.android.bundle` (M1 scope). Not
verifiable here; flagged as out-of-scope.

---

## Logic Chain

1. Contract oracle `bridge-schema.js` pins `phase ∈ {FOCUS,BREAK,IDLE}` and
   `mode ∈ {COUNTDOWN,STOPWATCH}`.
2. `YanjiTimerModule.sessionPhase()` emits `PAUSED` / `COUNTDOWN` / `STOPWATCH`
   for `phase`, and `session.mode` (a Chinese `FocusModes` name) for `mode`.
3. => every `getActiveSession()` / `onTimerStateChanged` payload from the real
   native module fails `isValidActiveSessionState`.
4. The module's own companion constants (`PHASE_FOCUS/BREAK/IDLE`) exist with a
   KDoc claiming contract alignment, and are unused — evidence the mapping was
   intended and then lost.
5. `src/bridge/index.ts` declares a *third* vocabulary
   (`'IDLE'|'RUNNING'|'PAUSED'|'COMPLETED'|'CANCELLED'`) which neither the
   contract nor the native payload matches. `npm run typecheck` passes because
   TS cannot check a runtime `WritableMap`.
6. The E2E suite passes 58/58 because it drives `MockYanjiBridge`, which is
   contract-conformant by construction — it never exercises the Kotlin module.
   => the contract oracle is green while the real bridge is red. This is exactly
   the class of failure the gate exists to catch.

Second chain (review stats):
1. `ORIGINAL_REQUEST.md` R2 + `mock-bridge.js` both define 回顾's trend as a
   rolling N-day window ending today.
2. `YanjiDataModule.getReviewStats` delegates to `getWeeklyStudySummary(0)`
   (calendar Mon-Sun) and ignores `days`.
3. `ReviewScreen.tsx` sorts the map by key and renders one bar per entry,
   including future dates with 0.
4. => on any day except Monday, the 回顾 trend shows fabricated zero-bars for
   days that have not happened yet, and omits real data from the prior week.
5. The correct primitive `YanjiTime.lastDaysRange(days)` already exists in the
   codebase (YanjiTime.kt line 92) — so this is a wiring mistake, not a missing
   capability.

---

## Caveats

- I did **not** run the app on a device; per AGENTS.md §二.3 / §三.11 I did not
  use `screencap`, `input tap/swipe`, or `uiautomator dump`. All findings are
  static-analysis based on the actual source and the coroutines library
  bytecode in the local Gradle cache.
- I did **not** verify runtime behaviour of `collectFirst` with an instrumented
  test (no JVM test covers the bridge — grep for `YanjiDataModule` /
  `collectFirst` / `FirstEmissionException` across `app/src/test/**` returns
  zero matches). My safety conclusion is derived from the library's
  `SafeCollector` / `DownstreamExceptionContext` implementation, which
  explicitly handles collector-thrown exceptions.
- `MainActivity.kt` was not in the review file list; I could not verify whether
  the stored `YanjiThemeMode` is actually honoured by the Compose host, nor
  whether `app/src/main/assets/index.android.bundle` exists. These belong to
  M1/M3 and are flagged, not counted against M2.
- The `lastEmittedPaused` guard analysis relies on the current
  `FocusTimerService.pause()` assigning `_elapsedSecondsForUi`. I could not
  construct a live drop with today's code, but the invariant is unasserted by
  any test.

---

## Conclusion

**REQUEST_CHANGES**

Required changes (numbered):

1. **R1 — Fix `sessionPhase()` and the `mode` field to be schema-conformant.**
   `phase` must be one of `FOCUS | BREAK | IDLE` (use the already-defined
   `PHASE_FOCUS` / `PHASE_BREAK` / `PHASE_IDLE` companion constants, which are
   currently dead code). `mode` must be `COUNTDOWN | STOPWATCH`, derived from
   `session.targetDurationSeconds > 0` — not the raw Chinese `FocusModes` name.
   Align `src/bridge/index.ts` `TimerPhase` / `TimerMode` unions to the contract
   vocabulary so the TS types stop lying about the payload. Until this lands,
   `BridgeSchemas.isValidActiveSessionState` returns `false` for every real
   native payload while the E2E suite stays green on the mock.

2. **R2 — Replace `taskMap`'s hardcoded `actualMinutes: 0`** with the real value
   from `YanjiRepository.observeTaskActualSeconds()` (already exists,
   YanjiRepository.kt line 431). Emitting a constant 0 while the native UI shows
   real minutes breaks the module's own stated guarantee of
   "与 Compose UI 同一口径".

3. **R3 — Make `getReviewStats(days)` a rolling N-day window**, not
   `getWeeklyStudySummary(0)`. Use `YanjiTime.lastDaysRange(days)` (already
   exists) and build per-day bars from it, so `days` is honoured and no future
   zero-days are fabricated. Today, on 6 of 7 weekdays the 回顾 trend is wrong.

4. **R4 — Make `saveQuickNote` resolve only after the Room write lands.**
   Either expose a `suspend` note-save path on the repository (preferred, and
   consistent with how `saveStudyTask` / `setStudyTaskCompleted` are already
   suspend), or await the store's completion before `promise.resolve`. Also
   return `sessionId` as a real nullable value rather than the `""` sentinel,
   so `noteMap` matches `mock-bridge.js`'s `null`.

5. **R5 — Add a JVM unit test that asserts the bridge's own payloads satisfy
   `BridgeSchemas`.** There is currently zero test coverage of
   `app/src/main/java/com/example/yanji/bridge/**` (verified by grep). The
   whole reason a `phase`/`mode` mismatch survived a green 58/58 suite is that
   the Kotlin module is never executed by any test. A single test calling
   `YanjiTimerModule.activeSessionMap(...)` and re-implementing the schema
   assertions would have caught R1.

Recommended (non-blocking):

- R6 — Replace the `FirstEmissionException` trick in `collectFirst` with
  `flow.first()`. Verified safe today, but the exception-based control flow is
  unnecessary now that the standard operator is available.
- R7 — Sort the `kotlinx.coroutines.flow` imports in `YanjiDataModule`
  (line 23-25: `collectLatest`, `launch`, `Flow` out of order).
- R8 — Either drop the unused `PHASE_*` companion constants or actually use
  them (they are the fix for R1).
- R9 — Reconcile `UserSettings` TS interface with what `settingsMap` /
  `updateUserSettings` actually support (`dailyGoalHours`,
  `validStudyThresholdMinutes` are accepted but not typed; `focusDurationMinutes`
  / `breakDurationMinutes` are emitted but not updatable).

## Verification Commands & Results

```
npm run typecheck
```
```
> yanji@1.0.0 typecheck
> tsc --noEmit
(exit 0, no output)
```
Note: passes **only because** TS cannot see the runtime `WritableMap` — this is
not evidence that R1 is fine.

```
.\gradlew.bat :app:compileDebugKotlin
```
```
> Task :app:compileDebugKotlin UP-TO-DATE
BUILD SUCCESSFUL in 26s
7 actionable tasks: 7 up-to-date
Configuration cache entry reused.
```

```
.\gradlew.bat :app:testDebugUnitTest
```
```
> Task :app:testDebugUnitTest UP-TO-DATE
BUILD SUCCESSFUL in 34s
26 actionable tasks: 26 up-to-date
Configuration cache entry reused.
```
Parsed `app/build/test-results/testDebugUnitTest/*.xml` (47 files):
`TOTAL tests=338 failures=0 errors=0 skipped=0`.
No test file references the bridge package — hence R5.

```
node test-e2e/runner.js
```
```
 ✔ Tier 1    : 42/42 passed (100.0%) [Failed: 0, Skipped: 0]
 ✔ Tier 2    : 9/9 passed (100.0%) [Failed: 0, Skipped: 0]
 ✔ Tier 3    : 4/4 passed (100.0%) [Failed: 0, Skipped: 0]
 ✔ Tier 4    : 3/3 passed (100.0%) [Failed: 0, Skipped: 0]
 Total Tests: 58  Passed: 58  Failed: 0  Skipped: 0
Result: ALL TESTS PASSED (100% SUCCESS)
```
Green — but the suite drives `MockYanjiBridge`, which is contract-conformant by
construction. It never executes the Kotlin modules, so it cannot detect R1/R3.

Supporting greps (all executed, all empty where noted):
- `src/**/*.ts(x)` for `setInterval|setTimeout|Date.now()|performance.now`
  -> **0 matches** (no JS timer).
- `app/src/main/java/com/example/yanji/bridge/*.kt` for
  `YanjiDatabase|Dao|dao|DAO` -> **0 matches** (no direct Room access).
- `app/src/main/java/com/example/yanji/**` for `CoroutineExceptionHandler`
  -> **0 matches** (see caveat on unhandled-exception path).
- `gradle.properties` for `newArchEnabled|hermesEnabled` -> **0 matches**
  (old architecture; `ReactPackage` is the correct base).
