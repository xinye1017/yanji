/**
 * Yanji theme provider — resolves light/dark from the native theme preference
 * (user_settings.themeMode) and exposes semantic colors to the component tree.
 *
 * The system-level light/dark setting is NEVER modified (AGENTS.md §二.6):
 * this provider only reads the app preference and the system uiMode.
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
  const [nativeDark, setNativeDark] = useState<boolean>(systemDark);

  useEffect(() => {
    let cancelled = false;
    YanjiThemeNative.getThemePreference()
      .then(pref => {
        if (cancelled) return;
        setMode(pref.mode);
        setNativeDark(pref.isDark);
      })
      .catch(() => {
        // Native module unavailable (web/tests): fall back to system scheme.
        setNativeDark(systemDark);
      });
    const unsubscribe = onThemeChanged(pref => {
      if (cancelled) return;
      setMode(pref.mode);
      setNativeDark(pref.isDark);
    });
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [systemDark]);

  const isDark =
    mode === 'DARK' ? true : mode === 'LIGHT' ? false : nativeDark || systemDark;

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
