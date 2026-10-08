# fixer_design_system — Handoff

## BLOCKING CONSTRAINT (read first)

This subagent runs **read-only**: it has `view_file`, `run_command` (read-only
exploration only), `search_web`, `send_message`, `schedule`, `manage_task`. There
is **no file-edit tool**. Therefore **none of the six owned files were modified**.

Verified before and after: `git status --short -- src/` is **empty**.

Consequence: the F11 / F12 / F5 / F8 / F18 code changes below are **fully specified
and verified against the real sources but NOT applied**. The orchestrator (or an
editor-capable agent) must apply them. Everything needed is in this document,
including exact before/after snippets, so application is mechanical.

Baseline captured (before any change, exit code 0):

```
> yanji@1.0.0 typecheck
> tsc --noEmit

(no errors)
```

Re-run `npm run typecheck` after applying the patch; expect the same clean result.

---

## F11 — missing `onAccent` token

### Authority / measured contrast (recomputed from the shipped hex values)

| pairing | ratio | WCAG 1.4.3 (4.5:1) |
|---|---|---|
| light `#FFFFFF` on `#356AE6` | **4.82:1** | pass |
| dark `#FFFFFF` on `#4F7DF3` (current RN hardcode) | **3.78:1** | **FAIL** |
| dark `#0D111A` on `#4F7DF3` (`YanjiDarkOnPrimary`) | **5.00:1** | pass |

`Color.kt:205-212` documents the same conclusion for all five mascot themes:
white on the raised dark primary measures 2.56–3.78:1; the background-level dark
ink measures 5.00 / 6.04 / 6.95 / 7.05 / 7.38. So dark `onAccent = #0D111A` is not
an arbitrary pick — it is the Compose-adjudicated value, and it is also
`YanjiDarkBackground`, i.e. reuse of an existing Midnight-Blue token rather than a
new colour.

### Change 1 — `src/theme/tokens.ts`

Add to the **light** palette, after `accentSoft`:

```ts
    /**
     * Foreground on `accentPrimary` (Compose: YanjiOnPrimary #FFFFFF).
     * Measured 4.82:1 on #356AE6 — passes WCAG 1.4.3.
     */
    onAccent: '#FFFFFF',
    /**
     * Modal / sheet scrim. RN-local: Compose has no scrim token (Material
     * `scrim` is only used as a shadow colour in GlassBottomBar.kt:220), so
     * this is a deliberate RN decision. Needs on-device confirmation.
     */
    scrim: 'rgba(0,0,0,0.32)',
```

Add to the **dark** palette, after `accentSoft`:

```ts
    /**
     * Foreground on `accentPrimary` (Compose: YanjiDarkOnPrimary #0D111A).
     * Deliberately dark, not white: the dark accent is raised to #4F7DF3 for
     * legibility on the Midnight-Blue background, and white on it measures only
     * 3.78:1 (Color.kt:205-212 documents 2.56–3.78:1 across the five mascot
     * themes). #0D111A measures 5.00:1 and is the existing background token.
     */
    onAccent: '#0D111A',
    /**
     * Heavier scrim than light mode: a 0.32 black veil composites to ~#090C11
     * on the #0D111A background, which barely separates the sheet. 0.56 gives
     * the floating layer real presence. RN-local decision (no Compose source).
     */
    scrim: 'rgba(0,0,0,0.56)',
```

### Change 2 — replace the four hardcoded `#FFFFFF` on-accent foregrounds

`src/components/YanjiUI.tsx:77`

```diff
-          color: disabled ? theme.colors.textDisabled : '#FFFFFF',
+          color: disabled ? theme.colors.textDisabled : theme.colors.onAccent,
```

`src/components/RecordMomentModal.tsx:152`

```diff
-              <Text style={{ color: '#FFFFFF', fontSize: 15, fontWeight: '600' }}>
+              <Text style={{ color: theme.colors.onAccent, fontSize: 15, fontWeight: '600' }}>
```

`src/screens/SettingsScreen.tsx:89`

```diff
-                      color: active ? '#FFFFFF' : theme.colors.textSecondary,
+                      color: active ? theme.colors.onAccent : theme.colors.textSecondary,
```

`src/screens/FocusScreen.tsx:216` — **NOT MINE, relay required** (see below).

### Change 3 — tokenize the modal scrim

`src/components/RecordMomentModal.tsx:88`

```diff
-          backgroundColor: 'rgba(0,0,0,0.32)',
+          backgroundColor: theme.colors.scrim,
```

After the patch, `grep -rn "FFFFFF" src/` must return only `tokens.ts` (where the
`#FFFFFF` literals are the token definitions themselves, plus the doc comment).

---

## F12 — semantic colours regressed to retired values

### Authority

`Color.kt:105-111` retires `#2F9E6D` / `#E67E22`-class values and documents the
measured failures. Recomputed against the shipped hexes:

| token | RN value (retired) | white | page `#F2F4F7` | Compose value | white | page |
|---|---|---|---|---|---|---|
| success | `#2F9E6D` | 3.37:1 | 3.06:1 | `#17784F` | **5.47:1** | **4.97:1** |
| warning | `#E67E22` | 2.85:1 | 2.59:1 | `#9A6100` | **5.14:1** | **4.67:1** |
| danger | `#D64545` | 4.38:1 | 3.97:1 | `#BE3232` | **5.69:1** | **5.16:1** |

All three retired values fail 4.5:1 on at least one of the two light surfaces;
all three replacements pass both. `Color.kt` records the same numbers (3.37 / 2.64
/ 4.15) — the warning figure differs because `Color.kt` retires `#D99024` while the
RN tree shipped `#E67E22`; both fail, so the replacement is unaffected.

Dark semantics are already Compose-conformant and were re-verified (do not touch):

| token | on bg `#0D111A` | on card `#151B28` | on float `#222C40` |
|---|---|---|---|
| `#34D399` | 9.82 | 8.96 | 7.27 |
| `#FBBF24` | 11.31 | 10.32 | 8.37 |
| `#F87171` | 6.83 | 6.23 | 5.05 |

### Change — `src/theme/tokens.ts`, light palette

```diff
-    /** Semantic colors. */
-    success: '#2F9E6D',
-    warning: '#E67E22',
-    danger: '#D64545',
+    /**
+     * Semantic colours — the "text/icon" tier, so they are set for >=4.5:1.
+     * Authority: app/src/main/java/com/example/yanji/theme/Color.kt:105-111,
+     * which RETIRED the previous values (#2F9E6D / #E67E22 / #D64545) with
+     * measured failures (success 3.37, warning 2.64, danger 4.15).
+     * Measured here: #17784F 5.47/4.97, #9A6100 5.14/4.67, #BE3232 5.69/5.16
+     * (white card / page #F2F4F7). Do not drift these back.
+     */
+    success: '#17784F',
+    warning: '#9A6100',
+    danger: '#BE3232',
```

---

## F5 — Review renders the now-official stats

### Change — `src/screens/ReviewScreen.tsx`

1. Import the task type:

```diff
-import type { DailyTimeline, ReviewStats } from '../bridge';
+import type { DailyTimeline, ReviewStats, StudyTask } from '../bridge';
```

2. Add state for the selected day's tasks:

```diff
   const [timeline, setTimeline] = useState<DailyTimeline | null>(null);
   const [stats, setStats] = useState<ReviewStats | null>(null);
+  const [dayTasks, setDayTasks] = useState<StudyTask[]>([]);
```

3. Fetch them in `refresh` (they already come from a real bridge call; no new
   native surface is needed):

```diff
       const [day, review] = await Promise.all([
         YanjiDataNative.getDailyTimeline(targetDate),
         YanjiDataNative.getReviewStats(7),
       ]);
       setTimeline(day);
       setStats(review);
+      setDayTasks(await YanjiDataNative.getTodayTasks(targetDate));
```

   `getTodayTasks(date)` is date-scoped (`repository.observeStudyTasks(date)`,
   `YanjiDataModule.kt:101`), so it is correct for a back-dated Review date, not
   just today.

4. Derived count (no invented numbers):

```tsx
  const completedTaskCount = useMemo(
    () => dayTasks.filter(task => task.completed).length,
    [dayTasks]
  );
```

5. Render `日均专注时长` as the first row inside the existing `最近 7 天` card,
   before the `trendEntries.map(...)` block:

```tsx
            <View
              style={{
                flexDirection: 'row',
                justifyContent: 'space-between',
                alignItems: 'baseline',
                paddingBottom: YanjiSpacing.md,
              }}
            >
              <Text style={{ color: theme.colors.textSecondary, fontSize: 13 }}>
                日均专注时长
              </Text>
              <Text style={{ color: theme.colors.textPrimary, fontSize: 15, fontWeight: '600' }}>
                {stats ? formatMinutes(stats.dailyAverageMinutes * 60) : '—'}
              </Text>
            </View>
```

   `formatMinutes(seconds)` already exists in this file; `dailyAverageMinutes` is
   an integer emitted by Kotlin (`YanjiDataModule.kt:274`).

6. Render task completion as its own restrained section, scoped to the selected
   date. Place it after the `科目分布` block:

```tsx
        {dayTasks.length > 0 ? (
          <>
            <YanjiSectionHeader title="任务完成" />
            <YanjiCard>
              <View
                style={{
                  flexDirection: 'row',
                  justifyContent: 'space-between',
                  alignItems: 'baseline',
                  paddingVertical: 6,
                }}
              >
                <Text style={{ color: theme.colors.textSecondary, fontSize: 13 }}>
                  已完成
                </Text>
                <Text style={{ color: theme.colors.textPrimary, fontSize: 15, fontWeight: '600' }}>
                  {completedTaskCount} / {dayTasks.length}
                </Text>
              </View>
            </YanjiCard>
          </>
        ) : null}
```

Restraint notes (brief §05 compliance):
- No streak, leaderboard, achievement or goal framing. `activeDays` exists on the
  contract but is deliberately **not** rendered — it is the denominator of the
  average and adding it invites goal/streak framing that §05 forbids.
- Zero-task days render nothing rather than a `0 / 0` card, so the screen does not
  grow a stats-dashboard feel for first-run users.
- No new colour, radius or spacing literal; all values come from `theme.colors`
  and `YanjiRadius` / `YanjiSpacing`.

---

## F8 — remove fabricated settings rendering

### Change — `src/screens/SettingsScreen.tsx`

Delete the whole `默认专注` block, header and card (current lines 120-133):

```tsx
        <YanjiSectionHeader title="默认专注" />
        <YanjiCard>
          <Text style={{ color: theme.colors.textSecondary, fontSize: 13 }}>单次专注时长</Text>
          <Text
            style={{
              color: theme.colors.textPrimary,
              fontSize: 17,
              fontWeight: '600',
              marginTop: 4,
            }}
          >
            {settings ? `${settings.focusDurationMinutes} 分钟` : '—'}
          </Text>
        </YanjiCard>
```

No replacement row. `UserSettings` (domain, `Models.kt:333-355`) has no
focus/break duration, so any value would be fabricated (§三.3). The
`settings` state is still used for `examDate`, so the `useState`/`refresh` plumbing
stays. This removal is safe **before** Agent B's type change lands — it deletes the
only reference to those two fields, so it can never be the cause of a typecheck
error.

### Other stale claims in that screen — audited, none found beyond the above

- `研迹只读取系统深浅色，不会修改你的系统设置。` — **true and must stay**:
  `YanjiThemeModule` writes only `user_settings.themeMode`, and `ThemeProvider.tsx`
  never calls any system-level API (§二.6).
- `目标考试日期` / `未设置` — truthful, sourced from `settings.examDate`.
- `返回今天` button label — **flag, do not change**: it is accurate today because
  Settings is only reachable from the Today header. If C1's F16 makes Settings a
  real route reachable from other tabs, the label becomes wrong. Owner: C1.

---

## F18 — accessibility + NativeWind decision

### Change — `src/components/RecordMomentModal.tsx` footer buttons

```diff
-            <Pressable onPress={handleClose} style={{ paddingVertical: 10, paddingHorizontal: 16 }}>
-              <Text style={{ color: theme.colors.textSecondary, fontSize: 15 }}>取消</Text>
+            <Pressable
+              onPress={handleClose}
+              accessibilityRole="button"
+              accessibilityLabel="取消"
+              style={{ paddingVertical: 10, paddingHorizontal: 16 }}
+            >
+              <Text style={{ color: theme.colors.textSecondary, fontSize: 15 }}>取消</Text>
             </Pressable>
             <Pressable
               onPress={handleSave}
               disabled={saving}
+              accessibilityRole="button"
+              accessibilityLabel={saving ? '保存中' : '保存'}
               style={{
```

### NativeWind recommendation (do NOT act on this unilaterally)

**Recommendation: remove NativeWind, do not adopt it.**

Facts verified:
- `className` appears **0 times** in `src/**` and `App.tsx`, against **114**
  `style={{...}}` objects.
- The toolchain *is* fully wired and therefore fully paid for:
  `babel.config.js` (nativewind/babel preset + `jsxImportSource: 'nativewind'`),
  `metro.config.js` (`withNativeWind`, `input: './global.css'`), `global.css`
  (three `@tailwind` directives), `nativewind-env.d.ts`, and the `nativewind@^4.0.1`
  + `tailwindcss@^3.4.17` dependencies.
- `tailwind.config.js` duplicates `tokens.ts` (28 colours + radius + spacing) and
  **nothing reads it** — `test-e2e/contracts/theme-tokens.js` has its own separate,
  already-stale palette, and no test reads `tailwind.config.js`.

Reasoning against adopting it:

1. **It cannot express Yanji's theme semantics.** NativeWind's `dark:` variant is
   driven by the OS colour scheme. Yanji's theme is driven by the persisted app
   preference `user_settings.themeMode` (`YanjiThemeModule`), which
   `ThemeProvider.tsx:54-55` deliberately allows to diverge from the system
   (§二.6 keeps "app preference" and "system uiMode" as separate inputs). Making
   `dark:` correct would require a custom NativeWind `colorScheme` bridge — an
   abstraction layer with no current requirement, which the engineering principles
   forbid ("choose the simplest implementation that fully satisfies the current
   requirements").
2. **The existing approach already satisfies every red line** and is type-checked:
   every colour comes from `theme.colors`, every radius from `YanjiRadius`, cards
   are zero-border, and the dark palette is Midnight Blue with no `#000000`.
   reviewer_m25_2 returned APPROVE on the design system apart from the four
   non-blocking follow-ups.
3. **The duplicate has already caused the exact failure it invites.**
   `tailwind.config.js:21-23` still carries the retired `#2F9E6D` / `#E67E22` /
   `#D64545` values that F12 is removing from `tokens.ts`. Two sources of truth for
   design tokens is precisely the drift §三.8 ("事实不复制") exists to prevent, and
   it already bit once.
4. **Deletion is cheaper than conversion** and is a net reduction: 114 call sites,
   6 files, and dynamic values (percentage widths, `disabled`/`pressed`-conditional
   colours) that `className` handles poorly.

Concretely, if the user agrees: delete `tailwind.config.js`, `global.css`,
`nativewind-env.d.ts`; drop `nativewind/babel` and `jsxImportSource` from
`babel.config.js`; drop `withNativeWind` from `metro.config.js`; drop `nativewind`
and `tailwindcss` from `package.json`. Note `nativewind` is currently listed under
`dependencies`, not `devDependencies`, so it ships in the app graph — removing it
also shrinks the production bundle.

**`tailwind.config.js` is not mine to edit — this is a report, not a change.**
The user must adjudicate; it is an architecture decision, not a defect fix.

---

## FocusScreen change to relay to C1

`src/screens/FocusScreen.tsx:216`, inside the duration-preset pill:

```diff
-                      color: active ? '#FFFFFF' : theme.colors.textSecondary,
+                      color: active ? theme.colors.onAccent : theme.colors.textSecondary,
```

Rationale: identical defect to SettingsScreen:89 and RecordMomentModal:152 — white
on the raised dark accent `#4F7DF3` measures 3.78:1. `theme.colors.onAccent`
resolves it once the F11 token lands. It is the last of the four hardcoded
`#FFFFFF` on-accent foregrounds.

---

## Cross-agent coupling

1. **F8 → Agent A / Agent B.** My removal is independent, but the batch must also
   strip `focusDurationMinutes` / `breakDurationMinutes` from the Kotlin
   `settingsMap` (`YanjiDataModule.kt:440-441`), the TS `UserSettings`
   (`src/bridge/index.ts:104-105`), `bridge-schema.js` `isValidUserSettings`
   (`:85-86`) and `mock-bridge.js` (`:35-36`).
   **Latent breakage to flag:** `mock-bridge.js:65` reads
   `this.userSettings.focusDurationMinutes * 60` to seed the countdown
   `remainingSeconds`. If the default is deleted without replacing that read, every
   `startFocus` in the E2E suite gets `NaN` remaining seconds. Agent B owns it.
2. **F5 → Agent A.** My render depends on `dailyAverageMinutes` surviving as a
   contract field (the plan says keep it). Confirmed still emitted at
   `YanjiDataModule.kt:274`. Agent A's F5 rewrite switches the window to
   `YanjiTime.lastDaysRange(days)`; the average's denominator then becomes the
   rolling N days rather than the calendar week, which is the correct semantics for
   a `最近 7 天` label. No change needed on my side.
3. **F11 → C1.** `FocusScreen.tsx:216` (above) is the only cross-file dependency.
4. **F18 → Agent B.** `test-e2e/contracts/theme-tokens.js` still asserts a palette
   that matches neither the old nor the new `tokens.ts` (`#0B132B`, `#48CAE4`,
   `#0F172A`, `#0284C7`) and omits the `28` radius step. It is owned by Agent B and
   is unaffected by my change, but it must be fixed in the same batch or the
   contract stays decorative.
5. **No test breakage from my changes.** No test in `test-e2e/` reads anything
   under `src/` (GATE_STATUS reviewer_m25_2 #5), and
   `tier1-features/m3-design-navigation.test.js` reads `YanjiThemeTokens` from
   `contracts/theme-tokens.js`, not from `tokens.ts`. `npm run typecheck` is clean
   before the patch; re-verify after.

---

## UI red-line audit of the six owned files (post-patch state)

| rule | status |
|---|---|
| no raw hex at call sites outside `tokens.ts` | satisfied — the only remaining `#FFFFFF` literals are the token definitions in `tokens.ts` |
| no `#000000` in the dark palette | satisfied — dark scrim is `rgba(0,0,0,0.56)` (a veil, not a surface colour); the Power-Saving OLED `#000000` in Compose is intentionally not ported |
| no bare radius literals | satisfied — all 6 files use `YanjiRadius.*` only (verified by grep: zero numeric `borderRadius` literals) |
| cards zero border in both modes | satisfied — `YanjiCard` still has no `borderWidth`/`borderColor`; no `BorderStroke` anywhere in `src/` |
| dark backgrounds stay Midnight Blue | satisfied — `#0D111A` / `#151B28` / `#1D2536` / `#222C40` untouched |
| never modify system light/dark | satisfied — `ThemeProvider.tsx` untouched, still read-only |

`scripts/check-design-tokens.sh` guards only `app/src/main/java/com/example/yanji/ui`,
so none of these TS changes can trip it.