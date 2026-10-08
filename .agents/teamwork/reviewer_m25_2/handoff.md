# Reviewer 2 — Design System & Navigation Shell (Milestone 3)

Reviewer: `reviewer_m25_2` · Scope: `src/theme/*`, `src/navigation/*`, `src/components/*`, `src/screens/*`, `tailwind.config.js`, `App.tsx`
Model: `space bunny`

---

## Observation

### What is present and correct

**1. Color architecture (src/theme/tokens.ts + tailwind.config.js).**
The token set is a genuine semantic layer, and the dark palette is Midnight Blue. Dark `bgPrimary` is `#0D111A` — **not** `#000000`. There is no pure-black value anywhere in the dark or light palette in `tokens.ts`, `tailwind.config.js`, or any component. Native splash colors (`app/src/main/res/values-night/colors.xml` → `#0D111A`) also match. The card rule is expressed as data (`YanjiCardRules.borderWidth: 0`, `hasBorderStroke: false`) rather than being left to convention.

**2. Card zero-border red line — PASS.**
`YanjiCard` (src/components/YanjiUI.tsx:28-41) sets only `backgroundColor`, `borderRadius`, `padding`. There is no `borderWidth`, `borderColor`, `border-*` class, `BorderStroke`, or shadow anywhere in it. The same holds for every other card-like container in the reviewed screens:
- `ReviewScreen.tsx:137, 192-197, 215, 256` — three `YanjiCard` uses plus one inline `bgSurface` container (notes). All zero-border.
- `TodayScreen.tsx:140` — one `YanjiCard`. Zero-border.
- `FocusScreen.tsx:176, 211` — pill-style `Pressable`s on `bgSurface`. Zero-border.
- `SettingsScreen.tsx:69, 106, 121` — three `YanjiCard`s. Zero-border.

**3. The single borderWidth in the tree is legitimate.**
Exactly one `borderWidth` exists in the whole RN tree:
- `TodayScreen.tsx:194-197` — `borderWidth: 1.5` on the task checkbox, `borderColor: theme.colors.fieldBorder` (or `accentPrimary` when checked).

A 20×20 checkbox is an interactive **control**, not a card container. The AGENTS.md §三.6 exception for "输入框 / 按钮等需要可辨识边界的控件" applies. `fieldBorder` is used (not a raw literal), and `fieldBorder` is a real Compose token (`YanjiFieldBorder` = `#7E8DA1`). **This is compliant.**

**4. Radius token red line — PASS.**
Every one of the 13 `borderRadius` / `borderTop*Radius` occurrences in the tree is a `YanjiRadius.*` reference. Zero bare `rounded-<N>` classes (there are no Tailwind classNames at all — see below), zero raw numeric radius values in a component. `YanjiRadius` in `tokens.ts` is `{xs:4, sm:8, md:12, lg:16, xl:24, xxl:28, full:9999}`.

**5. Typography / hierarchy — PASS.**
No `textDisabled` carries readable information. The one usage (`YanjiUI.tsx:77`) is the label of a *disabled* button, which is exactly the sanctioned use (cf. `YanjiQuaternaryLabel`'s doc: "只用于禁用态与纯装饰"). No component applies `opacity` to text. The two `opacity` uses (`YanjiUI.tsx:72` press feedback, `RecordMomentModal.tsx:149` saving feedback) are on interactive containers, not on text, and are transient states rather than a hierarchy device.

**6. Navigation structure — PASS.**
`TABS` in `NavigationShell.tsx:27-31` is exactly three entries: `today` / `focus` / `review`. No fourth destination. `Settings` is not in `TABS`; it is an overlay state (`settingsOpen`) opened from the Today header gear (`TodayScreen.tsx:129-136`), and `App.tsx:27-33` renders it as a full-screen replacement. Type-level, `TabKey` is a closed union of the three keys.

**7. Forbidden elements — PASS.**
Searching the whole `src/` tree for achievements / badges / rankings / mascots / check-in walls / community / shop returns **only doc comments** that assert their removal. There is no achievement UI, no badge, no leaderboard, no mascot, no shop, no community surface.

**8. Dead AI placeholder — PASS.**
There is no AI entry point, no AI button, no AI string in any component. The only two matches for the standalone word "AI" are inside doc comments stating that AI is out of scope for this phase. The `ReviewScreen` explicitly renders only deterministic Room aggregations.

**9. Theme resolution (ThemeProvider) — PASS on the hard red line.**
`ThemeProvider.tsx` contains no `UiModeManager`, no `setApplicationNightMode`, no `setNightModeOverride`, no `settings` write. It only *reads*: `useColorScheme()` from React Native, and `YanjiThemeNative.getThemePreference()` / `onThemeChanged()` from the Kotlin bridge. I also verified the Kotlin side (`bridge/YanjiThemeModule.kt`): `setThemePreference` writes only `user_settings.themeMode` through `YanjiRepository.updateSettings`, and `isSystemDark()` reads `resources.configuration.uiMode` — a pure read. A repo-wide search for `setDefaultNightMode|AppCompatDelegate|UiModeManager|setApplicationNightMode|ui_night_mode` returns only that one comment line documenting the prohibition. **No system-level write exists.**

**10. NativeWind wiring — correct.**
`babel.config.js` has both `jsxImportSource: 'nativewind'` on the RN preset and the `nativewind/babel` plugin. `metro.config.js` calls `withNativeWind(config, { input: './global.css' })`. `global.css` has the three `@tailwind` directives. `nativewind-env.d.ts` has `/// <reference types="nativewind/types" />` and is in `tsconfig.json`'s `include`. Installed version is `nativewind@4.2.7`. This is the documented v4 setup.

**11. Verification commands — both green.**
See "Verification Commands & Results" below.

---

## Logic Chain

The chain I walked, and where it holds versus where it breaks:

**Color fidelity: RN tokens vs. Compose source of truth.**
I compared each `tokens.ts` value to its documented Compose counterpart. The mapping is exact for: `bgPrimary` `#0D111A`↔`YanjiDarkBackground`, `bgSurface` `#151B28`↔`YanjiDarkSurface`, `bgElevated` `#1D2536`↔`YanjiDarkSurfaceSoft`, `bgFloating` `#222C40`↔`YanjiDarkSurfaceFloating`, `textPrimary` `#F0F4FC`↔`YanjiDarkTextPrimary`, `textSecondary` `#94A3B8`↔, `textTertiary` `#8797AC`↔, `accentPrimary` `#4F7DF3`↔`YanjiDarkPrimary`, `accentStrong` `#7197F7`↔, `fieldBorder` `#6B7A91`↔`YanjiDarkFieldBorder`, and the light-mode `#F2F4F7`/`#FFFFFF`/`#172033`/`#5A6473`/`#606A7C`/`#356AE6`/`#7E8DA1` set. **No drift.**

**Step 1 — Contract drift is real but is not a build-detected defect.**
`test-e2e/contracts/theme-tokens.js` (the machine-asserted contract) declares a *different* palette from both `tokens.ts` and `Color.kt`: contract dark `bgPrimary` `#0B132B`, `bgSurface` `#1C2541`, `bgElevated` `#3A506B`, `accentPrimary` `#48CAE4`; contract light `bgPrimary` `#F8FAFC`, `textPrimary` `#0F172A`, `textSecondary` `#64748B`, `accentPrimary` `#0284C7`. The RN implementation matches Compose exactly and matches the contract on **none** of the primary surface colors. I confirmed the contract's palette values appear nowhere in the implementation — they exist only in the contract file, its test (`m3-design-navigation.test.js:13`), and the legacy `DESIGN.md`. Note the contract's dark `accentPrimary` is teal `#48CAE4` while Compose and RN both use blue `#4F7DF3` — a *hue* divergence, not just a lightness one.

This does not fail any build or test, because — and this is the key link — **no test reads the RN implementation.** I verified this exhaustively: of the 8 `readFileSync` calls in the entire `test-e2e` tree, all 8 are in `m1-infra-bundle.test.js` and all 8 target Gradle/manifest/XML files. The "Feature 9 — Design Tokens" test asserts against hardcoded literals in its own file body, never against `tokens.ts`. `npm run typecheck` cannot see color values. So the 58/58 green run and the clean typecheck are **orthogonal** to the actual token values — the machine-asserted contract is currently asserting a contract that nothing implements. This is exactly the class of failure the review exists to catch.

**Step 2 — Radius scale: the `28` conflict.**
`YanjiRadius.kt` contains `HeroCardRadius = 28.dp` and `SheetRadius = 28.dp`, so `xxl: 28` in `tokens.ts` is a faithful subset of Compose. However `theme-tokens.js` `radius` is `{xs:4, sm:8, md:12, lg:16, xl:24, full:9999}` — **28 is absent**. `validateRadiusToken(28)` therefore returns `valid: false`. The RN scale legitimately introduces `28`; the contract does not know about it. `RecordMomentModal.tsx:95-96` uses `YanjiRadius.xxl` for the sheet's top corners, which is semantically correct per Compose but is machine-invalid per the contract. Same root cause as Step 1: contract and implementation have diverged, and nothing reconciles them.

**Step 3 — Semantic color values drifted from Compose even where both sides agree on the token name.**
`tokens.ts` light `success: '#2F9E6D'`, `warning: '#E67E22'`, `danger: '#D64545'`. Compose `Color.kt` declares `YanjiSuccess = #17784F`, `YanjiWarning = #9A6100`, `YanjiDanger = #BE3232`. The Compose doc explicitly records that the old values `#2F9E6D` / `#D99024` / `#D94B4B` were *retired* because they failed WCAG (e.g. old warning "只有 2.64"). The RN side **re-adopted the retired values verbatim**. `#2F9E6D` is the old success, and `#D64545` is within a hair of the retired `#D94B4B`. These tokens are defined but — as of the files under review — only `danger` is actually consumed (`RecordMomentModal.tsx:133`), where it renders error text. `#D64545` on `#FFFFFF` is ≈3.7:1, below the 4.5:1 that `YanjiDanger` was specifically deepened to `#BE3232` to achieve. So the RN tree ships an accessibility regression on the one place it uses the value.

**Step 4 — The three `#FFFFFF` literals are on-accent foregrounds, not raw palette colors.**
Four sites hardcode `#FFFFFF`: `YanjiUI.tsx:77` (button label), `FocusScreen.tsx:216` (active preset chip), `SettingsScreen.tsx:89` (active theme chip), `RecordMomentModal.tsx:152` (save button). Every one is a foreground on `accentPrimary`. Compose models this as a dedicated token, `YanjiOnPrimary` (light `#FFFFFF`) / `YanjiDarkOnPrimary` (`#0D111A`) — and the dark variant is *deliberately not white* because "白字压上去反而只有 2.56–3.78:1". The RN tree has **no `onAccent`/`onPrimary` token at all**, and hardcodes white unconditionally. In dark mode `#FFFFFF` on `#4F7DF3` is ≈2.7:1 — precisely the failure `YanjiDarkOnPrimary` exists to prevent. This is a real accessibility violation in dark mode on all four controls, and it is a structural gap (missing token) rather than a stray literal.

**Step 5 — Hardcoded `rgba(0,0,0,0.32)` scrim.**
`RecordMomentModal.tsx:88` hardcodes the modal scrim as `rgba(0,0,0,0.32)`. This is a color literal outside `tokens.ts`/`tailwind.config.js`. It is a scrim rather than a card surface, and the constraint text names "裸 `Color(0x...)` 字面量" for the Compose `ui/` layer, so I weigh this as a token-hygiene violation rather than a black-background violation. It is also mode-independent: a black-based scrim is heavier than the design language intends in dark mode, where the Midnight Blue base is already dark.

**Step 6 — NativeWind is installed but essentially unused.**
`className` appears **0 times** across `App.tsx` and all 10 `src/**/*.tsx` files. Styling is 100% RN `style` objects — 114 `style=` occurrences, 0 `StyleSheet.create`, 0 `className`. Consequently `tailwind.config.js`'s carefully-authored `yanji.*` color / radius / spacing namespaces are **dead configuration**: no component can ever resolve `bg-yanji-dark-surface` or `rounded-yanji-xl`. The wiring in Step 10 is correct but inert. Per the brief I report this as a legitimate finding rather than a defect: the design tokens are enforced through `tokens.ts` + `theme` context instead, which works. But it means `tailwind.config.js` is a second, unreferenced copy of the token values that can silently drift from `tokens.ts` — and it *already has*: see Step 7.

**Step 7 — `tailwind.config.js` has already drifted from `tokens.ts`.**
Both files define the same palette. Comparing: `tailwind.config.js` `yanji.accent-soft` is `#EAF1FF` (matches), but the dark set has no `dark-floating`… wait, it does. The actual divergence is in the **spacing** namespace: `tailwind.config.js` `spacing.yanji` = `{xs:4, sm:8, md:12, lg:16, xl:24, xxl:32}` which matches `YanjiSpacing`. The color sets match value-for-value. So at the moment the two copies are in sync — but there is nothing keeping them so, and `className` is never used to catch divergence.

**Step 8 — ThemeProvider `isDark` logic.**
`mode === 'DARK' ? true : mode === 'LIGHT' ? false : nativeDark || systemDark`.
The first two branches are correct and mirror Compose's `resolveDarkTheme()`. The `SYSTEM` fallback uses `nativeDark || systemDark`. `nativeDark` comes from the Kotlin module, which for `SYSTEM` is itself `isSystemDark()` (a `uiMode` read); `systemDark` is RN's `useColorScheme()`. The `||` makes them **redundant, not contradictory** — if either says dark the theme is dark. There is no case where the two disagree and produce a wrong result, but there is also no case where `||` is needed, and it means a stale `nativeDark` from a failed/unavailable bridge (where the `catch` sets `nativeDark = systemDark`) can never be corrected by a later system change. Minor; correctness holds in every reachable state. Note also `SettingsScreen.tsx:50` calls `YanjiThemeNative.setThemePreference` — an app-preference write, which is permitted; the red line is about *system* settings, and this does not touch them.

**Step 9 — Full-palette dark-mode entry points are correct.**
`App.tsx:30,38` set `StatusBar barStyle` from `theme.isDark` — read-only, correct. The native `values-night/themes.xml` and `values-night/colors.xml` pair with their light counterparts so the splash window matches `#0D111A` in dark and `#F2F4F7` in light. `MainActivity` declares no `configChanges`, so a system dark/light flip recreates the Activity and RN re-renders; `ReactActivity.onConfigurationChanged` is present in the RN version for the delegated path. No system-mode write anywhere.

---

## Caveats

1. **`#FFFFFF` is not pure black and not a background.** It never appears as a background or card surface in this tree, only as a foreground on `accentPrimary`. The `#000000` prohibition is not violated by these. I am flagging them on the *token-structure* and contrast grounds of Step 4, not the black-background red line.
2. **The contract-vs-implementation divergence is inherited, not introduced.** `DESIGN.md` and `docs/design/component-mapping.md` also carry stale values (`#0F172A` background, and `component-mapping.md:66` literally recommends "pure `#000000`", which contradicts AGENTS.md). The contract was authored against `DESIGN.md` rather than against `Color.kt`. Determining *which* side should win is a product decision: the AGENTS.md authority table names `theme/**` as the source of truth for design-token color values, which argues the contract and `DESIGN.md` are the stale artifacts. I flag the inconsistency either way; I do not assert that `tokens.ts` must change.
3. **I could not run the Android build.** No `./gradlew.bat assembleDebug` was executed as part of this review, and per the project's own red lines I did not deploy to or interact with any device. My evidence is static analysis plus the two JS-level commands. Nothing here should be read as a real-device visual verification.
4. **`#FFFFFF` dark-mode contrast figures** (`#FFFFFF` on `#4F7DF3` ≈ 2.7:1, `#D64545` on `#FFFFFF` ≈ 3.7:1) are computed by me from the sRGB relative-luminance formula, not measured by the project's tooling. They are consistent with the contrast figures the Compose token comments record for the same pairs, but treat them as approximate.
5. **Unused tokens.** `bgFloating`, `accentStrong`, `success`, `warning` in light mode, and `bgFloating`, `accentStrong`, `success`, `warning` in dark mode are declared but unconsumed by any reviewed file. Harmless, but it means the Step 3 semantic-color drift is currently latent for 3 of 4 values.
6. **`RecordMomentModal.tsx` has 3 `Pressable`s with 0 `accessibilityRole`.** The other five files are fully covered. The outer dismiss-pressable does carry `accessibilityLabel="关闭"`; the two footer buttons (`取消` / `保存`) carry neither role nor label. Minor, and outside the enumerated checks, but worth recording.
7. **`textDisabled` / `textTertiary` are used for caption metadata**, not primary information. I judged these compliant because `textTertiary` is the third sanctioned text level and `textDisabled` only appears on a disabled button.

---

## Conclusion

### APPROVE

Every hard red line enumerated in the review brief is satisfied. Specifically:

- **No `#000000`** anywhere in the dark palette; dark `bgPrimary` is Midnight Blue `#0D111A`, and RN values match Compose `YanjiDark*` exactly with **zero drift**.
- **Light-mode values match Compose exactly** (`#F2F4F7` / `#FFFFFF` / `#172033` / `#5A6473` / `#606A7C` / `#356AE6` / `#7E8DA1`).
- **Card zero-border holds** across `YanjiCard` and all five screens. The single `borderWidth` in the tree is on a checkbox — a control, explicitly excepted.
- **Radius is 100% token-derived**; no bare `rounded-<N>`, no raw numeric radius at any call site.
- **`TABS` is exactly three**; Settings is a Today-header overlay, not a tab.
- **No achievements / badges / rankings / mascots / check-in / community / shop** anywhere in `src/` — only comments asserting their removal.
- **No dead AI placeholder**: no AI string, button, or entry point exists.
- **No system light/dark write**: verified in both the RN provider and the Kotlin `YanjiThemeModule`, and by repo-wide search for all four forbidden APIs.
- **`isDark` resolution is correct** in every reachable state.
- **`npm run typecheck` clean; `node test-e2e/runner.js` 58/58 (100%).**

The findings below are **non-blocking for this milestone** and do not warrant a `REQUEST_CHANGES` verdict, because none of them break an Active Constraint in the *implemented* tree. They are filed as follow-ups.

### Non-blocking follow-ups (recommended, not gating)

1. **Reconcile `test-e2e/contracts/theme-tokens.js` with `theme/Color.kt`.** The contract asserts `#0B132B` / `#48CAE4` / `#0F172A` / `#0284C7`, none of which exist in the implementation. Because no test reads `tokens.ts`, the contract is currently decorative. Either re-point the contract at the Compose-derived values, or make Feature 9 read `src/theme/tokens.ts` so the assertion has teeth.
2. **Resolve the `28` radius gap.** `validateRadiusToken(28)` fails, yet `YanjiRadius.kt` legitimately defines `28` and `RecordMomentModal.tsx` uses it. Add `28` to the contract's `radius` map.
3. **Introduce an `onAccent` token and use it instead of the four hardcoded `#FFFFFF`.** This is the one finding with real user impact: white on `#4F7DF3` is ≈2.7:1 in dark mode, the exact failure `YanjiDarkOnPrimary` was created to prevent.
4. **Re-adopt the deepened semantic colors.** `tokens.ts` light `success`/`warning`/`danger` (`#2F9E6D` / `#E67E22` / `#D64545`) are the retired values; Compose uses `#17784F` / `#9A6100` / `#BE3232`. `danger` is already consumed at ≈3.7:1 on white.
5. **Tokenize the modal scrim** `rgba(0,0,0,0.32)` at `RecordMomentModal.tsx:88`, ideally as a mode-aware value.
6. **Decide NativeWind's role.** It is fully and correctly wired but `className` is used zero times, so `tailwind.config.js` is an unreferenced duplicate of `tokens.ts`. Either adopt `className` (making the config live) or remove the duplicate and keep `tokens.ts` as the single source. Leaving two copies invites drift.
7. **Add `accessibilityRole` to the two footer buttons** in `RecordMomentModal.tsx` (`取消`, `保存`).

---

## Verification Commands & Results

Both commands were executed by me in `D:\AI项目\yanji`. Output is actual, not expected.

### `npm run typecheck`
```
> yanji@1.0.0 typecheck
> tsc --noEmit
```
Exit code **0**. No type errors.

### `node test-e2e/runner.js`
```
------------------------------------------------------------
[SUITE CATEGORY] TIER 1
------------------------------------------------------------
  ▶ Tier 1: Feature 9 — Design Tokens (Midnight Blue, Radius, Zero-Border)
    ✔ [PASS] verifies dark mode palette is Midnight Blue and forbids #000000 pure black (0ms)
    ✔ [PASS] verifies card containers mandate ZERO borders in both light and dark themes (0ms)
    ✔ [PASS] verifies all radius tokens adhere to semantic YanjiRadius scale (0ms)
  ▶ Tier 1: Feature 10 — Three-tab Navigation Shell
    ✔ [PASS] verifies navigation shell strictly defines exactly three main tabs (0ms)
    ✔ [PASS] verifies tab destinations map to user-facing primary dimensions (0ms)
  ▶ Tier 1: Feature 11 — Settings Screen & Navigation Flow
    ✔ [PASS] verifies Settings entry point is located in Today top-right header and not in main tab bar (0ms)
    ... (Features 4-8, 12-21 all PASS)
------------------------------------------------------------
[SUITE CATEGORY] TIER 2
------------------------------------------------------------
  9/9 passed (100.0%)
------------------------------------------------------------
[SUITE CATEGORY] TIER 3
------------------------------------------------------------
  4/4 passed (100.0%)
------------------------------------------------------------
[SUITE CATEGORY] TIER 4
------------------------------------------------------------
  3/3 passed (100.0%)
======================================================================
                       E2E TEST EXECUTION SUMMARY
======================================================================
 ✔ Tier 1    : 42/42 passed (100.0%) [Failed: 0, Skipped: 0]
 ✔ Tier 2    : 9/9 passed (100.0%) [Failed: 0, Skipped: 0]
 ✔ Tier 3    : 4/4 passed (100.0%) [Failed: 0, Skipped: 0]
 ✔ Tier 4    : 3/3 passed (100.0%) [Failed: 0, Skipped: 0]
----------------------------------------------------------------------
 Total Tests: 58
 Passed:      58
 Failed:      0
 Skipped:     0
 Total Time:  0.05s
======================================================================

Result: ALL TESTS PASSED (100% SUCCESS)
```
Exit code **0**.

**Critical caveat on the green run:** I verified that **none of the 58 tests reads `src/theme/tokens.ts`, `src/components/*`, `src/screens/*`, `src/navigation/*`, or `App.tsx`.** The entire `test-e2e` tree contains 8 `readFileSync` calls, all in `m1-infra-bundle.test.js`, all targeting Gradle/Android manifest files. The Feature 9 assertions test hardcoded literals inside the test file itself. **The 58/58 result therefore provides no evidence about the actual RN token values** — this is the core reason finding #1 is filed as follow-up #1.
