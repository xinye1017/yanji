# Orchestration Plan — Yanji React Native Refactoring

## Objectives
Transform 「研迹」 into a React Native + TypeScript + NativeWind frontend architecture while retaining the native Kotlin core (Room DB, elapsedRealtime monotonic timing engine, system foreground notification service).
Build three quiet, restrained, high-quality core dimensions: Today (今天), Focus (专注), Review (回顾), and cross-page "Record Moment" (记录此刻).

## Phases

### Phase 0: Survey & Architecture Discovery
- Dispatch 3 parallel Explorers:
  1. `explorer_survey_native`: Investigate existing Kotlin business logic, Room entities/DAOs, FocusTimerService, ActiveSessionCoordinator, and unit tests.
  2. `explorer_survey_rn_env`: Investigate Gradle setup, React Native / Add-to-App dependencies, Node.js / npm environment, package.json, TypeScript, Metro, and NativeWind integration feasibility.
  3. `explorer_survey_ui_specs`: Map current UI features (Today, Focus, Review, Record Moment, Settings) and theme tokens (Radius, Colors, Midnight Blue, Card zero-border).
- Synthesize findings into `PROJECT.md` (Feature Inventory, Architecture, Code Layout, Interfaces).

### Phase 1: Dual Track Dispatch
- **E2E Testing Track**: Spawn E2E Testing Orchestrator (`orchestrator_e2e_1`) to establish `TEST_INFRA.md` and Tiers 1-4 opaque-box test suites based on user requirements.
- **Implementation Track**:
  - Milestone 1: RN Integration & Native Bridge (Add-to-App, Native Modules for Room & FocusTimerService, TypeScript types).
  - Milestone 2: Design System, Tokens & Navigation Shell (NativeWind, Radius/Color tokens, 3-tab Bottom Navigation, Settings entry).
  - Milestone 3: Today & Focus & Record Moment Views (Today overview/cards, Focus monotonic timer sync, seamless Record Moment modal).
  - Milestone 4: Review View & Historical Timeline (Daily aggregation, 7-day stats, deterministic data from Room).
  - Final Milestone: Pass 100% E2E tests + Phase 2 Adversarial Coverage Hardening.

### Phase 2: Verification & Victory Audit
- Verify all acceptance criteria:
  - TypeScript typecheck passes with 0 errors.
  - Gradle `assembleDebug` passes.
  - `:app:testDebugUnitTest` passes.
  - E2E Test Suite 100% passes.
  - Forensic integrity audit passes with CLEAN status.
- Report victory to Sentinel.
