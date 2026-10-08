/**
 * Yanji navigation shell — strictly three primary destinations.
 *
 * Active constraint (AGENTS.md §三): the bottom tab bar has EXACTLY three
 * destinations — Today / Focus / Review. Settings is NOT a tab; it is reached
 * from the Today screen top-right header. Achievements, check-ins, mascots and
 * rankings are removed from primary navigation entirely.
 *
 * State discipline: the three tab screens stay mounted for the whole session
 * (App.tsx hides the inactive ones with `display: 'none'`), so tab switches
 * preserve in-progress input. Settings and the task editor are overlay layers
 * on top of that mounted content, never route replacements. Android back
 * dismisses the topmost overlay before it is allowed to leave the app.
 *
 * The running focus session itself lives in the Kotlin business layer
 * (ActiveSessionCoordinator), never in React state, so switching tabs can
 * never interrupt or lose a running timer.
 */

import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';

export type TabKey = 'today' | 'focus' | 'review';

export interface TabDefinition {
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
export interface FocusPreset {
  taskId: string;
  subjectId: string;
  subjectName: string;
  title: string;
  plannedMinutes: number;
}

export interface NavigationState {
  activeTab: TabKey;
  /** Settings overlay (never a tab). */
  settingsOpen: boolean;
  /** Task editor overlay (Today only). */
  taskEditorOpen: boolean;
  /** Task the Focus tab should prepare a session for; null once consumed. */
  focusPreset: FocusPreset | null;
  selectTab: (tab: TabKey) => void;
  openSettings: () => void;
  closeSettings: () => void;
  openTaskEditor: () => void;
  closeTaskEditor: () => void;
  /** Prepare a focus bound to a task and switch to the Focus tab. */
  requestFocusPreset: (preset: FocusPreset) => void;
  clearFocusPreset: () => void;
}

const NavigationContext = createContext<NavigationState | null>(null);

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
  const [focusPreset, setFocusPreset] = useState<FocusPreset | null>(null);

  const selectTab = useCallback((tab: TabKey) => {
    setSettingsOpen(false);
    setActiveTab(tab);
  }, []);

  const openSettings = useCallback(() => setSettingsOpen(true), []);
  const closeSettings = useCallback(() => setSettingsOpen(false), []);
  const openTaskEditor = useCallback(() => setTaskEditorOpen(true), []);
  const closeTaskEditor = useCallback(() => setTaskEditorOpen(false), []);

  const requestFocusPreset = useCallback((preset: FocusPreset) => {
    setSettingsOpen(false);
    setActiveTab('focus');
    setFocusPreset(preset);
  }, []);

  const clearFocusPreset = useCallback(() => setFocusPreset(null), []);

  const value = useMemo<NavigationState>(
    () => ({
      activeTab,
      settingsOpen,
      taskEditorOpen,
      focusPreset,
      selectTab,
      openSettings,
      closeSettings,
      openTaskEditor,
      closeTaskEditor,
      requestFocusPreset,
      clearFocusPreset,
    }),
    [
      activeTab,
      settingsOpen,
      taskEditorOpen,
      focusPreset,
      selectTab,
      openSettings,
      closeSettings,
      openTaskEditor,
      closeTaskEditor,
      requestFocusPreset,
      clearFocusPreset,
    ]
  );

  return <NavigationContext.Provider value={value}>{children}</NavigationContext.Provider>;
}

export function useNavigation(): NavigationState {
  const state = useContext(NavigationContext);
  if (!state) {
    throw new Error('useNavigation must be used inside NavigationProvider');
  }
  return state;
}
