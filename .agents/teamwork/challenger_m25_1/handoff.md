# Challenger m25-1 — Adversarial Requirement Audit (M2–M5)

Commit under review: `4530352` (+ `9d6879d`, `2fbdbcc`)
Model directive: `space bunny`

---

## Observation

I read `ORIGINAL_REQUEST.md` (R1–R4 + 6 acceptance criteria), `AGENTS.md` §三, `PROJECT.md`,
and every file under review in full. I also traced the Kotlin side that the JS depends on
(`YanjiTimerModule.kt`, `YanjiDataModule.kt`, `FocusTimerService.kt`, `FocusModes`, `Entities.kt`,
schema v20) and the whole `test-e2e/` suite, because "did the user get what they asked for" cannot be
answered from the TSX files alone.

The architecture spine is genuinely correct and is the strongest part of the delivery: the timer's
physical authority stays in Kotlin (`SystemClock.elapsedRealtime` + `FocusTimerService` +
`ActiveSessionCoordinator`), JS only renders `onTimerTick` payloads, there is no `setInterval` /
`Date.now()`-driven timer anywhere in `src/`, all Room access goes through `YanjiRepository`, and
there is no mock/demo/sample data rendered as real in `src/`. Cards are zero-border, dark mode is
Midnight Blue (`#0D111A`, never `#000000`), radii come from `YanjiRadius`, and the tab bar has
exactly three destinations.

But the delivery is a **shell that renders data rather than a product that performs the requested
actions**. Four of the six acceptance criteria are met; the task loop and the timer-mode loop are
incomplete in ways that are user-visible on the first launch, and the "58/58 passed" E2E claim does
not survive inspection.

---

## Logic Chain

1. **The task-to-focus loop is cut in half.** `ORIGINAL_REQUEST.md` §02/§03 and the audit brief
   require "支持点击任务直接开始对应专注". In `TodayScreen.tsx` the only interactive element inside a
   task row is the checkbox (`TodayScreen.tsx:179-201`), which calls `toggleTask`. The row body has
   no `onPress`. The CTA's "继续学习 · {title}" branch (`TodayScreen.tsx:104-106`) does not start
   anything — it only calls `selectTab('focus')`. On the Focus screen, `handleStart` always passes
   `taskId = null` (`FocusScreen.tsx:106-112`) and never reads a task. So a task can be ticked off
   but can never drive a focus session from the RN UI, and `StudyTask.actualMinutes` can never move.
   The native `startFocus(subjectId, subjectName, mode, note, taskId)` already accepts `taskId` —
   the capability exists at the bridge and is simply never used.

2. **Task creation is absent, not deferred.** `createTask` and `deleteTask` are fully implemented
   natively (`YanjiDataModule.kt:109-136`, `:148-154`) and typed in the bridge
   (`src/bridge/index.ts:173`, `:181`), but a grep across `src/` shows **zero call sites**. Only
   `toggleTask` is wired. "提供轻量的添加、编辑和删除操作" is therefore unmet in the delivered UI,
   and the unused bridge surface proves it is an omission rather than a scoping decision.

3. **Stopwatch is unreachable.** `FocusScreen` has only `DURATION_PRESETS = [25,45,60,90]`
   (`FocusScreen.tsx:44`) and always builds the mode string as `` `${durationMinutes}分钟专注` ``
   (`FocusScreen.tsx:109`). `FocusModes.targetSeconds` parses "N 分钟" (`Models.kt:49-50`), so
   `targetDurationSeconds > 0`, so `isCountdown` is always `true` and `sessionPhase` always returns
   `"COUNTDOWN"` (`YanjiTimerModule.kt:222`, `:227`). There is no UI path that can ever produce
   `STOPWATCH`. The contract type `TimerMode = 'COUNTDOWN' | 'STOPWATCH'` (`src/bridge/index.ts:18`)
   is dead code in the RN layer, and "正计时/倒计时记忆切换" is unimplemented.

4. **"上次有效设置" is not restored.** `durationMinutes` is `useState(45)` and `selectedSubjectId`
   falls back to `selectable[0]` (`FocusScreen.tsx:53-54`, `:72`). Nothing reads a persisted
   preference, and `FocusPreferences.kt` only stores power-saving/timeout — not subject or duration.
   Worse, because `App.tsx:40-42` conditionally renders (`activeTab === 'today' ? <TodayScreen/> : null`),
   every tab switch unmounts the screen, so even in-session choices reset. This is the same root
   cause as defect #5.

5. **Tab switching loses in-progress input — confirmed, and reachable.** `RecordMomentModal` holds
   `text` in local state (`RecordMomentModal.tsx:42`). The modal is rendered *inside* the screen
   (`TodayScreen.tsx:231`, `FocusScreen.tsx:245`, `:304`), and `BottomTabBar` is a sibling of the
   screen container in `App.tsx:39-44`, so the tab bar stays visible under the modal's transparent
   backdrop. The user can switch tabs mid-draft; the screen unmounts; the draft is gone. This
   directly violates "切换页面不丢失临时输入". The same unmount resets `durationMinutes` /
   `selectedSubjectId` and the Review screen's `date` back to today. `NavigationShell.tsx:9-11`
   and `App.tsx:9-10` explicitly *claim* state is restored on tab switch — the comments assert the
   opposite of what the code does.

6. **Android back has no defined behaviour.** `BackHandler` appears nowhere in `src/`, and
   `MainActivity.kt` does not override back handling. Settings is React state, not a route
   (`NavigationShell.tsx:52`, `App.tsx:27-34`), so pressing system back on the Settings screen
   finishes the Activity and exits the app instead of returning to Today. Only the modal is safe,
   because RN's `Modal` maps back to `onRequestClose` (`RecordMomentModal.tsx:82`).

7. **The quick-note session binding is never persisted.** `saveQuickNote(content, date, sessionId)`
   (`YanjiDataModule.kt:165-184`) writes a `NoteEntry` — but `NoteEntryEntity` has **no `sessionId`
   column** (schema v20 `journal_entries`: id, date, title, content, moodScore, energyScore,
   studySatisfaction, tomorrowPlan, blockers, tags, createdAt, updatedAt, isFavorite, isDraft). The
   sessionId is only injected into the *response* map (`noteMap(entry, sessionId)`, `:182`); every
   read path calls `noteMap(it)` with the default `null` and emits `""` (`:425-431`,
   `getDailyTimeline` → `noteMap(it)`). So R3's "自动关联…正在进行的专注会话" holds only inside the
   save callback and silently disappears from Room and from every subsequent read. The bridge type
   `NoteEntry.sessionId: string` (`src/bridge/index.ts:54`) therefore reports a value that the
   database does not contain.

8. **The E2E suite is largely self-certifying.** No file under `test-e2e/` imports anything from
   `src/`. The "Feature 9 design tokens" test asserts against `test-e2e/contracts/theme-tokens.js`,
   whose palette (`dark.bgPrimary #0B132B`, `accentPrimary #48CAE4` / `#0284C7`) **does not match**
   the shipped `src/theme/tokens.ts` (`#0D111A`, `#356AE6` / `#4F7DF3`) — so the shipped tokens are
   never validated. `m3-design-navigation.test.js:66-75` and `m5-review-ai-e2e.test.js:74-101`
   assert against object literals the test itself defines (`navigationFlow`, `aiSlotConfig`,
   `e2eMetrics` with `facadeTestsPresent: false`) — they are facade tests by their own definition.
   Feature 14's "mode memory" test (`m4:79-93`) drives the **mock bridge** with `'STOPWATCH'`, which
   is exactly the path the real UI cannot reach. So "58/58 passed" is true but close to vacuous for
   UI behaviour; it is meaningful for the bridge contract and boundary logic.

9. **"最近 7 天" is actually "this calendar week".** `getReviewStats(days)` ignores its `days`
   argument and calls `getWeeklyStudySummary(0)` (`YanjiDataModule.kt:255-257`), which uses
   `YanjiTime.weekRange(0)` → Monday..Sunday. `ReviewScreen.tsx:216-248` renders all seven bars, so
   on any day other than Sunday the trend shows future days as `0 分` and omits the days before
   this week's Monday. `dailyAverageMinutes` is computed natively (`:274`) and typed
   (`src/bridge/index.ts:70`) but never rendered.

10. **A fabricated user setting is surfaced as real.** `settingsMap` hardcodes
    `focusDurationMinutes = 45` and `breakDurationMinutes = 10` (`YanjiDataModule.kt:440-441`).
    `UserSettings` has neither field. `SettingsScreen.tsx:120-133` renders it as
    "单次专注时长 45 分钟". This is a hardcoded value presented to the user as their own persisted
    preference — a direct hit on AGENTS.md §三.3 (零假数据) and R4 ("严禁在正式界面注入虚假 Mock /
    演示数据"), even though it is a single constant rather than a demo dataset.

---

## Caveats

- I did not run the build, tests, or typecheck; I audited source only. GATE_STATUS.md reports
  typecheck 0 errors, `:app:testDebugUnitTest` pass, `assembleDebug` success, and 58/58 E2E. I
  treat those as claimed, not verified.
- AGENTS.md §二.3 / §三.11 forbid me from operating the device, so I have **no runtime evidence**.
  Findings about state loss, back behaviour, and safe-area overlap are derived from code paths and
  RN semantics, not from observed device behaviour.
- `git status` shows `.gitignore` modified and `node_modules/`, `.agents/teamwork/orchestrator_2/`
  untracked; none are source files and none affect this audit.
- Clause 7 (immersive focus hides bottom nav) and clause 25 (exam mode) are **not present in
  `ORIGINAL_REQUEST.md`** — they come only from the reviewer brief. I judge them below, but they are
  not violations of the authoritative requirement document.
- Subjective clause 32 is my honest read from code, not from a rendered screen.

---

## Conclusion

**REQUEST_CHANGES**

The native-authority spine, the design-token compliance, the three-tab IA and the settings entry
are correct, and I would approve those in isolation. But the user asked for a working task→focus
loop, a stopwatch/countdown switch, task add/edit/delete, preserved transient input across tab
switches, predictable Android back, and review statistics including daily average and task
completion. Four of those are absent and one is actively broken. The E2E suite that is supposed to
gate these covers none of the RN UI code. That is not an acceptable deferral set — it is the
difference between a demo and the product that was requested.

---

## Requirement Coverage Table

| # | Clause | Status | Evidence |
|---|--------|--------|----------|
| 1 | Exactly three top-level pages | **MET** | `NavigationShell.tsx:27-31` (`TABS` = 3), `App.tsx:40-42`, `BottomTabBar.tsx:31` |
| 2 | Settings via Today top-right | **MET** | `TodayScreen.tsx:129-136` (⚙ → `openSettings`) |
| 3 | Record Moment cross-page, no tab | **PARTIAL** | Reachable from Today (`TodayScreen.tsx:222-228`) and Focus (`FocusScreen.tsx:236-242`, `:295-301`); **not from Review** — `ReviewScreen.tsx` never imports it |
| 4 | Only three primary destinations | **MET** | `NavigationShell.tsx:27-31`, `BottomTabBar.tsx:31-58` |
| 5 | Nav state restorable, no input loss | **MISSING** | `App.tsx:40-42` unmounts screens; `RecordMomentModal.tsx:42` draft lost on tab switch; `FocusScreen.tsx:53-54` resets; `ReviewScreen.tsx:52` date resets. Comments at `NavigationShell.tsx:9-11` / `App.tsx:9-10` assert the opposite |
| 6 | Timer decoupled from nav UI | **MET** | Authority in Kotlin; no `setInterval`/`Date.now()` timer in `src/`; `FocusScreen.tsx:78` renders `onTimerTick` only |
| 7 | Immersive focus hides bottom nav | **MISSING** | `BottomTabBar` unconditionally rendered (`App.tsx:44`); no hide logic anywhere. Judged **acceptable deferral** — clause absent from ORIGINAL_REQUEST.md |
| 8 | Predictable Android back | **MISSING** | No `BackHandler` in `src/`; `MainActivity.kt:9-35` no override. Back on Settings (`App.tsx:27-34`, React state not a route) exits the app |
| 9 | Layer 1: date + restrained countdown + today total | **MET** | `TodayScreen.tsx:120-157`; countdown hidden when `daysRemaining <= 0`, small tertiary type |
| 10 | Adaptive single CTA (3 labels) | **PARTIAL** | Labels exist (`TodayScreen.tsx:99-108`) but "继续学习"/"继续专注" only `selectTab('focus')` — no task/subject binding |
| 11 | Tap task starts its focus | **MISSING** | `TodayScreen.tsx:179-201` — checkbox only; row body has no `onPress`; `FocusScreen.tsx:110` always `taskId: null` |
| 12 | Lightweight add / edit / delete task | **MISSING** | `createTask`/`deleteTask` bridged (`src/bridge/index.ts:173,181`; `YanjiDataModule.kt:109,148`) with **zero call sites in `src/`** |
| 13 | No forced planning before focus | **MET** | `TodayScreen.tsx:166-167` empty state "可以直接开始专注，不必先做计划" |
| 14 | Quiet Record Moment entry | **MET** | `TodayScreen.tsx:222-228` |
| 15 | ≤2 clicks cold-open → running timer | **MET** | Today CTA (1) → Focus "开始专注" (2) |
| 16 | No check-in / achievement / banner clutter | **MET** | No such elements in `TodayScreen.tsx` |
| 17 | First-run / no-record / has-record / focusing / task states | **MET** | Empty states `TodayScreen.tsx:167`, `ReviewScreen.tsx:134`; focusing via `activeSession` (`TodayScreen.tsx:100-102`); completed/uncompleted via `task.completed` (`:198-208`) |
| 18 | Focus prep: subject + mode selector + duration + start | **PARTIAL** | Subject (`FocusScreen.tsx:160-194`), duration (`:196-226`), start (`:228-234`) present; **no stopwatch/countdown selector** — countdown is forced (`:109` → `Models.kt:49-50` → `YanjiTimerModule.kt:222,227`) |
| 19 | Restore last valid settings | **MISSING** | `FocusScreen.tsx:53-54` hardcoded `45` / first subject; no persistence; unmount on tab switch compounds it |
| 20 | Complex config collapsed | **MET** | Prep screen is subject chips + duration presets only |
| 21 | Running state: big digits + subject + task + pause + end + record | **PARTIAL** | Digits (`:262-273`), subject (`:258-260`), pause/resume (`:280-283`), 结束/放弃 (`:286-293`), record (`:295-301`) present; **"当前关联任务" absent** (no taskId ever bound) |
| 22 | COMPLETED / CANCELLED / countdown-complete semantics | **MET** | `completeTimer`→`completeByUser` (`FocusTimerService.kt:341-372`), `discardTimer`→`discard` (`:454-470`), `onCountdownFinished` (`:316-338`) all distinct and correct |
| 23 | Record Moment never auto-pauses | **MET** | `RecordMomentModal.tsx:46-74` — no `pauseTimer` call anywhere |
| 24 | Light success feedback / preserve input on failure | **MET** | Success closes + `onSaved` (`:66-67`); failure keeps text and shows error (`:68-70`) |
| 25 | Exam mode as Focus sub-mode | **MISSING** | No exam UI; no `startExamSession` bridge method at all. Display only (`ReviewScreen.tsx:163`). Judged **acceptable deferral** — clause absent from ORIGINAL_REQUEST.md |
| 26 | Review defaults to today | **MET** | `ReviewScreen.tsx:52` `useState(todayIso)` |
| 27 | Browse any past day | **MET** | `ReviewScreen.tsx:110-129` prev/next; no upper clamp (future reachable) — minor |
| 28 | Day contains records / distribution / notes / diary / exams | **MET** | `ReviewScreen.tsx:137-207`; `isExam` badge `:163` |
| 29 | 7-day trend / daily average / subject distribution / task completion | **PARTIAL** | Trend (`:216-250`) and subject distribution (`:253-270`) present; **日均专注时长 missing** (available at `YanjiDataModule.kt:274`, typed `src/bridge/index.ts:70`, never rendered); **任务完成情况 missing** from stats. Also trend is calendar-week not rolling 7 days (`YanjiDataModule.kt:255-257` → `weekRange`) |
| 30 | No streak / leaderboard / achievements as main content | **MET** | None in `ReviewScreen.tsx` |
| 31 | AI expansion slot, no fake AI, no dead button | **MET** | No AI UI in `src/` |
| 32 | Modern / restrained / simple / refined | **MET** (subjective) | Consistent token use, zero-border cards, `tabular-nums` timer (`FocusScreen.tsx:269`), single accent. Deduct: `⚙` / `‹ ›` / `＋` are raw glyphs, not icons |
| 33 | Unified animations + reduce-motion support | **MISSING** | Zero `Animated` / `LayoutAnimation` / Reanimated / `Easing` in `src/`; only `Modal animationType="fade"` (`RecordMomentModal.tsx:82`). `reanimated ^4.7.1` is installed but unused. No `AccessibilityInfo` reduce-motion check anywhere |
| 34 | Adapt: sizes / system bars / gesture nav / keyboard / themes / font scale / TalkBack | **PARTIAL** | Theme + `StatusBar barStyle` handled (`App.tsx:30,38`); `accessibilityRole`/`State`/`Label` on interactive elements; `adjustResize` in manifest. Gaps: no `react-native-safe-area-context` (no insets → overlap risk), no `maxFontSizeMultiplier` / `allowFontScaling` (64pt timer under large font scale), no reduce-motion |
| 35 | Typed Native Module, no bypassing Kotlin Repository | **MET** (spot-check) | `startFocus`→`YanjiRepository.startFocus`+`FocusTimerService` (`YanjiTimerModule.kt:93-110`); `saveQuickNote`→`repository.addOrUpdateNote` (`:180`); `createTask`→`repository.saveStudyTask` (`:132`); no direct DAO access from JS |
| 36 | Zero fake data | **VIOLATION** | `settingsMap` hardcodes `focusDurationMinutes = 45`, `breakDurationMinutes = 10` (`YanjiDataModule.kt:440-441`) and `SettingsScreen.tsx:120-133` renders them as the user's own settings. Otherwise clean — no mock/demo/sample data anywhere in `src/` |

---

## Prioritized Required Changes

### P0 — Hard requirement violations (must fix)

1. **Make tapping a task start that task's focus.** Give the task row an `onPress` that navigates to
   Focus with the task's `subjectId` / `subjectName` / `plannedMinutes` / `taskId` preselected, and
   pass `taskId` through `YanjiTimerNative.startFocus(...)`. Fix `TodayScreen.tsx:104-106` so the
   "继续学习 · {title}" CTA does the same instead of a bare `selectTab('focus')`.
   *(clauses 10, 11, 21)*

2. **Add task create / edit / delete to the RN UI.** `createTask` and `deleteTask` are already
   bridged and unused — wire them. Without this, first-run users can never create a task at all.
   *(clause 12; R4 "首次使用（无任务）快速开始" is met, but "添加、编辑和删除" is not)*

3. **Stop the hardcoded settings leak.** Remove `focusDurationMinutes`/`breakDurationMinutes` from
   `settingsMap` (`YanjiDataModule.kt:440-441`) or source them from real persisted settings; do not
   render a constant as the user's preference. *(AGENTS.md §三.3, R4)*

4. **Persist the quick-note ↔ session association.** Either add a `sessionId` column to
   `journal_entries` via a hand-written `migrate()` (no `fallbackToDestructiveMigration`, no
   `ALTER TABLE DROP COLUMN`) and commit the schema JSON, or drop the `sessionId` field from the
   bridge contract so the UI stops claiming an association Room does not store. The current state
   returns a value on save that reads back as `""`. *(R3)*

5. **Keep screens mounted / hoist transient state so tab switching loses nothing.** Mount all three
   screens and toggle visibility, or lift the Record Moment draft (and Focus prep selections, and
   Review's date) above the screen boundary. Also remove the now-false comments at
   `NavigationShell.tsx:9-11` and `App.tsx:9-10`. *(clause 5)*

### P1 — Material gaps

6. **Add the stopwatch / countdown selector and persist the last-used subject + duration.** Without
   it, `TimerMode` is dead in the RN layer and clause 19 is unmet. *(clauses 18, 19)*

7. **Handle Android back explicitly.** `BackHandler` in `AppShell`: when `settingsOpen`, close
   settings instead of letting the Activity finish. *(clause 8)*

8. **Complete the Review statistics.** Render `dailyAverageMinutes` and a task-completion summary;
   decide whether "最近 7 天" should be a rolling 7-day window (it currently returns this calendar
   week, including future zero days). *(clause 29)*

9. **Fix the E2E suite's integrity problem.** Make the design-token test import
   `src/theme/tokens.ts` instead of the divergent `test-e2e/contracts/theme-tokens.js`, make the
   navigation test import `TABS` from `src/navigation/NavigationShell.tsx`, and delete the
   self-asserting facade tests (`aiSlotConfig`, `e2eMetrics`, hardcoded `navigationFlow`). Add at
   least one test that exercises the real task-row → `startFocus(taskId)` path that the mock bridge
   currently papers over. *(acceptance criterion "E2E Test Suite Pass")*

### P2 — Documented deferrals (record and move on)

10. Immersive-focus bottom-nav hiding (clause 7) — absent from `ORIGINAL_REQUEST.md`.
11. Exam mode on the Focus screen (clause 25) — absent from `ORIGINAL_REQUEST.md`; note that no
    `startExamSession` bridge exists, so it is unreachable by construction, not merely unbuilt.
12. Motion system + reduce-motion support (clause 33) — zero animation exists. Worth a follow-up
    ticket: the brief explicitly asks for unified duration/easing and reduce-motion support, and
    `react-native-reanimated` is already a dependency.
13. Safe-area insets and font-scale clamping (clause 34) — add `react-native-safe-area-context` and
    `maxFontSizeMultiplier` on the 64pt timer before shipping to devices with gesture nav and large
    font settings.
14. Record Moment entry on the Review screen (clause 3) — "ideally" in the brief; Today + Focus
    coverage is arguably sufficient for this round.