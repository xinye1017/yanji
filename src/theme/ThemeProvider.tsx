/**
 * Yanji theme provider — resolves light/dark from the native theme preference
 * (user_settings.themeMode) and exposes semantic colors to the component tree.
 *
 * The system-level light/dark setting is NEVER modified (AGENTS.md §二.6):
 * this provider only reads the app preference and the system-wide color scheme.
 *
 * SYSTEM follows the **system-wide** scheme, not the app-effective one. ColorOS
 * and similar OEM builds expose a per-app "force dark" override
 * (`ui_night_mode_override_on`), which makes `useColorScheme()` — and
 * `context.resources` — report dark while the phone itself is light. Choosing
 * 「跟随系统」 means following the phone, so the native side reads
 * `Resources.getSystem()` and pushes it through `onThemeChanged`.
 *
 * `useColorScheme()` is kept only as the pre-hydration fallback: it is correct
 * on platforms without an app-level override and avoids a light flash while the
 * native promise is in flight.
 *
 * The 素笺与寸阴 paper domain is the single colour and type vocabulary, for the
 * navigation dock as much as for the body. The remaining scales — radius,
 * spacing, layout, touch, motion, shadow — are neutral measurements that carry
 * no colour of their own.
 */

import React, { createContext, useContext, useEffect, useState } from 'react';
import { useColorScheme } from 'react-native';
import { YanjiThemeNative, onThemeChanged } from '../bridge';
import {
  YanjiLayout,
  YanjiMotion,
  YanjiPaperColors,
  YanjiPaperImmersive,
  YanjiPaperLayout,
  YanjiPaperMotion,
  YanjiPaperRadius,
  YanjiPaperTypography,
  YanjiRadius,
  YanjiShadow,
  YanjiSpacing,
  YanjiTouch,
} from './tokens';
import type { ThemeMode } from '../bridge';

export interface YanjiTheme {
  isDark: boolean;
  mode: ThemeMode;
  radius: typeof YanjiRadius;
  spacing: typeof YanjiSpacing;
  layout: typeof YanjiLayout;
  touch: typeof YanjiTouch;
  motion: typeof YanjiMotion;
  shadow: typeof YanjiShadow;
  /**
   * 素笺与寸阴 domain. `colors` resolves with the theme; `immersive` is always
   * the full pair because a focus screen picks running vs paused itself.
   */
  paper: {
    colors: (typeof YanjiPaperColors)['light'] | (typeof YanjiPaperColors)['dark'];
    immersive: typeof YanjiPaperImmersive;
    typography: typeof YanjiPaperTypography;
    layout: typeof YanjiPaperLayout;
    radius: typeof YanjiPaperRadius;
    motion: typeof YanjiPaperMotion;
  };
}

const ThemeContext = createContext<YanjiTheme | null>(null);

export function YanjiThemeProvider({ children }: { children: React.ReactNode }): React.JSX.Element {
  const rnSystemDark = useColorScheme() === 'dark';
  const [mode, setMode] = useState<ThemeMode>('SYSTEM');
  const [systemDark, setSystemDark] = useState<boolean>(rnSystemDark);

  useEffect(() => {
    let cancelled = false;
    YanjiThemeNative.getThemePreference()
      .then(pref => {
        if (cancelled) return;
        setMode(pref.mode);
        setSystemDark(pref.isDark);
      })
      .catch(() => {
        // Native module unavailable (web/tests): fall back to the RN scheme.
        setSystemDark(rnSystemDark);
      });
    const unsubscribe = onThemeChanged(pref => {
      if (cancelled) return;
      setMode(pref.mode);
      setSystemDark(pref.isDark);
    });
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [rnSystemDark]);

  const isDark = mode === 'DARK' || (mode === 'SYSTEM' && systemDark);

  const theme: YanjiTheme = {
    isDark,
    mode,
    radius: YanjiRadius,
    spacing: YanjiSpacing,
    layout: YanjiLayout,
    touch: YanjiTouch,
    motion: YanjiMotion,
    shadow: YanjiShadow,
    paper: {
      colors: isDark ? YanjiPaperColors.dark : YanjiPaperColors.light,
      immersive: YanjiPaperImmersive,
      typography: YanjiPaperTypography,
      layout: YanjiPaperLayout,
      radius: YanjiPaperRadius,
      motion: YanjiPaperMotion,
    },
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
