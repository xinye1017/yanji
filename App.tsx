/**
 * Yanji 2.0 root component.
 *
 * Composition order (outermost -> innermost):
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
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { BottomTabBar } from './src/navigation/BottomTabBar';
import { NavigationProvider, useNavigation } from './src/navigation/NavigationShell';
import { FocusScreen } from './src/screens/FocusScreen';
import { ReviewScreen } from './src/screens/ReviewScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { TodayScreen } from './src/screens/TodayScreen';
import { YanjiThemeProvider, useYanjiTheme } from './src/theme/ThemeProvider';

/**
 * A tab screen that stays mounted. Inactive panes are faded out and made
 * non-interactive rather than `display: 'none'`, so their local state (drafts,
 * selections) survives tab switches.
 *
 * `display: 'none'` is avoided deliberately: it removes the pane from layout
 * entirely, which would tear down the inner ScrollView's offset and would also
 * drop the pane's accessibility subtree. An opacity fade plus
 * `pointerEvents="none"` keeps the tree intact while costing nothing, because
 * all three panes are always mounted anyway.
 */
function TabPane({
  active,
  children,
}: {
  active: boolean;
  children: React.ReactNode;
}): React.JSX.Element {
  return (
    <View
      style={{ flex: 1, opacity: active ? 1 : 0 }}
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

  // Safe-area insets replace the old hardcoded 38/44: on a notched or
  // punch-hole device the old value either clipped the title or wasted a
  // centimetre of paper. The status bar itself stays translucent so the page
  // background runs to the top edge.
  const topInset = insets.top;

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <StatusBar barStyle={theme.isDark ? 'light-content' : 'dark-content'} />

      <View style={{ flex: 1, paddingTop: topInset }}>
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
