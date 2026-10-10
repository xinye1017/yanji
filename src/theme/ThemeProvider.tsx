/**
 * Yanji theme provider — resolves light/dark from the native theme preference
 * (user_settings.themeMode) and exposes semantic colors to the component tree.
 *
 * The system-level light/dark setting is NEVER modified (AGENTS.md §二.6):
 * this provider only reads the app preference and the system color scheme.
 *
 * SYSTEM resolves from `useColorScheme()` alone. The native `pref.isDark` is
 * deliberately not merged in: it is a snapshot of the configuration cached by
 * a possibly-frozen process, pushed only when user_settings changes. OR-ing it
 * in let a stale `true` from a night-time launch override the live system value
 * forever, pinning the app to dark while the phone stayed light. Activity
 * recreation on uiMode changes (MainActivity does not declare uiMode in
 * configChanges) guarantees `useColorScheme()` re-reads a fresh value.
 */

import React, { createContext, useContext, useEffect, useState } from 'react';
import { useColorScheme } from 'react-native';
import { YanjiThemeNative, onThemeChanged } from '../bridge';
import {
  YanjiColors,
  YanjiMotion,
  YanjiRadius,
  YanjiShadow,
  YanjiSpacing,
  YanjiTouch,
  YanjiTypography,
} from './tokens';
import type { ThemeMode } from '../bridge';

export interface YanjiTheme {
  isDark: boolean;
  mode: ThemeMode;
  colors: typeof YanjiColors.light | typeof YanjiColors.dark;
  radius: typeof YanjiRadius;
  spacing: typeof YanjiSpacing;
  typography: typeof YanjiTypography;
  touch: typeof YanjiTouch;
  motion: typeof YanjiMotion;
  shadow: typeof YanjiShadow;
}

const ThemeContext = createContext<YanjiTheme | null>(null);

export function YanjiThemeProvider({ children }: { children: React.ReactNode }): React.JSX.Element {
  const systemDark = useColorScheme() === 'dark';
  const [mode, setMode] = useState<ThemeMode>('SYSTEM');

  useEffect(() => {
    let cancelled = false;
    YanjiThemeNative.getThemePreference()
      .then(pref => {
        if (cancelled) return;
        setMode(pref.mode);
      });
    const unsubscribe = onThemeChanged(pref => {
      if (cancelled) return;
      setMode(pref.mode);
    });
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, []);

  const isDark =
    mode === 'DARK' || (mode === 'SYSTEM' && systemDark);

  const theme: YanjiTheme = {
    isDark,
    mode,
    colors: isDark ? YanjiColors.dark : YanjiColors.light,
    radius: YanjiRadius,
    spacing: YanjiSpacing,
    typography: YanjiTypography,
    touch: YanjiTouch,
    motion: YanjiMotion,
    shadow: YanjiShadow,
  };

  return <ThemeContext.Provider value={theme}>{children}</ThemeContext.Provider>;
}

export function useYanjiTheme(): YanjiTheme {
  const theme = useContext(ThemeContext);
  if (!theme) {
    throw new Error('useYanjiTheme must be used inside YanjiThemeProvider');
  }
  return theme;
}
