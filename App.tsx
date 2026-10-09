/**
 * Yanji 2.0 root component.
 *
 * Composition order (outermost -> innermost):
 *   YanjiThemeProvider      resolves light/dark from the native preference
 *     NavigationProvider    three-tab shell state + overlay state
 *       AppShell            keeps every tab screen mounted and overlays
 *                           secondary layers (settings / task editor) on top
 *
 * The three tab screens stay mounted and share one full-size content area for
 * the whole session. Inactive panes are transparent and isolated
 * from touch and accessibility, so a Record Moment draft, the Focus
 * subject/duration choice and the Review date all survive a tab switch. The
 * running focus session is owned by the Kotlin business layer, so switching
 * tabs can never interrupt the timer.
 */

import React, { useEffect } from 'react';
import { BackHandler, StatusBar, StyleSheet, View } from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { BottomTabBar } from './src/navigation/BottomTabBar';
import { NavigationProvider, useNavigation } from './src/navigation/NavigationShell';
import { FocusScreen } from './src/screens/FocusScreen';
import { ReviewScreen } from './src/screens/ReviewScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { TodayScreen } from './src/screens/TodayScreen';
import { YanjiThemeProvider, useYanjiTheme } from './src/theme/ThemeProvider';

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

  // Keep page content below the status bar using the native safe-area inset.
  // StatusBar controls icon contrast; the tab bar owns the bottom safe area.
  const topInset = insets.top;

  return (
    <View testID="app-shell" style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <StatusBar barStyle={theme.isDark ? 'light-content' : 'dark-content'} />

      <View style={{ flex: 1, paddingTop: topInset }}>
        {/* Keep the safe-area padding outside the absolute-positioning parent. */}
        <View testID="tab-content" style={{ flex: 1 }}>
          <TabPane testID="pane-today" active={activeTab === 'today'}>
            <TodayScreen />
          </TabPane>
          <TabPane testID="pane-focus" active={activeTab === 'focus'}>
            <FocusScreen />
          </TabPane>
          <TabPane testID="pane-review" active={activeTab === 'review'}>
            <ReviewScreen />
          </TabPane>
        </View>
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
            paddingTop: topInset,
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
      <SafeAreaProvider>
        <NavigationProvider>
          <AppShell />
        </NavigationProvider>
      </SafeAreaProvider>
    </YanjiThemeProvider>
  );
}
