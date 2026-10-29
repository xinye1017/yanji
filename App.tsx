/**
 * Yanji 2.0 root component.
 *
 * Composition order (outermost -> innermost):
 *   YanjiThemeProvider      resolves light/dark from the native preference
 *     NavigationProvider    three-tab shell state + overlay state
 *       AppShell            mounts each tab on first visit and keeps it mounted,
 *                           then overlays secondary layers (settings / task editor)
 *
 * A tab screen is mounted the first time it becomes active and stays mounted for
 * the rest of the session. Inactive panes are transparent and isolated from touch
 * and accessibility, so a Record Moment draft, the Focus subject/duration choice
 * and the Review date all survive a tab switch. Deferring the first mount keeps
 * the Focus dial and Review charts out of the cold-start first-frame budget,
 * where only Today is visible anyway. The running focus session is owned by the
 * Kotlin business layer, so switching tabs can never interrupt the timer.
 *
 * The props are the host's launch options: `MainActivity.getLaunchOptions()`
 * hands over today's snapshot, read before the first frame, so Today never
 * opens on 「正在读取…」. They are absent when the preheat failed, and the shell
 * then falls back to the regular bridged reads.
 */

import React, { useEffect, useLayoutEffect, useState } from 'react';
import { AppState, BackHandler, StatusBar, StyleSheet, View } from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { BlurTarget } from 'react-native-blur-overlay';
import { YanjiLiquidGlass } from './src/theme/liquidGlass';
import { BottomTabBar } from './src/navigation/BottomTabBar';
import { NavigationProvider, useNavigationActions, useNavigationState } from './src/navigation/NavigationShell';
import type { TabKey } from './src/navigation/NavigationShell';
import { FocusScreen } from './src/screens/FocusScreen';
import { ReviewScreen } from './src/screens/ReviewScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { TimetableScreen } from './src/screens/TimetableScreen';
import { TodayScreen } from './src/screens/TodayScreen';
import { YanjiThemeProvider, useYanjiTheme } from './src/theme/ThemeProvider';
import { onDataChanged } from './src/bridge';
import type { YanjiLaunchProperties } from './src/bridge';
import { dataRefreshCoordinator } from './src/state/dataRefresh';
import { seedLaunchSnapshot } from './src/state/dataRefresh';
import { setTimerForeground, useActiveSession } from './src/state/timerStore';

/**
 * All panes stay mounted and fill the same content area, preserving local
 * state and scroll offsets. Opacity only controls visibility; absoluteFill
 * keeps inactive panes from taking space in the parent's flex layout.
 */
function TabPane({
  active,
  testID,
  children,
}: {
  active: boolean;
  testID: string;
  children: React.ReactNode;
}): React.JSX.Element {
  return (
    <View
      testID={testID}
      style={[StyleSheet.absoluteFill, { opacity: active ? 1 : 0 }]}
      pointerEvents={active ? 'auto' : 'none'}
      // Keep the hidden pane out of the a11y tree so TalkBack does not read it.
      accessibilityElementsHidden={!active}
      importantForAccessibility={active ? 'auto' : 'no-hide-descendants'}
    >
      {children}
    </View>
  );
}

function AppShell(): React.JSX.Element {
  const theme = useYanjiTheme();
  const insets = useSafeAreaInsets();
  const { activeTab, settingsOpen, taskEditorOpen, timetableOpen } = useNavigationState();
  const { closeSettings, closeTaskEditor, closeTimetable, selectTab } = useNavigationActions();
  const [foreground, setForeground] = useState(() => AppState.currentState === 'active');
  const [foregroundRevision, setForegroundRevision] = useState(0);
  /**
   * 首次进入某个 Tab 时才挂载它的页面。
   *
   * 三个页面都会保持挂载（状态、滚动位置与草稿不丢），但没有理由在启动瞬间
   * 把所有三个完整页面树都渲染一遍：专注页的圆环 / 刻度盘与回顾页的图表在用户
   * 还停留在启动画面时就消耗了首帧预算。挂载后它们与旧行为完全一致。
   */
  const [mountedTabs, setMountedTabs] = useState<ReadonlySet<TabKey>>(
    () => new Set<TabKey>([activeTab])
  );
  useLayoutEffect(() => {
    setMountedTabs(previous =>
      previous.has(activeTab) ? previous : new Set(previous).add(activeTab)
    );
  }, [activeTab]);
  // Session listeners are low-frequency native state transitions, never second ticks.
  // Paused sessions remain immersive; another tab must keep the glass dock visible.
  const activeSession = useActiveSession(
    foreground && activeTab === 'focus' && !settingsOpen && !timetableOpen
  );
  const focusImmersive = foreground && activeTab === 'focus' && !settingsOpen && !timetableOpen
    && activeSession !== null && activeSession.phase === 'FOCUS';

  useEffect(() => {
    const unsubscribeData = onDataChanged(event =>
      dataRefreshCoordinator.enqueueDataChanged(event.type)
    );
    const appState = AppState.addEventListener('change', next => {
      const isForeground = next === 'active';
      // `setForeground` itself reconciles the readers when it sees a real
      // background → foreground transition, so it is the single place a resume
      // can trigger reads. Calling `handleForeground` here as well would
      // invalidate the read it just started and issue a second one.
      dataRefreshCoordinator.setForeground(isForeground);
      setTimerForeground(isForeground);
      setForeground(isForeground);
      if (isForeground) setForegroundRevision(revision => revision + 1);
    });
    // AppState may change between the initial render and listener attachment.
    // Reconcile its current native cache after subscribing so a resumed shell
    // cannot remain hidden or keep its readers suspended until another event.
    //
    // This also covers the case the coordinator cannot see on its own: after an
    // Activity exit and re-entry in the same process the React tree remounts
    // while the coordinator still believes it is backgrounded. Its readers
    // register during the child effects — before this line runs — so their
    // registration is gated, and without the transition here nothing would ever
    // read Room again and Today would sit on 「正在读取」 forever.
    const currentForeground = AppState.currentState === 'active';
    dataRefreshCoordinator.setForeground(currentForeground);
    setTimerForeground(currentForeground);
    if (currentForeground !== foreground) {
      setForeground(currentForeground);
      if (currentForeground) setForegroundRevision(revision => revision + 1);
    }
    return () => {
      unsubscribeData();
      appState.remove();
    };
  }, []);

  // Android back: dismiss the topmost overlay first, then step out of the
  // immersion field, and only then leave the app.
  //
  // The immersion field deliberately carries no back affordance of its own
  // (AB-04 keeps that column to the session pill alone), so the system back
  // gesture *is* its exit. Returning to Today rather than finishing the
  // Activity is also the Android convention: back must not quit the app while
  // an in-app "up" destination still exists. The session is unaffected either
  // way — it lives in the Kotlin timer and keeps running behind the dock.
  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (taskEditorOpen) {
        closeTaskEditor();
        return true;
      }
      if (timetableOpen) {
        closeTimetable();
        return true;
      }
      if (settingsOpen) {
        closeSettings();
        return true;
      }
      if (focusImmersive) {
        selectTab('today');
        return true;
      }
      return false;
    });
    return () => subscription.remove();
  }, [
    taskEditorOpen, settingsOpen, timetableOpen, closeSettings, closeTaskEditor, closeTimetable,
    focusImmersive, selectTab,
  ]);

  // Keep page content below the status bar using the native safe-area inset.
  // StatusBar controls icon contrast; the tab bar owns the bottom safe area.
  const topInset = insets.top;
  const backdrop = focusImmersive
    ? (activeSession?.isPaused ? theme.paper.immersive.paused.canvas : theme.paper.immersive.running.canvas)
    : theme.paper.colors.canvas;

  return (
    <View testID="app-shell" style={{ flex: 1, backgroundColor: backdrop }}>
      <StatusBar barStyle={focusImmersive || theme.isDark ? 'light-content' : 'dark-content'} />

      <BlurTarget id={YanjiLiquidGlass.targetId} style={{ flex: 1, paddingTop: topInset }}>
        {/* The capture target paints the page canvas itself. A live glass overlay
            records only this subtree, never the shell behind it, so a pane that is
            momentarily empty — the commit where a tab is selected before its screen
            mounts, or the frame where the old pane is already hidden — would
            otherwise record transparent pixels. Blurring transparency yields black,
            which the glass body turns into the grey band that flashed on every tab
            switch. Giving the content area its own canvas keeps every capture
            opaque. */}
        <View testID="tab-content" style={{ flex: 1, backgroundColor: backdrop }}>
          <TabPane
            testID="pane-today"
            active={foreground && !settingsOpen && !timetableOpen && activeTab === 'today'}
          >
            {mountedTabs.has('today') ? (
              <TodayScreen
                visible={foreground && !settingsOpen && !timetableOpen && activeTab === 'today'}
                foregroundRevision={foregroundRevision}
              />
            ) : null}
          </TabPane>
          <TabPane
            testID="pane-focus"
            active={foreground && !settingsOpen && !timetableOpen && activeTab === 'focus'}
          >
            {mountedTabs.has('focus') ? (
              <FocusScreen
                visible={foreground && !settingsOpen && !timetableOpen && activeTab === 'focus'}
              />
            ) : null}
          </TabPane>
          <TabPane
            testID="pane-review"
            active={foreground && !settingsOpen && !timetableOpen && activeTab === 'review'}
          >
            {mountedTabs.has('review') ? (
              <ReviewScreen
                visible={foreground && !settingsOpen && !timetableOpen && activeTab === 'review'}
              />
            ) : null}
          </TabPane>
        </View>
      </BlurTarget>

      <BottomTabBar
        visible={foreground && !settingsOpen && !taskEditorOpen && !timetableOpen && !focusImmersive}
      />

      {settingsOpen ? (
        <View
          pointerEvents={timetableOpen ? 'none' : 'auto'}
          importantForAccessibility={timetableOpen ? 'no-hide-descendants' : 'auto'}
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: 0,
            bottom: 0,
            backgroundColor: theme.paper.colors.canvas,
            paddingTop: topInset,
          }}
        >
          <SettingsScreen />
        </View>
      ) : null}

      {timetableOpen ? (
        <View
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: 0,
            bottom: 0,
            backgroundColor: theme.paper.colors.canvas,
            paddingTop: topInset,
          }}
        >
          <TimetableScreen onClose={closeTimetable} />
        </View>
      ) : null}
    </View>
  );
}

export default function App(launchProperties: Partial<YanjiLaunchProperties>): React.JSX.Element {
  // Seed the host's preheated snapshot before the first render, so Today's first
  // frame paints real data instead of 「正在读取…」.
  //
  // `useState` runs this exactly once per mounted root and discards the result:
  // seeding is a cache fill, not state. Doing it on every render would mutate the
  // coordinator during render for no gain, and a `useEffect` would be too late —
  // the first frame is the one that has to be right.
  useState(() => {
    seedLaunchSnapshot(launchProperties);
    return null;
  });
  return (
    <YanjiThemeProvider>
      <SafeAreaProvider>
        <NavigationProvider>
          <AppShell />
        </NavigationProvider>
      </SafeAreaProvider>
    </YanjiThemeProvider>
  );
}
