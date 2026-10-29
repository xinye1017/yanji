/**
 * Yanji navigation shell — strictly three primary destinations.
 *
 * Active constraint (AGENTS.md §三): the bottom tab bar has EXACTLY three
 * destinations — Today / Focus / Review. Settings is NOT a tab; it is reached
 * from the Today screen top-right header. Achievements, check-ins, mascots and
 * rankings are removed from primary navigation entirely.
 *
 * State discipline: each tab screen is mounted on its first visit and stays
 * mounted for the rest of the session (App.tsx stacks the panes with absoluteFill
 * and isolates inactive ones), so tab switches preserve in-progress input. Settings
 * and the task editor are overlay layers on top of that mounted content, never
 * route replacements. Android back dismisses the topmost overlay before it is
 * allowed to leave the app.
 *
 * The running focus session itself lives in the Kotlin business layer
 * (ActiveSessionCoordinator), never in React state, so switching tabs can
 * never interrupt or lose a running timer.
 */

import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';

export type TabKey = 'today' | 'focus' | 'review';

interface TabDefinition {
  key: TabKey;
  /** Bottom-bar label. */
  label: string;
  /** Screen title shown in the page header. */
  title: string;
}

/** The complete and only list of primary destinations. */
export const TABS: readonly TabDefinition[] = [
  { key: 'today', label: '今天', title: '今天' },
  { key: 'focus', label: '专注', title: '专注' },
  { key: 'review', label: '回顾', title: '回顾' },
] as const;

/**
 * A task handed from the Today tab to the Focus tab. FocusScreen consumes it
 * once (subject + duration preset + the `taskId` the started session binds to).
 */
interface FocusPreset {
  /** null = 从时间表启动，不绑定任何任务。 */
  taskId: string | null;
  subjectId: string;
  subjectName: string;
  title: string;
  plannedMinutes: number;
}

interface NavigationState {
  activeTab: TabKey;
  /** Settings overlay (never a tab). */
  settingsOpen: boolean;
  /** Task editor overlay (Today only). */
  taskEditorOpen: boolean;
  /** Daily timetable overlay (reached from the Today header). */
  timetableOpen: boolean;
  /** Task the Focus tab should prepare a session for; null once consumed. */
  focusPreset: FocusPreset | null;
}

interface NavigationActions {
  selectTab: (tab: TabKey) => void;
  openSettings: () => void;
  closeSettings: () => void;
  openTaskEditor: () => void;
  closeTaskEditor: () => void;
  openTimetable: () => void;
  closeTimetable: () => void;
  /** Prepare a focus bound to a task and switch to the Focus tab. */
  requestFocusPreset: (preset: FocusPreset) => void;
  clearFocusPreset: () => void;
}

const NavigationStateContext = createContext<NavigationState | null>(null);
const NavigationActionsContext = createContext<NavigationActions | null>(null);

export function NavigationProvider({
  children,
  initialTab = 'today',
}: {
  children: React.ReactNode;
  initialTab?: TabKey;
}): React.JSX.Element {
  const [activeTab, setActiveTab] = useState<TabKey>(initialTab);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [taskEditorOpen, setTaskEditorOpen] = useState(false);
  const [timetableOpen, setTimetableOpen] = useState(false);
  const [focusPreset, setFocusPreset] = useState<FocusPreset | null>(null);

  const selectTab = useCallback((tab: TabKey) => {
    setSettingsOpen(false);
    setTimetableOpen(false);
    setActiveTab(tab);
  }, []);

  const openSettings = useCallback(() => {
    setTimetableOpen(false);
    setSettingsOpen(true);
  }, []);
  const closeSettings = useCallback(() => setSettingsOpen(false), []);
  const openTaskEditor = useCallback(() => setTaskEditorOpen(true), []);
  const closeTaskEditor = useCallback(() => setTaskEditorOpen(false), []);
  const openTimetable = useCallback(() => {
    setSettingsOpen(false);
    setTaskEditorOpen(false);
    setTimetableOpen(true);
  }, []);
  const closeTimetable = useCallback(() => setTimetableOpen(false), []);

  const requestFocusPreset = useCallback((preset: FocusPreset) => {
    setSettingsOpen(false);
    setTimetableOpen(false);
    setActiveTab('focus');
    setFocusPreset(preset);
  }, []);

  const clearFocusPreset = useCallback(() => setFocusPreset(null), []);

  const state = useMemo<NavigationState>(
    () => ({
      activeTab,
      settingsOpen,
      taskEditorOpen,
      timetableOpen,
      focusPreset,
    }),
    [activeTab, settingsOpen, taskEditorOpen, timetableOpen, focusPreset]
  );

  const actions = useMemo<NavigationActions>(
    () => ({
      selectTab,
      openSettings,
      closeSettings,
      openTaskEditor,
      closeTaskEditor,
      openTimetable,
      closeTimetable,
      requestFocusPreset,
      clearFocusPreset,
    }),
    [
      selectTab,
      openSettings,
      closeSettings,
      openTaskEditor,
      closeTaskEditor,
      openTimetable,
      closeTimetable,
      requestFocusPreset,
      clearFocusPreset,
    ]
  );

  return (
    <NavigationActionsContext.Provider value={actions}>
      <NavigationStateContext.Provider value={state}>
        {children}
      </NavigationStateContext.Provider>
    </NavigationActionsContext.Provider>
  );
}

export function useNavigationState(): NavigationState {
  const state = useContext(NavigationStateContext);
  if (!state) {
    throw new Error('useNavigationState must be used inside NavigationProvider');
  }
  return state;
}

export function useNavigationActions(): NavigationActions {
  const actions = useContext(NavigationActionsContext);
  if (!actions) {
    throw new Error('useNavigationActions must be used inside NavigationProvider');
  }
  return actions;
}
