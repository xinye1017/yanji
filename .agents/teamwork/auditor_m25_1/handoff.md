# Forensic Integrity Audit — M2–M5 Retrospective Gate (Auditor 1)

- **Auditor**: `auditor_m25_1`
- **Scope**: process integrity & red-line compliance (AGENTS.md §三, §七, §七.1)
- **Date**: 2026-10-08
- **Commits under review**: `4530352` (M2–M4), `9d6879d` (M1), `2fbdbcc` (fix)

## Verdict: **CLEAN**

All four hard red lines (credential leak, migration safety, zero fake data, system theme)
pass. Commit hygiene passes with one non-blocking hygiene gap (`node_modules/` is not
covered by any ignore rule). Documentation is accurate on M1–M4 completion; one stale
test count in `TEST_READY.md`. No violations of AGENTS.md Active Constraints were found.

---

## 1. Observation

### 1.1 Git history under review

```
4530352 feat: Milestone 2-4 — 原生桥接层、设计系统与三大核心页面
9d6879d feat: Milestone 1 — React Native 0.87.1 Add-to-App 基建工程与 E2E 测试框架
2fbdbcc fix: 修复 values-night/themes.xml 深色模式冷启动崩溃并补充 E2E 守卫
```

`HEAD` == `origin/main` == `453035288b30a072f47a1720853e2a5f55345aa9` — work is pushed
(§七.1 closed for the reviewed commits). No stash entries, no unrelated active branch.

### 1.2 Working tree state

```
 M .gitignore
?? .agents/teamwork/orchestrator_2/
?? node_modules/
```

- `.gitignore` — single-line addition `.freebuff/`, **unstaged, not committed** in any of
  the three reviewed commits (`git show <sha> --name-only` confirms no `.gitignore` entry).
- `.agents/teamwork/orchestrator_2/` — untracked gate-review scratch (GATE_STATUS.md,
  plan.md) belonging to this retrospective gate itself.
- `node_modules/` — 18,427 untracked files, **0 tracked** (`git ls-files` count = 0).

### 1.3 Commit scope

`4530352` = 21 files / +2,771 / −19, every one of them inside the M2–M4 blast radius:
`App.tsx`, `PROJECT.md`, `app/src/main/assets/index.android.bundle`,
`YanjiApplication.kt`, four new `bridge/*.kt` modules, `src/bridge/index.ts`,
`src/{components,navigation,screens,theme}/**`, `tailwind.config.js`, `tsconfig.json`.

`9d6879d` = M1 infra (RN/Gradle wiring, bundle pipeline, PROJECT.md blueprint,
`test-e2e/**`, `gradle/verification-metadata.xml`) plus the `.agents/teamwork/**`
process artefacts of the M1 teamwork run.

`2fbdbcc` = `values-night/themes.xml` + one new E2E guard assertion file.

No cross-contamination between commits: no M2–M4 file appears in `9d6879d`, no
`.agents/**` file appears in `4530352`.

---

## 2. Logic Chain

### 2.1 Credential red line (§三.4)

1. `src/**/*.{ts,tsx}` grep for `apiKey|api_key|secret|token|Keystore|credential`
   (case-insensitive) → **0 hits**. The only matches are the filename `tokens.ts`
   (design tokens) and `YanjiRadius` imports.
2. `app/src/main/java/com/example/yanji/bridge/**/*.kt` grep for the same set →
   **0 hits**. No bridge module references an AI credential at all.
3. `YanjiDataModule.updateUserSettings` (lines 335–345) copies exactly four fields —
   `targetExamDate`, `dailyGoalHours`, `validStudyThresholdMinutes`, `themeMode`.
   There is **no code path** by which JS can write `aiApiKey`; the field is not in the
   `ReadableMap` contract and not in `settingsMap()`.
4. `src/bridge/index.ts` `UserSettings` interface = `examDate`, `targetSchool`,
   `targetMajor`, `focusDurationMinutes`, `breakDurationMinutes`, `themePreference`.
   No credential field exists in the TS contract.
5. Defence in depth confirmed unchanged and pre-existing:
   `UserSettingsEntity.fromDomainModel` hard-codes `aiApiKey = ""`, so even a
   hypothetical in-memory key can never reach Room; `BackupModels.UserSettingsBackup`
   deliberately omits the key (documented at `BackupModels.kt:19`);
   `YanjiDatabase.migration7to8` already dropped the `user_settings.aiApiKey` column
   in favour of `SecretStore` in `noBackupFilesDir`.
6. `git show 4530352` scanned for `sk-|AIza|apiKey|api_key|secret|password|Bearer|token`
   → no credential-shaped string. The only `SECRET` hit in the shipped
   `index.android.bundle` is React's internal
   `__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED` symbol — a framework constant,
   not a credential.
7. No `console.log` / `console.debug` anywhere in `src/**` (count = 0), so no
   accidental log leakage channel from the RN side.
8. `androidTest/.../BackupTransferInstrumentedTest.kt` asserts `aiApiKey` is **absent**
   from exported JSON and forces `assertEquals("", settings.aiApiKey)` after restore —
   an existing guard, untouched by M2–M5.

### 2.2 Migration safety red line (§三.5)

1. `git grep fallbackToDestructiveMigration` → **1 hit, and it is a KDoc comment**
   at `YanjiDatabase.kt:619` stating the call is deliberately omitted. No invocation.
2. `git grep "DROP COLUMN"` in `YanjiDatabase.kt` → only explanatory comments
   (lines 126, 220, 384, 497) describing why the create-copy-drop-rename pattern is
   used on `minSdk 24`. No executed `DROP COLUMN` statement.
3. `YanjiDatabase.kt:27` → `version = 20`. `app/schemas/**` last modified at
   `877b12c` (pre-M2); `git diff HEAD -- app/schemas` = empty. M2–M5 changed **zero**
   Room entities, therefore **zero** schema JSONs were required and none are missing.
4. Migration chain intact: `MIGRATION_1_2 … MIGRATION_19_20` (19 migrations) all
   registered via `.addMigrations(*migrations(appContext))`, no fallback.

### 2.3 Zero fake data red line (§三.3)

1. `src/**` grep for `const <NAME>` containing `SAMPLE|MOCK|DEMO|DUMMY|FAKE|SEED|PRESET`
   → single hit: `DURATION_PRESETS = [25, 45, 60, 90]` in `FocusScreen.tsx` — a UI
   duration picker option list, not business data. Second array literal found anywhere
   is `['日','一','二','三','四','五','六']` (weekday glyphs).
2. Grep for `sample|mock|dummy|placeholder data|hardcoded` across `src/**` → **0 hits**.
3. Empty states are genuine and reachable: `TodayScreen` "今天还没有任务",
   `ReviewScreen` "这一天还没有记录" / "暂无统计数据",
   `SettingsScreen` swallows bridge failure and leaves the panel empty rather than
   inventing values (explicit comment at line 35).
4. `SubjectCatalog.defaults` (`Models.kt:160`) is pre-existing Kotlin factory seed data
   used by `YanjiDatabase.onCreate`, already reviewed and allowlisted in
   `scripts/runtime-fixture-allowlist.txt` with owner + removal criteria. It is
   distinct from JS-side fabrication, which does not exist.
5. `scripts/check-runtime-fixtures.sh` **exists**. It could not be executed in this
   environment (the Windows `bash` launcher fails with
   `HCS_E_SERVICE_NOT_AVAILABLE`), so I replicated its three regex rules directly with
   `git grep` over `app/src/main/**/*.kt` → **0 unreviewed matches**, and the allowlist
   itself still matches the current `val seeds = listOf(` line. Equivalent to PASS.

### 2.4 System theme red line (§二.6)

1. `src/**` and `app/src/main/java/**` grep for
   `UiModeManager|setApplicationNightMode|setNightModeOverride|ui_night_mode`
   → **0 code hits**; the single match is the KDoc in `YanjiThemeModule.kt:21`
   documenting the prohibition.
2. `YanjiThemeModule` behaviour: `getThemePreference` /
   `setThemePreference` touch only `user_settings.themeMode` via
   `YanjiRepository.updateSettings`. `isSystemDark()` **reads**
   `resources.configuration.uiMode` and never writes it. `SettingsScreen` renders the
   user-facing statement "研迹只读取系统深浅色，不会修改你的系统设置。" — which the code
   substantiates.
3. No ADB-level theme command is proposed anywhere in the reviewed commits.

### 2.5 Forbidden-artifact check (§七 decision table)

`git ls-files` (436 entries) filtered for
`node_modules|local.properties|\.freebuff|\.db$|\.jks$|\.keystore$|\.pem$|device-backup|^build/`
→ **0 matches**. None of the three commits introduce any of them. `node_modules` is
tracked 0 files.

`app/src/main/assets/index.android.bundle` (1.7 MB) *is* committed — this is intentional
and load-bearing: it is Feature 2 "Offline Standalone JS Bundle", the offline APK
assembly input. It is not a `build/` artifact. Treated as compliant.

---

## 3. Caveats

1. **`scripts/check-runtime-fixtures.sh` not executed.** The Windows bash launcher is
   broken in this environment (`HCS_E_SERVICE_NOT_AVAILABLE`). I substituted an
   equivalent `git grep` replication of its three rules plus a manual allowlist
   comparison. If the gate requires the literal script to pass, it must be re-run on a
   machine with a working WSL/Git-Bash.
2. **No build/typecheck run by this auditor.** `node test-e2e/runner.js` was run here and
   returned **58/58 PASS** (independently reproducing the commit-message claim).
   `npm run typecheck`, `assembleDebug` and `:app:testDebugUnitTest` were *not* executed
   by me — I rely on the recorded pre-review baseline in
   `.agents/teamwork/orchestrator_2/GATE_STATUS.md`. My verdict covers process and
   red-line integrity, not build success.
3. **`node_modules/` is not covered by any `.gitignore`.** The root `.gitignore` has no
   `node_modules` entry and neither does `app/.gitignore`; `git check-ignore` returns
   exit 1 for `node_modules/...`. Nothing was committed (0 tracked files), but 18,427
   untracked entries sit in `git status` output, which is one `git add .` away from a
   catastrophic commit. This is a hygiene gap, not a committed violation.
4. **Pre-existing bridge value-fidelity issues** surfaced during the audit but belong to
   `reviewer_m25_1`'s correctness scope. Recorded here as cross-reference only:
   `settingsMap()` hard-codes `focusDurationMinutes = 45` / `breakDurationMinutes = 10`
   instead of reading real preferences (so `SettingsScreen` shows a fabricated "45 分钟");
   `taskMap()` hard-codes `actualMinutes = 0` despite a real value existing in the
   domain model; `getReviewStats(days)` ignores its `days` argument and always returns
   the current-week summary, so the Review screen's "7-day" label is not what the
   bridge computes. None of these violate a §三 red line, but they mean some RN surfaces
   display non-authoritative constants.
5. **TEST_READY.md test count is stale** (see Findings). It documents 57/57 total with
   Tier 1 = 41/41; the actual suite is 58/58 with Tier 1 = 42/42 after `2fbdbcc` added
   the `values-night/themes.xml` guard. The doc was written in `9d6879d` and never
   refreshed. This is a documentation-accuracy defect, not an overstated milestone claim.

---

## 4. Conclusion

**CLEAN.**

Every AGENTS.md hard red line holds: no credential crosses into JS, Room, preferences,
logs, fixtures, backups or the shipped bundle; no destructive migration and no
`DROP COLUMN`; Room schema stays at v20 with no schema drift; no fabricated business data
in the RN screens, with genuine empty states instead; and nothing anywhere writes the
system light/dark mode. Commit hygiene is disciplined — `4530352` touches only M2–M4
files, `.gitignore`'s `.freebuff/` addition stayed uncommitted, and no forbidden artifact
type entered the repository. `PROJECT.md`'s M1–M4 completion marks are accurate against
the code.

Three non-blocking WARNs are recorded with remediation below; none is a red-line
violation, so they do not flip the verdict to `VIOLATIONS_FOUND`.

---

## 5. Findings Table

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | `4530352` contains only M2–M4-relevant files | **PASS** | 21 files, all inside bridge/design/screen scope; no cross-milestone bleed |
| 2 | `9d6879d` / `2fbdbcc` scope discipline | **PASS** | M1 infra + E2E only; `2fbdbcc` = themes.xml + one guard test |
| 3 | `.gitignore` `.freebuff/` addition NOT committed | **PASS** | ` M .gitignore` in working tree only; no `.gitignore` in any of the 3 commits' `--name-only` |
| 4 | No `local.properties` / `*.db` / `*.jks` / `*.keystore` / `*.pem` / `device-backup/` / `build/` committed | **PASS** | `git ls-files` (436) filtered → 0 matches |
| 5 | `node_modules/` not committed | **PASS** | 0 tracked files under `node_modules/` |
| 6 | `node_modules/` covered by an ignore rule | **WARN** | No `node_modules` entry in root or `app/.gitignore`; `git check-ignore` exit 1; 18,427 untracked entries in `git status` |
| 7 | `app/schemas/**` consistent with committed code | **PASS** | `version = 20`; `git diff HEAD -- app/schemas` empty; last touched `877b12c` (pre-M2); M2–M5 changed no Room entity |
| 8 | No AI credential in `src/**` | **PASS** | grep `apiKey|api_key|secret|token|Keystore|credential` → 0 hits (only `tokens.ts` filename) |
| 9 | No AI credential in Kotlin bridge | **PASS** | grep over `bridge/**/*.kt` → 0 hits |
| 10 | `updateUserSettings` cannot write an API key | **PASS** | `YanjiDataModule.kt:335-345` copies only 4 non-credential fields |
| 11 | No credential field in TS bridge contract | **PASS** | `src/bridge/index.ts` `UserSettings` = examDate/targetSchool/targetMajor/focusDurationMinutes/breakDurationMinutes/themePreference |
| 12 | `4530352` free of credential-shaped strings | **PASS** | `sk-|AIza|apiKey|Bearer|token` → none; bundle's only `SECRET` is React's `__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED` |
| 13 | Credential not in Room / backup (existing guards intact) | **PASS** | `UserSettingsEntity.fromDomainModel` forces `aiApiKey=""`; `BackupModels` omits key; instrumented test asserts absence |
| 14 | No log-leak channel from RN side | **PASS** | `console.log|console.debug` in `src/**` → 0 |
| 15 | `fallbackToDestructiveMigration()` absent | **PASS** | sole grep hit is the KDoc at `YanjiDatabase.kt:619` stating it is deliberately omitted |
| 16 | No `ALTER TABLE DROP COLUMN` introduced | **PASS** | only comments (lines 126/220/384/497) explaining the create-copy-drop-rename workaround for minSdk 24 |
| 17 | Room schema version unchanged at 20 | **PASS** | `YanjiDatabase.kt:27`; 19 migrations registered, chain 1→20 complete |
| 18 | No fabricated business data in `src/**` | **PASS** | no SAMPLE/MOCK/DEMO/DUMMY/FAKE/SEED constants; no `sample|mock|dummy` strings at all |
| 19 | Genuine empty states rendered | **PASS** | "今天还没有任务" / "这一天还没有记录" / "暂无统计数据"; Settings leaves panel empty on bridge failure |
| 20 | `SubjectCatalog.defaults` = legitimate factory seed | **PASS** | pre-existing Kotlin default catalog, allowlisted in `scripts/runtime-fixture-allowlist.txt` with owner + removal criteria |
| 21 | `scripts/check-runtime-fixtures.sh` present & equivalent rules clean | **PASS (with caveat)** | script exists; execution blocked by broken Windows bash launcher (`HCS_E_SERVICE_NOT_AVAILABLE`); equivalent `git grep` replication → 0 unreviewed matches |
| 22 | No system light/dark mode write anywhere | **PASS** | `UiModeManager\|setApplicationNightMode\|setNightModeOverride\|ui_night_mode` → 0 code hits |
| 23 | `YanjiThemeModule` reads uiMode / writes app preference only | **PASS** | `isSystemDark()` reads `resources.configuration.uiMode`; writes go to `user_settings.themeMode` via `YanjiRepository.updateSettings` |
| 24 | `PROJECT.md` M1–M4 completion accurate | **PASS** | M2 bridge modules, M3 tokens/nav, M4 screens all present in `4530352`; "47 JVM unit test classes" verified (47 files); "Room v20 / 19 migrations / zero fallback" verified |
| 25 | No stale/overstated claims in `PROJECT.md` | **PASS** | milestones 5 and Final remain PLANNED and are genuinely unimplemented |
| 26 | `TEST_READY.md` accuracy | **WARN** | documents 57/57 with Tier 1 = 41/41; live runner reports 58/58 with Tier 1 = 42/42 (guard test added in `2fbdbcc`, doc never refreshed) |
| 27 | Working tree contains only expected in-flight items | **WARN** | ` M .gitignore` (`.freebuff/`, unrelated to M1–M5, correctly uncommitted) + untracked gate scratch + 18k untracked `node_modules` |
| 28 | Work pushed to remote (§七.1) | **PASS** | `HEAD` == `origin/main` == `4530352` |
| 29 | No destructive git state | **PASS** | `git stash list` empty; no reset/clean/checkout performed during audit; audit itself was read-only |
| 30 | Bridge value fidelity (cross-reference, not a red line) | **WARN** | `settingsMap()` hard-codes 45/10; `taskMap()` hard-codes `actualMinutes=0`; `getReviewStats(days)` ignores its argument — see Caveat 4, owned by `reviewer_m25_1` |

---

## 6. Prioritized Remediation List (non-blocking, verdict remains CLEAN)

1. **[Hygiene, high value / low effort] Add `node_modules/` to `.gitignore`.**
   Currently 18,427 untracked files sit one `git add .` away from a repository-breaking
   commit. Include the existing `.freebuff/` line in the same commit so the in-flight
   change is finally closed out. Do **not** fold this into an M5 feature commit; keep it
   an isolated chore commit per §七.1.
2. **[Correctness, cross-referenced to `reviewer_m25_1`] Remove fabricated constants from
   the bridge mappings.** `settingsMap()` should return real
   `focusDurationMinutes`/`breakDurationMinutes` (or omit the fields), `taskMap()` should
   return the real `actualMinutes`, and `getReviewStats(days)` should honour its `days`
   argument instead of always returning the current week. These make RN surfaces display
   values that are not authoritative — not a §三 violation, but a genuine data-fidelity
   defect in the M2 contract.
3. **[Documentation] Refresh `TEST_READY.md`** to the real 58/58 / Tier 1 = 42/42 totals
   produced by `node test-e2e/runner.js`, so the published readiness matrix matches the
   suite that `2fbdbcc` extended.
4. **[Process] Re-run `scripts/check-runtime-fixtures.sh`** on an environment with a
   working bash launcher (WSL or Git Bash) so the zero-fake-data guard is executed by the
   real script rather than an equivalent replication, and record the result in the gate
   status.
