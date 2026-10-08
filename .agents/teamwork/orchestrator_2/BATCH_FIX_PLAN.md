# Teamwork Batch Fix — M2–M5 Gate Remediation (18 items)

User decisions (2026-10-08T23:55Z):
1. **F3 = Option A** — add `sessionId` column to `NoteEntryEntity` + hand-written
   `MIGRATION_20_21` + commit `app/schemas/**`. Do NOT drop the contract field.
2. **Scope = all 18 items in one batch**, then a single verification + commit + push.

Gate authority: `.agents/teamwork/orchestrator_2/GATE_STATUS.md` (verdicts + evidence).

---

## File ownership map (STRICT — no agent edits a file it does not own)

| Agent | Owns | Fixes |
|---|---|---|
| **A** Kotlin Bridge & Migration | `app/src/main/java/com/example/yanji/bridge/**`, `data/Entities.kt`, `data/Models.kt`, `data/db/YanjiDatabase.kt`, `data/note/NoteStore.kt`, `data/YanjiRepository.kt`, `app/schemas/**`, `app/src/test/java/com/example/yanji/bridge/**`, `app/src/test/java/com/example/yanji/data/db/YanjiMigrationTest.kt` | F1, F2(Kotlin), F3, F4, F5(Kotlin), F8(Kotlin), F10, F13 |
| **B** TS Contract & E2E | `src/bridge/index.ts`, `test-e2e/**`, `PROJECT.md`, `TEST_READY.md`, `.gitignore` | F2(TS), F3(TS), F5(TS), F8(TS), F14, F18 |
| **C1** App / Nav / Today / Focus | `App.tsx`, `src/navigation/**`, `src/screens/TodayScreen.tsx`, `src/screens/FocusScreen.tsx` | F6, F7, F9, F15, F16, F17 |
| **C2** Review / Settings / Components / Theme | `src/screens/ReviewScreen.tsx`, `src/screens/SettingsScreen.tsx`, `src/components/**`, `src/theme/**` | F5(render), F8(render), F11, F12 |

Cross-agent coupling is **type-level only** (no shared files), so all four run
concurrently. The orchestrator runs the final verification and the single commit.

---

## Binding decisions (all agents must honour these — they are the contract)

### ActiveSessionState (F2)
- `phase`: `'FOCUS' | 'BREAK' | 'IDLE'` — exactly these three. `FOCUS` whenever a
  session exists (running **or** paused; `isPaused` carries pause separately).
  `BREAK` is reserved for a future break phase and is never emitted yet.
  `IDLE` only when there is no session (i.e. `getActiveSession()` returns `null`).
- `mode`: `'COUNTDOWN' | 'STOPWATCH'`, derived from `targetDurationSeconds > 0`.
- Never emit `TimerPhase` enum names (`RUNNING`/`PAUSED`/`COMPLETED`/`CANCELLED`)
  nor raw Chinese `FocusModes` names (`正向计时`) across the bridge.
- `onTimerTick.phase` uses the **same** vocabulary as `ActiveSessionState.phase`.
- TS `TimerPhase` = `'FOCUS' | 'BREAK' | 'IDLE'`; `TimerMode` = `'COUNTDOWN' | 'STOPWATCH'`.

### StudyTask (F4)
- `actualMinutes` = real accumulated seconds from
  `YanjiRepository.observeTaskActualSeconds()` (`Map<taskId, seconds>`), divided by 60.
  Never a constant.

### NoteEntry (F3, F10)
- `sessionId: string | null`. `null` when the note is not bound to a focus session.
  Never `""`.
- The binding is **persisted** (new Room column), so every read path returns it —
  not just the save callback.
- `saveQuickNote` resolves only after the Room write has landed.

### ReviewStats (F5)
- `days` is honoured. Window = `YanjiTime.lastDaysRange(days)` — rolling N days
  including today, **no future-dated zero bars**, no calendar-week clipping.
- Keep `dailyAverageMinutes` and `activeDays` (both now become contract fields).

### UserSettings (F8)
- **Remove** `focusDurationMinutes` and `breakDurationMinutes` entirely. `UserSettings`
  (domain) has no such fields, so any value would be fabricated (§三.3 zero fake data).
- Removed from: Kotlin `settingsMap`, TS `UserSettings`, `bridge-schema.js`
  `isValidUserSettings`, `mock-bridge.js` defaults, and `SettingsScreen` rendering.

### Theme tokens (F11, F12)
- Add `onAccent` per mode: light `#FFFFFF` (matches `YanjiOnPrimary`), dark `#0D111A`
  (matches `YanjiDarkOnPrimary`). Replace the 4 hardcoded `#FFFFFF` on-accent
  foregrounds with `theme.colors.onAccent`.
- Semantic light colors adopt Compose's retired-replacement values:
  `success #17784F`, `warning #9A6100`, `danger #BE3232` (per `Color.kt:105-111`).
- Dark semantic colors stay as-is (they already match Compose).

---

## Hard rules (from AGENTS.md — non-negotiable)

- **No `git commit`, no `git add`, no `git push`.** The orchestrator does one commit.
- **No device interaction**: no `adb`, no `screencap`, no `input tap/swipe`,
  no `uiautomator dump` (§二.3, §三.11).
- **Never modify system light/dark mode** (§二.6).
- **No destructive git ops**: no `checkout -- .`, `reset --hard`, `clean -fd`, `stash`.
- **Do not touch the user's in-flight `.gitignore` `.freebuff/` line.** Agent B adds
  `node_modules/` as a separate line and preserves everything else.
- **Zero fake/mock runtime data** (§三.3). Room remains the only business truth source.
- **No `fallbackToDestructiveMigration()`**; migration must be hand-written
  `migrate(connection: SQLiteConnection)`; no `ALTER TABLE DROP COLUMN`;
  `app/schemas/**` must accompany the Entity change (§三.5).
- Build discipline: only `assembleDebug` / targeted `testDebugUnitTest`. Never
  `gradlew build`. Never routine `clean`. Windows: always `.\gradlew.bat`.
- No AI credential may cross into JS, Room, logs or fixtures.

## Verification (orchestrator runs after all agents report)

```
npm run typecheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat assembleDebug
node test-e2e/runner.js
npm run bundle:android
```

Then: precise `git add` per file, one Conventional Commits commit, `git push`.
