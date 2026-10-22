# Focus Screen UI/UX Redesign & Immersion Micro-transition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Focus Screen (专注页) subject selector according to the provided reference design, modernize the timer display (clean `min` suffix, `∞` stopwatch, relocation of estimated end time, clean CTA button), and implement smooth micro-transitions for entering/exiting the immersive focus state.

**Architecture:**
- `RollingTimeDisplay`: Render duration with minutes digits followed by clean `min` label, eliminating `:00` seconds.
- `FocusScreen`:
  - Subject Selector: Redesign with `学习内容` + `01 / SUBJECT` header, bordered pill/rect buttons for Level 1 categories (no dots), and text tab bar with indicator underline for Level 2 sub-subjects.
  - Duration & Stopwatch: Unify container heights between countdown (`RollingTimeDisplay`) and stopwatch (`∞`), avoiding any layout jump. Relocate "预计xx结束本轮" below the Vernier dial with dynamic time updating.
  - CTA Button: Remove `detail` (`xxmin`), keeping clean "开始专注".
  - Immersion Micro-transition: Animate canvas background fading into `#0F1214` and smooth opacity/scale transition for controls and timer readout when starting and stopping focus sessions.
- `test-e2e/focus-audit.test.mjs` & `test-e2e/rolling-time-display.test.mjs`: Update test assertions to match the new UI specification.

**Tech Stack:** React Native 0.87 (Fabric), React 19, TypeScript, React Native Reanimated, Node test runner.

**Spec / Directives:**
1. Reference design image: `media_1792686801813_ac6cc336.png`
2. Remove `:00` seconds from intermediate big digits, display `min` suffix.
3. Start button (`YanjiPrimaryButton`): remove `xxmin` detail.
4. Move "预计xx结束本轮" below the Vernier dial, with live minute update.
5. In Stopwatch mode: display `∞` at the identical vertical height and position as countdown to eliminate layout shift.
6. Entering/exiting immersion: Smooth background fade and timer scale/centering micro-transition.
7. Top bar remains unchanged for now (future mock-exam entry placeholder).

---

## Tasks

### Task 1: Update `RollingTimeDisplay.tsx` (Remove `:00`, Show `min`)
- [ ] In `src/components/RollingTimeDisplay.tsx`, remove the static `:00` text node.
- [ ] Render a styled `min` suffix right after the units digit slot with matching baseline alignment, using `paper.typography.titleSection` or proportional font size with `paper.colors.secondary`.
- [ ] Update `test-e2e/rolling-time-display.test.mjs` to test for `min` instead of `:00`.
- [ ] Run `node --test test-e2e/rolling-time-display.test.mjs` to verify.

### Task 2: Redesign Subject Selector in `FocusScreen.tsx`
- [ ] Replace "选择科目" section header with a 2-column header: `学习内容` (left) and `01 / SUBJECT` (right, captionMeta/monospace style).
- [ ] Redesign Category selection (Level 1):
  - Row of rounded card/pill buttons (`borderRadius: paper.radius.compactCard` ~ 10dp).
  - Selected: dark background with brand border (`borderColor: paper.colors.brand`, `borderWidth: 1.5`), `color: paper.colors.ink`, no color dot.
  - Unselected: subtle surface background (`backgroundColor: paper.colors.surface`), hairline border, `color: paper.colors.secondary`, no color dot.
- [ ] Redesign Sub-subject selection (Level 2):
  - Clean text tabs underneath Level 1 categories.
  - Selected: bold text (`color: paper.colors.ink`) with a bottom indicator line (`height: 2.5`, `backgroundColor: paper.colors.brand`, `borderRadius: 1.5`).
  - Unselected: secondary text (`color: paper.colors.secondary`), no indicator line.
  - If category has no children, gracefully omit Level 2 strip.

### Task 3: Stopwatch Mode (`∞`), Estimated Time Relocation & Clean Start CTA
- [ ] Unify layout height of Countdown vs Stopwatch:
  - In Stopwatch mode, display `∞` in `paper.typography.displayDial` at the exact same vertical slot height and margin as `RollingTimeDisplay`.
  - Add descriptive subtitle ("自由正向计时 · 不设上限") below `∞` at a matching visual rhythm.
- [ ] Move "预计 xx:xx 结束本轮" to below the Vernier dial (`FocusVernierDial`).
  - Implement a dynamic time ticker so the estimated end time updates as real-world minutes tick by.
- [ ] In `YanjiPrimaryButton`, remove `detail` prop so the button cleanly says "开始专注" (or "启动中…" when busy).

### Task 4: Implement Micro-transition for Entering/Exiting Immersion Field
- [ ] In `FocusScreen.tsx`, introduce a smooth transition container:
  - When clicking "开始专注" (`handleStart`), animate background fade to `#0F1214` / `immersed.canvas` and fade out preparation controls (subject selector, segmented control, dial, button).
  - Maintain the timer readout in the visual hierarchy so it smoothly transitions to the immersive clock position.
  - When exiting immersion (complete or discard), smoothly fade back to ready state.
- [ ] Ensure reduced motion (`useReducedMotion`) is respected.

### Task 5: Update Tests and Verify
- [ ] Update `test-e2e/focus-audit.test.mjs` for the new subject selector styling (bordered category, underline tab sub-subject, no dots, start button without detail).
- [ ] Run full test suite: `npm test`.
- [ ] Run `npm run typecheck`.
- [ ] Run bundle & build APK: `npm run bundle:android` and `./gradlew.bat assembleDebug`.
- [ ] Push to connected Android device (`PKB110`), launch, and verify with screenshot.
