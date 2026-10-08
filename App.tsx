/**
 * Yanji 2.0 root component.
 *
 * Composition order (outermost → innermost):
 *   YanjiThemeProvider      resolves light/dark from the native preference
 *     NavigationProvider    three-tab shell state + overlay state
 *       AppShell            keeps every tab screen mounted and overlays
 *                           secondary layers (settings / task editor) on top
 *
 * The three tab screens stay mounted for the whole session and inactive ones
 * are hidden with `display: 'none'`, so a Record Moment draft, the Focus
 * subject/duration choice and the Review date all survive a tab switch. The
 * running focus session is owned by the Kotlin business layer, so switching
 * tabs can never interrupt the timer.
 */

import React, { useEffect } from 'react';
import { BackHandler, StatusBar, View } from 'react-native';
import { BottomTabBar } from './src/navigation/BottomTabBar';
import { NavigationProvider, useNavigation } from './src/navigation/NavigationShell';
import { FocusScreen } from './src/screens/FocusScreen';
import { ReviewScreen } from './src/screens/ReviewScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { TodayScreen } from './src/screens/TodayScreen';
import { YanjiThemeProvider, useYanjiTheme } from './src/theme/ThemeProvider';

/**
 * A tab screen that stays mounted. Inactive panes are hidden with
 * `display: 'none'` instead of being unmounted, so their local state (drafts,
 * selections) survives tab switches.
 */
function TabPane({
  active,
  children,
}: {
  active: boolean;
  children: React.ReactNode;
}): React.JSX.Element {
  return <View style={{ flex: 1, display: active ? 'flex' : 'none' }}>{children}</View>;
}

function AppShell(): React.JSX.Element {
  const theme = useYanjiTheme();
  const { activeTab, settingsOpen, taskEditorOpen, closeSettings, closeTaskEditor } =
    useNavigation();

  // Android back: dismiss the topmost overlay first, only then leave the app.
  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (taskEditorOpen) {
        closeTaskEditor();
        return true;
      }
      if (settingsOpen) {
        closeSettings();
        return true;
      }
      return false;
    });
    return () => subscription.remove();
  }, [taskEditorOpen, settingsOpen, closeSettings, closeTaskEditor]);

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <StatusBar barStyle={theme.isDark ? 'light-content' : 'dark-content'} />

      <View style={{ flex: 1 }}>
        <TabPane active={activeTab === 'today'}>
          <TodayScreen />
        </TabPane>
        <TabPane active={activeTab === 'focus'}>
          <FocusScreen />
        </TabPane>
        <TabPane active={activeTab === 'review'}>
          <ReviewScreen />
        </TabPane>
      </View>

      <BottomTabBar />

      {settingsOpen ? (
        <View
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: 0,
            bottom: 0,
            backgroundColor: theme.colors.bgPrimary,
          }}
        >
          <SettingsScreen />
        </View>
      ) : null}
    </View>
  );
}

export default function App(): React.JSX.Element {
  return (
    <YanjiThemeProvider>
      <NavigationProvider>
        <AppShell />
      </NavigationProvider>
    </YanjiThemeProvider>
  );
}
