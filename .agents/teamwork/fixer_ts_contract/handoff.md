# handoff — fixer_ts_contract

Scope: TypeScript bridge contracts, test-e2e/**, PROJECT.md, TEST_READY.md, .gitignore.

> [!IMPORTANT]
> **RESOLVED — write access was granted mid-task.** All files below were copied into the real tree
> and both gates were re-verified there (see section 3). Originally I ran read-only: every write
> attempt under the real project tree was refused, so work was staged and byte-verified in a
> replica at
> `C:\Users\dex\.gemini\antigravity-cli\brain\043e022e-44bb-420d-82b3-f41a1bc2fd4a\scratch\replica`.
> Section 1 is now a record of what was applied, not a to-do list.

## 1. Files applied from the replica to the real tree

| Replica path (relative to replica root) | Real destination |
|---|---|
| `src/bridge/index.ts` | `src/bridge/index.ts` |
| `test-e2e/contracts/bridge-schema.js` | same relative path |
| `test-e2e/contracts/mock-bridge.js` | same relative path |
| `test-e2e/contracts/theme-tokens.js` | same relative path |
| `test-e2e/tier1-features/m2-native-bridge.test.js` | same relative path |
| `test-e2e/tier1-features/m3-design-navigation.test.js` | same relative path |
| `test-e2e/tier1-features/m5-review-ai-e2e.test.js` | same relative path |
| `test-e2e/tier1-features/m8-shipped-tokens.test.js` (**new file**) | same relative path |
| `test-e2e/tier2-boundaries/boundaries.test.js` | same relative path |
| `test-e2e/tier4-workflows/workflows.test.js` | same relative path |
| `test-e2e/runner.js` | `test-e2e/runner.js` |
| `PROJECT.md` | `PROJECT.md` |
| `TEST_READY.md` | `TEST_READY.md` |

All replica files are LF-only with no BOM (verified by byte inspection). Do not convert line endings.

`src/screens/SettingsScreen.tsx` and `src/theme/tokens.ts` were re-synced from the real tree into the
replica for verification only — they are not part of my ownership and must NOT be copied back.

## 2. Per-file changes

### src/bridge/index.ts
- **F2**: TimerPhase is now FOCUS | BREAK | IDLE — the bridge no longer defines a third phase vocabulary (PAUSED). TimerMode stays COUNTDOWN | STOPWATCH. TimerTickEvent.phase reuses TimerPhase.
- **F3**: NoteEntry.sessionId is `string | null`, documented as never an empty string.
- **F8**: removed focusDurationMinutes and breakDurationMinutes from UserSettings (the Kotlin domain model has no such fields).

### test-e2e/contracts/bridge-schema.js
- isValidNoteEntry: sessionId must be null or a non-empty string.
- isValidReviewStats: additionally requires dailyAverageMinutes and activeDays to be numbers.
- isValidUserSettings: requires targetSchool / targetMajor strings and rejects payloads still carrying focusDurationMinutes / breakDurationMinutes.

### test-e2e/contracts/mock-bridge.js
- ACTIVE_DAY_THRESHOLD_MINUTES = 30 header constant.
- Default settings: removed targetScore / focusDurationMinutes / breakDurationMinutes; added targetSchool, targetMajor, defaultCountdownSeconds.
- startFocus: planned seconds derive from the mode (mirrors FocusModes.targetSeconds), not from settings.
- getReviewStats(days) rewritten as a rolling N-day window oracle with _isoDateAt(epochMs, deltaDays) (local-calendar, DST-safe), activeDays and dailyAverageMinutes.

### test-e2e/contracts/theme-tokens.js
- Palette synchronised to the shipped token values; radius scale gained xxl: 28.

### Test suites
- m2-native-bridge.test.js: settings test now exercises targetMajor and asserts the removed duration fields are absent.
- m3-design-navigation.test.js: dark background #0D111A, loop rejecting #000000 / #000 / BLACK across all dark tokens; allowedRadius includes 28 and asserts radius.xxl === 28; mock settings use targetSchool / targetMajor.
- m5-review-ai-e2e.test.js: added the BridgeSchemas import; new describes Feature 18b (3 tests) and Feature 18c (2 tests).
- m8-shipped-tokens.test.js (new, 7 tests): imports the real src/theme/tokens.ts via pathToFileURL + dynamic import() (Node 22 native type-stripping) and asserts no #000000 in dark, onAccent in both modes, card zero-border, radius scale membership and the retired-replacement semantic colours.
- boundaries.test.js and workflows.test.js: updated for the removed settings fields / defaultCountdownSeconds.
- runner.js: registers registerShippedTokenTests().

### PROJECT.md
- saveQuickNote signature documents sessionId?: string | null.
- New "Shared type contracts" subsection: TimerPhase / TimerMode vocabularies, NoteEntry.sessionId nullability, UserSettings field list (explicitly no duration fields), and the full ReviewStats field list with rolling-window semantics.

### TEST_READY.md
- Results block and coverage matrix updated to the measured totals: 70/70 with Tier 1 = 54/54.

### .gitignore
- Added exactly one line node_modules/ immediately after the in-flight .freebuff/ line. `git check-ignore -v node_modules` exits 0. No other bytes touched.

## 3. Verification (actual output)

```text
node test-e2e/runner.js
 Tier 1    : 54/54 passed (100.0%) [Failed: 0, Skipped: 0]
 Tier 2    : 9/9 passed (100.0%) [Failed: 0, Skipped: 0]
 Tier 3    : 4/4 passed (100.0%) [Failed: 0, Skipped: 0]
 Tier 4    : 3/3 passed (100.0%) [Failed: 0, Skipped: 0]
 Total Tests: 70   Passed: 70   Failed: 0   Skipped: 0
 Result: ALL TESTS PASSED (100% SUCCESS)

node node_modules/typescript/bin/tsc -p tsconfig.json --noEmit   -> exit 0
```



Both commands were re-run against the REAL tree after applying the files, with identical results:

``text
node test-e2e/runner.js   -> 70/70, Tier 1 54/54, exit 0
npm run typecheck        -> exit 0, 0 errors
``

Real-tree pre-change baseline (captured before any edit): npm run typecheck 0 errors; node test-e2e/runner.js 58/58 with Tier 1 42/42.

## 4. Cross-agent coupling

1. **Agent A (Kotlin)** — sessionPhase() must emit FOCUS / IDLE (and keep FOCUS for a paused countdown) to match the TS TimerPhase. settingsMap() must stop emitting focusDurationMinutes / breakDurationMinutes.
2. **Agent C2 (theme)** — m8-shipped-tokens.test.js asserts the post-change values (onAccent in both modes, light #17784F / #9A6100 / #BE3232, radius xxl: 28). At the time I verified, the real src/theme/tokens.ts already carried those values and the suite was green after syncing it into the replica.
3. **Agent C2 / SettingsScreen** — any rendering of settings.focusDurationMinutes must be removed, otherwise tsc fails with TS2339 once the field is dropped from UserSettings. The real SettingsScreen.tsx no longer renders it.

## 5. Note on the plan stated numbers

BATCH_FIX_PLAN.md quotes 58/58 with Tier 1 = 42/42 for the live runner. That figure is pre-addition:
the new contract tests raise the suite to 70/70 with Tier 1 = 54/54. I report the measured numbers.
