/**
 * Yanji 2.0 root component.
 *
 * Composition order (outermost → innermost):
 *   YanjiThemeProvider      resolves light/dark from the native preference
 *     NavigationProvider    three-tab shell state + settings overlay state
 *       AppShell            renders the active screen and the bottom tab bar
 *
 * The running focus session is owned by the Kotlin business layer, so switching
 * tabs can never interrupt the timer.
 */

import React from 'react';
import { StatusBar, View } from 'react-native';
import { BottomTabBar } from './src/navigation/BottomTabBar';
import { NavigationProvider, useNavigation } from './src/navigation/NavigationShell';
import { FocusScreen } from './src/screens/FocusScreen';
import { ReviewScreen } from './src/screens/ReviewScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { TodayScreen } from './src/screens/TodayScreen';
import { YanjiThemeProvider, useYanjiTheme } from './src/theme/ThemeProvider';

function AppShell(): React.JSX.Element {
  const theme = useYanjiTheme();
  const { activeTab, settingsOpen } = useNavigation();

  if (settingsOpen) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
        <StatusBar barStyle={theme.isDark ? 'light-content' : 'dark-content'} />
        <SettingsScreen />
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <StatusBar barStyle={theme.isDark ? 'light-content' : 'dark-content'} />
      <View style={{ flex: 1 }}>
        {activeTab === 'today' ? <TodayScreen /> : null}
        {activeTab === 'focus' ? <FocusScreen /> : null}
        {activeTab === 'review' ? <ReviewScreen /> : null}
      </View>
      <BottomTabBar />
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
