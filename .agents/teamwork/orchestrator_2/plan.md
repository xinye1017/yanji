# Orchestration Plan — Retrospective Gate Review for M2–M5

## Background

The teamwork multi-agent system completed Milestone 1 and was interrupted by an
API 429 rate limit during the M1 gate review. The remaining milestones
(M2 Native Bridge, M3 Design System & Navigation, M4 Today/Focus/Record Moment,
M5 Review View) were subsequently implemented by a **single agent** without
multi-agent adversarial review.

The M1 gate proved the value of that review: `reviewer_m1_2` independently
caught a critical dark-mode cold-launch crash
(`values-night/themes.xml` missing AppCompat/Material3 inheritance) that the
implementing agent had missed.

This orchestration runs a **retrospective gate** over the already-delivered
M2–M5 code so it receives the same adversarial scrutiny M1 received.

## Commit Under Review

- `4530352` — "feat: Milestone 2-4 — 原生桥接层、设计系统与三大核心页面"
- `9d6879d` — "feat: Milestone 1 — React Native 0.87.1 Add-to-App 基建工程与 E2E 测试框架"
- `2fbdbcc` — "fix: 修复 values-night/themes.xml 深色模式冷启动崩溃并补充 E2E 守卫"

## Files Under Review

### Kotlin (native bridge)
- `app/src/main/java/com/example/yanji/bridge/YanjiTimerModule.kt`
- `app/src/main/java/com/example/yanji/bridge/YanjiDataModule.kt`
- `app/src/main/java/com/example/yanji/bridge/YanjiThemeModule.kt`
- `app/src/main/java/com/example/yanji/bridge/YanjiPackage.kt`
- `app/src/main/java/com/example/yanji/YanjiApplication.kt`

### TypeScript / React Native
- `src/bridge/index.ts`
- `src/theme/tokens.ts`
- `src/theme/ThemeProvider.tsx`
- `src/navigation/NavigationShell.tsx`
- `src/navigation/BottomTabBar.tsx`
- `src/components/YanjiUI.tsx`
- `src/components/RecordMomentModal.tsx`
- `src/screens/TodayScreen.tsx`
- `src/screens/FocusScreen.tsx`
- `src/screens/ReviewScreen.tsx`
- `src/screens/SettingsScreen.tsx`
- `App.tsx`

### Config
- `tailwind.config.js`
- `tsconfig.json`

## Reviewer Assignments (adversarial separation of concerns)

| Agent | Role | Focus |
|---|---|---|
| `reviewer_m25_1` | reviewer | **Native bridge correctness & authority boundary**: does JS implement a second timer? Does the data path bypass `YanjiRepository`? Are thread/dispatch choices safe? Is `getActiveSession` truthful after process death? |
| `reviewer_m25_2` | reviewer | **Design system & constraint compliance**: Midnight Blue / no `#000000`, zero-border cards, `YanjiRadius` tokens only, no raw `Color(0x…)` or bare `rounded-*` literals, light/dark parity. |
| `challenger_m25_1` | challenger | **Adversarial requirement audit against the original brief**: strict 3 tabs, settings not a tab, Record Moment must not pause the timer, no fake/mock data, no un-clickable AI UI, statistics must come from real data. |
| `challenger_m25_2` | challenger | **Failure-mode & lifecycle attack**: concurrent session rejection, discard vs complete semantics, keyboard-avoidance layout jumps, note-save failure preserving input, tab switch during a running timer, theme write path failure. |
| `auditor_m25_1` | auditor | **Forensic integrity**: git diff scope discipline, no secrets crossing into JS, no destructive migration, no unrelated in-flight files committed, AGENTS.md red lines. |

## Verification Commands (every reviewer runs these independently)

```bash
npm run typecheck
.\gradlew.bat assembleDebug
.\gradlew.bat :app:testDebugUnitTest
node test-e2e/runner.js
```

## Output

Each agent writes `handoff.md` into its own directory with:
- Observation
- Logic Chain
- Caveats
- Conclusion with explicit verdict: `APPROVE` or `REQUEST_CHANGES`
- Verification Commands & Results

The orchestrator aggregates into `GATE_STATUS.md`. Any `REQUEST_CHANGES`
verdict is a hard blocker: the fix must be applied and re-verified before the
retrospective gate can pass.
