# fixer_kotlin_bridge — Handoff

**Batch**: M2–M5 Gate Remediation, Kotlin half
**Owned fixes**: F1, F2 (Kotlin), F3, F4, F5 (Kotlin), F8 (Kotlin), F10, F13
**Verification**: `.\gradlew.bat :app:testDebugUnitTest` → **BUILD SUCCESSFUL**, 375 tests, 0 failures, 0 errors.

---

## 1. What changed

### New file — [`BridgeMappers.kt`](file:///d:/AI项目/yanji/app/src/main/java/com/example/yanji/bridge/BridgeMappers.kt)

All pure bridge semantics, extracted as `internal object BridgeMappers` in the `bridge` package (testability requirement C3). Every function takes **plain Kotlin types** and returns a **plain data class** — `WritableMap` is produced only in the modules, because `Arguments.createMap()` is unavailable on the JVM test classpath.

Contains: `PHASE_FOCUS/BREAK/IDLE`, `MODE_COUNTDOWN/STOPWATCH`, `timerModeOf`, `sessionPhaseOf`, `tickPhase`, `sessionFields`, `rollingWindowDates`, `actualMinutesOf`, `taskFields`, `noteFields`, `settingsFields`, `reviewStatsFields`, plus the payload data classes `SessionFields` / `TaskFields` / `NoteFields` / `SettingsFields` / `ReviewStatsFields`.

---

### F1 (BLOCKER) — `collectFirst` deleted, `flow.first()` adopted

[`YanjiDataModule.kt`](file:///d:/AI项目/yanji/app/src/main/java/com/example/yanji/bridge/YanjiDataModule.kt)

- Deleted `private suspend fun <T> collectFirst(flow: Flow<T>)` and `private class FirstEmissionException` **entirely** — no shim, no fallback.
- Added `import kotlinx.coroutines.flow.first`; removed the now-unused `import kotlinx.coroutines.flow.Flow`.
- All three call sites converted:
  - `getTodayStats` (was :74) → `repository.observeStudyTasks(date).first()`
  - `getTodayTasks` (was :101) → same
  - `getDailyTimeline` (was :304) → same
- A KDoc block at the deletion site records *why* (throw-in-collector escapes the suspend function, `return result` unreachable, all three `promise.resolve` sites therefore never settle; zero `CoroutineExceptionHandler` in `app/src/main`).

---

### F2 — contract-conformant phase / mode vocabulary

[`YanjiTimerModule.kt`](file:///d:/AI项目/yanji/app/src/main/java/com/example/yanji/bridge/YanjiTimerModule.kt)

- `activeSessionMap` now delegates to `BridgeMappers.sessionFields(...)`.
- `mode` = `timerModeOf(targetDurationSeconds)` → `COUNTDOWN` when `> 0`, else `STOPWATCH`. The raw Chinese `FocusModes` name (`session.mode`, e.g. `正向计时`) is **no longer crossed**.
- `phase` = `sessionPhaseOf(hasSession = true)` → `FOCUS` whenever a session exists, running **or** paused. `BREAK` is reserved and never emitted. `IDLE` is only reachable when there is no session — `getActiveSession` already `resolve(null)` in that case, so no IDLE payload is ever built.
- `sessionPhase()` (the old `when` returning `PAUSED`/`COUNTDOWN`/`STOPWATCH`) is **deleted**.
- `emitTick` lost its `phase: TimerPhase` parameter and now emits `BridgeMappers.tickPhase(hasSession = true)` — same vocabulary as `ActiveSessionState.phase`.
- The `TimerPhase` **import stays**: `observeTicker` still needs `snapshot.phase == TimerPhase.PAUSED` at line 160 to compute `isPaused`.
- Companion `PHASE_*` constants now forward to `BridgeMappers.*` so there is exactly one source of truth for the vocabulary.

---

### F3 — persisted note → session binding (Option A)

**Schema** (all three owned files):

- [`Entities.kt`](file:///d:/AI项目/yanji/app/src/main/java/com/example/yanji/data/db/Entities.kt): `NoteEntryEntity` gains `val sessionId: String? = null` (appended after `isDraft`, nullable, **no default**), mapped in both `toDomainModel` (`sessionId?.ifBlank { null }`) and `fromDomainModel` (`model.sessionId?.ifBlank { null }`).
- [`Models.kt`](file:///d:/AI项目/yanji/app/src/main/java/com/example/yanji/data/Models.kt): `NoteEntry` gains `val sessionId: String? = null` with KDoc explaining that the binding is now persisted (v21) and that the bridge persists the JS-supplied parameter as-is. All construction sites use named arguments, so appending a defaulted field is safe.
- [`YanjiDatabase.kt`](file:///d:/AI项目/yanji/app/src/main/java/com/example/yanji/data/db/YanjiDatabase.kt): `version = 20 → 21`; new hand-written `MIGRATION_20_21`; registered in `migrations(context)` after `MIGRATION_19_20`.

**Bridge read paths** — every NoteEntry payload now carries the binding:

- `saveQuickNote` resolves `noteMap(BridgeMappers.noteFields(entry))` where `entry.sessionId` was set from the parameter.
- `getNotes` → `noteMap(BridgeMappers.noteFields(it))`
- `getNotesForDate` → `noteMap(BridgeMappers.noteFields(it))`
- `getDailyTimeline` notes → `noteMap(BridgeMappers.noteFields(it))`
- `noteMap` emits **explicit `putNull("sessionId")`** when unbound (never `""`), using the same idiom as `emitStateChanged`'s `putNull("session")`.

**No fallback logic added** (explicit instruction honoured): `saveQuickNote` does **not** call `getActiveSession()`, does not consult `ActiveSessionCoordinator`, and does not mirror the TS mock's `sessionId || activeSession.sessionId`. The parameter from `RecordMomentModal.tsx:56-64` is persisted verbatim (`sessionId?.takeIf { it.isNotBlank() }`). One binding, one source of truth.

---

### F4 — real `actualMinutes`

- `taskMap` now takes `actualSeconds: Long?` and delegates to `BridgeMappers.taskFields`, whose `actualMinutesOf(seconds) = max(seconds,0) / 60`.
- Fed from `repository.observeTaskActualSeconds()` (`Flow<Map<String, Long>>`, backed by `FocusSessionDao.observeTaskActualSeconds()` filtering `status='COMPLETED' AND taskId IS NOT NULL`) in **both** read paths: `getTodayTasks` and `getDailyTimeline`'s `completedTasks`.
- The old `putInt("actualMinutes", 0)` is gone.

---

### F5 — rolling window for `getReviewStats`

`getReviewStats(days)` no longer calls `getWeeklyStudySummary(0)`. It now:

1. `windowDays = days.toLong().coerceAtLeast(1L).toInt()` — `days` is honoured exactly, and `YanjiTime.lastDaysRange`'s `require(days > 0)` is satisfied.
2. `windowDates = BridgeMappers.rollingWindowDates(today - (windowDays-1), windowDays)` — local-calendar keys, earliest → latest, ending at today, **never future-dated**.
3. `range = YanjiTime.lastDaysRange(windowDays.toLong())`.
4. Reads the range-bounded `internal` APIs already in `YanjiRepository`: `observeFocusSessionsInRange`, `observeExamSessionsInRange`, `observeFocusSubjectTotals`, `observeExamSubjectTotals` — all via `.first()`.
5. Buckets each session into its window date by `YanjiTime.localDate(startTime)`, ignoring anything outside the window keys.
6. Aggregates `subjectDistribution` from the subject-total rows (minutes per subject display name).
7. Delegates the arithmetic to `BridgeMappers.reviewStatsFields`, which matches the E2E oracle `mock-bridge.js:313-350` exactly:
   - `dailyAverageMinutes = round(totalMinutes / windowDays)` — denominator is the **window length**, not active days;
   - `activeDays` counted with the **30-minute threshold** (`repository.settings.value.validStudyThresholdMinutes.coerceAtLeast(1) * 60`, mirroring `StudyStatisticsRepository.activeDayThresholdFlow` at 206-207);
   - `totalFocusHours = totalMinutes / 60.0`.
8. `dailyAverageMinutes` and `activeDays` are both kept (now official contract fields).

---

### F8 — no fabricated settings

`settingsMap` no longer contains `focusDurationMinutes` / `breakDurationMinutes`. It emits exactly `examDate`, `targetSchool`, `targetMajor`, `themePreference` — all real `UserSettings` fields. The `YanjiThemeMode` import was dropped from the module (the theme mapping moved into `BridgeMappers.settingsFields`).

---

### F10 — `saveQuickNote` resolves after the Room write lands

- [`NoteStore.kt`](file:///d:/AI项目/yanji/app/src/main/java/com/example/yanji/data/note/NoteStore.kt): new `internal suspend fun addOrUpdateAndAwait(entry: NoteEntry)`. It repeats `addOrUpdate`'s normalisation (`existing = dao.getById(entry.id)` → `createdAt = existing?.createdAt ?: entry.createdAt`, `updatedAt = now`) and then `dao.insert(...)` + `onAchievementEvent?.invoke(AchievementEvent.NoteCreated(normalized))`. Placed **inside `NoteStore`** because `onAchievementEvent` is private to it.
- [`YanjiRepository.kt`](file:///d:/AI项目/yanji/app/src/main/java/com/example/yanji/data/YanjiRepository.kt): new `suspend fun addOrUpdateNoteAndAwait(entry: NoteEntry) = noteStore.addOrUpdateAndAwait(entry)` next to the existing `addOrUpdateNote` delegate.
- `saveQuickNote` calls `repository.addOrUpdateNoteAndAwait(entry)`.
- **No double-write**: the in-memory list is still re-filled solely from the DAO `Flow` in `NoteStore.bind()`. `addOrUpdate` (the non-suspend UI path used by `NoteViewModel.saveNote`) is untouched.

---

## 2. Migration approach + schema JSON confirmation

**Approach — hand-written, matching `MIGRATION_19_20` exactly:**

```kotlin
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(connection: SQLiteConnection) {
        connection.exec("ALTER TABLE journal_entries ADD COLUMN sessionId TEXT")
    }
}
```

Red lines honoured:
- `migrate(connection: SQLiteConnection)` style (the repo's documented Room 2.8 KMP-safe override — see the long comment at `YanjiDatabase.kt:50-63`), **not** the deprecated `SupportSQLiteDatabase` variant.
- **No** `fallbackToDestructiveMigration()` anywhere.
- **No** `ALTER TABLE DROP COLUMN` (this is a pure `ADD COLUMN`, which is safe on all SQLite versions incl. minSdk 24).
- Nullable with **no default** → every pre-existing row is `NULL`. This is deliberate and asserted by tests: a note written before v21 cannot have had a session binding, so the migration does not invent one.

**Schema JSON:** `app/schemas/com.example.yanji.data.db.YanjiDatabase/21.json` was generated by Room's KSP schema export during the test build and is present. Verified consistent with the entity:

```
"version": 21
"createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `moodScore` INTEGER NOT NULL, `energyScore` INTEGER NOT NULL, `studySatisfaction` INTEGER NOT NULL, `tomorrowPlan` TEXT NOT NULL, `blockers` TEXT NOT NULL, `tags` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `isDraft` INTEGER NOT NULL, `sessionId` TEXT, PRIMARY KEY(`id`))"
```

`21.json` is untracked (`??`) — the orchestrator must `git add` it.

---

## 3. Tests (F13)

### New — [`TimerVocabularyTest.kt`](file:///d:/AI项目/yanji/app/src/test/java/com/example/yanji/bridge/TimerVocabularyTest.kt) (9 tests)

`sessionPhase` / `activeSessionMap` schema conformance across running, paused, countdown and stopwatch sessions: `phase ∈ {FOCUS,BREAK,IDLE}`, `mode ∈ {COUNTDOWN,STOPWATCH}`, never a `TimerPhase` enum name (`RUNNING`/`PAUSED`/`COMPLETED`/`CANCELLED`), never a Chinese mode name. Also asserts the companion constants forward to the single vocabulary source, and that a blank `taskId` normalises to `null`.

### New — [`BridgeMappersTest.kt`](file:///d:/AI项目/yanji/app/src/test/java/com/example/yanji/bridge/BridgeMappersTest.kt) (16 tests)

- `taskFields` emits real `actualMinutes` (5400s → 90), truncates sub-minute remainders (3599s → 59), and reports 0 only when nothing was invested.
- `noteFields` emits `sessionId: null` when unbound, the persisted id when bound, and normalises blank → null.
- `settingsFields` uses a **reflection guard** so re-adding `focusDurationMinutes` / `breakDurationMinutes` fails the build.
- `reviewStatsFields` / `rollingWindowDates`: window honours `days` exactly, no future keys, average divides by **window length** (120 min / 3 days = 40, not 60), `activeDays` uses the 30-minute boundary (1799s inactive, 1800s active), empty window reports zeros without fabricating, degenerate `days <= 0` coerced instead of dividing by zero, and window dates advance by local calendar (DST-safe).

### New — [`CollectFirstRemovedTest.kt`](file:///d:/AI项目/yanji/app/src/test/java/com/example/yanji/bridge/CollectFirstRemovedTest.kt) (5 tests)

Source-level guard that `collectFirst` / `FirstEmissionException` never come back. Strips block and line comments first, so the KDoc that documents the historical failure is not mistaken for live code. Also asserts all three `.first()` call sites, `addOrUpdateNoteAndAwait`, and that no fabricated values remain.

### Existing — `CollectFirstSemanticsTest.kt` (4 tests) still passes, unmodified.

### Extended — [`YanjiMigrationTest.kt`](file:///d:/AI项目/yanji/app/src/test/java/com/example/yanji/data/db/YanjiMigrationTest.kt) (25 tests, was 22)

- `CURRENT_VERSION = 20 → 21`; `allMigrations()` registers `MIGRATION_20_21`.
- New `insertNoteRowAt21(db, id, date, title, createdAt, sessionId)` helper binding `sessionId` (null → `bindNull`, so NULL is written rather than an empty string).
- `migrate20To21_addsNullableSessionIdColumnWithoutInventingBindings` — column exists, existing rows are NULL, both bound and unbound new rows round-trip.
- `migrate20To21_neverBindsExistingNotesToASession` — migration attributes nothing.
- `migrate19To21_chainLeavesBothNewColumnsNullableAndDataIntact` — the 19→20→21 chain leaves both `focus_sessions.taskId` and `journal_entries.sessionId` nullable with data intact, and both new write forms land afterwards.

---

## 4. Test results (actual output)

```
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 27s
26 actionable tasks: 6 executed, 20 up-to-date
```

Aggregated from `app/build/test-results/testDebugUnitTest/TEST-*.xml`:

```
TOTAL=375 FAILURES=0
com.example.yanji.bridge.BridgeMappersTest:       tests=16 failures=0 errors=0
com.example.yanji.bridge.CollectFirstRemovedTest: tests=5  failures=0 errors=0
com.example.yanji.bridge.CollectFirstSemanticsTest: tests=4 failures=0 errors=0
com.example.yanji.bridge.TimerVocabularyTest:     tests=9  failures=0 errors=0
com.example.yanji.data.db.YanjiMigrationTest:     tests=25 failures=0 errors=0 skipped=0
```

Baseline before this batch was 338 green; the suite is now 375 green (37 new: 30 bridge + 3 migration, plus the 4 pre-existing bridge tests that already existed).

---

## 5. Could not do / notes

- **`assembleDebug` not run** — per the build discipline, the orchestrator runs the full build. Only `:app:testDebugUnitTest` was executed.
- **`git add` / `commit` not performed.**
- No device interaction; no system light/dark mode change.

---

## 6. Cross-agent coupling the orchestrator must know

1. **`21.json` is untracked.** `app/schemas/com.example.yanji.data.db.YanjiDatabase/21.json` must be `git add`ed — §三.5 requires the schema to accompany the Entity change, and `YanjiMigrationTest` reads `app/schemas` from disk.
2. **`NoteEntry.sessionId` is a new domain field (default `null`).** Every Kotlin construction site uses named arguments, so this is source-compatible. However, `BackupTransfer` (`data/backup/BackupTransfer.kt:59`) round-trips notes through `NoteEntryEntity.fromDomainModel`, so backups written by v21 now also carry `sessionId` — older builds reading a v21 backup will ignore the column, which is safe.
3. **The `sessionId` bridge contract is `string | null`.** `saveQuickNote` now persists whatever `RecordMomentModal.tsx:56-64` resolves. If `fixer_ui_nav` (C1) adds another call site that passes `""` instead of `null`, the bridge will normalise it to `null` rather than persist an empty string — that is intentional, but the JS side should pass `null` for consistency with the type.
4. **`TimerMode` / `TimerPhase` are now live on the wire.** `FocusScreen.tsx` currently only reads `isCountdown` / `isPaused`; nothing in the RN tree consumes `mode`/`phase` yet, so the F2 change is invisible at runtime but now contract-conformant. `fixer_ts_contract`'s type change (`src/bridge/index.ts`) is the matching half.
5. **`getReviewStats` no longer uses the calendar week.** Any RN code assuming 7 Monday-aligned keys, or assuming `dailyFocusMinutes` covers Mon–Sun of the current week, will now see a rolling window instead. This is the intended F5 behaviour and matches `mock-bridge.js`.
6. **`actualMinutes` is now non-zero whenever focus sessions are linked to a task.** This interacts with F6/F7 (task → focus wiring): once `TodayScreen` passes `taskId` into `startFocus`, the value becomes meaningful rather than structurally pinned at 0.
