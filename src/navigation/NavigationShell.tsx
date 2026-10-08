/**
 * Yanji navigation shell — strictly three primary destinations.
 *
 * Active constraint (AGENTS.md §三): the bottom tab bar has EXACTLY three
 * destinations — Today / Focus / Review. Settings is NOT a tab; it is reached
 * from the Today screen top-right header. Achievements, check-ins, mascots and
 * rankings are removed from primary navigation entirely.
 *
 * Navigation state is restored on tab switch: the active focus session lives in
 * the Kotlin business layer (ActiveSessionCoordinator), never in React state,
 * so switching tabs can never interrupt or lose a running timer.
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

export interface NavigationState {
  activeTab: TabKey;
  /** Secondary routes pushed on top of a tab (e.g. settings). */
  settingsOpen: boolean;
  selectTab: (tab: TabKey) => void;
  openSettings: () => void;
  closeSettings: () => void;
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

  const selectTab = useCallback((tab: TabKey) => {
    setSettingsOpen(false);
    setActiveTab(tab);
  }, []);

  const openSettings = useCallback(() => setSettingsOpen(true), []);
  const closeSettings = useCallback(() => setSettingsOpen(false), []);

  const value = useMemo<NavigationState>(
    () => ({ activeTab, settingsOpen, selectTab, openSettings, closeSettings }),
    [activeTab, settingsOpen, selectTab, openSettings, closeSettings]
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
