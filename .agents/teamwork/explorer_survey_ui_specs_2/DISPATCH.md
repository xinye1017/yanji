# DISPATCH: explorer_survey_ui_specs_2

You are explorer_survey_ui_specs_2 (teamwork_preview_explorer), replacing explorer_survey_ui_specs_1 which was interrupted by a network stream EOF.
Your working directory is: d:\AI项目\yanji\.agents\teamwork\explorer_survey_ui_specs_2
Project root: d:\AI项目\yanji
Original request: d:\AI项目\yanji\.agents\teamwork\ORIGINAL_REQUEST.md

## Mission
Investigate and specify UI design tokens, navigation, and component contracts:
1. Examine existing theme files: `app/src/main/java/com/example/yanji/theme/Radius.kt`, `Color.kt`, `Theme.kt`, `Spacing.kt`.
   - AGENTS.md §三 rules: Midnight Blue dark palette (NO pure `#000000`), card zero-border (no BorderStroke), Radius tokens (`YanjiRadius`).
2. Map current UI features in existing Compose screens (`app/src/main/java/com/example/yanji/ui/`):
   - **Today (今天)**: Overview card (date, countdown, total focus), single adaptive CTA (start/continue), today tasks, record moment entry, settings entry in top right.
   - **Focus (专注)**: Big timer display, stopwatch/countdown toggle with memory, native service binding, seamless "Record Moment" popup without pausing timer.
   - **Review (回顾)**: Deterministic historical timeline by day, aggregated focus + completed tasks + notes, 7-day trend, subject breakdown, clean placeholder for future AI expansion (NO fake dialogue/data).
   - **Record Moment (记录此刻)**: Shared modal across tabs, quick text entry, auto timestamp & active session binding, instantaneous save to Room, smooth keyboard behavior.
   - **Settings (设置)**: Accessible via Today top-right, clean preferences.
3. Formulate the Tailwind/NativeWind theme configuration (colors, borderRadius, typography) and detailed React Native component hierarchy & TypeScript props interfaces.

Write your comprehensive 5-component report to `d:\AI项目\yanji\.agents\teamwork\explorer_survey_ui_specs_2\handoff.md` and send message back when done.
